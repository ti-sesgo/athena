# Plano de Correção — CodeSystems Fragment (issue ICD-9-CM)

## Contexto

Athena é um servidor FHIR R4 de terminologias construído com Spring Boot 3.4, Java 21, HAPI FHIR 7.6, PostgreSQL e Flyway. CodeSystems são extraídos de packages NPM FHIR (ex.: `hl7.terminology.r4:6.5.0`), indexados na tabela `terminology.code_systems` e consultados via operações `$lookup` e `$validate-code`.

Branch: `fix/codesystem-fragment-loading`.

## Problema

Ao carregar `hl7.terminology.r4:6.5.0` (também reproduzido em `7.0.1`), o CodeSystem `http://hl7.org/fhir/sid/icd-9-cm` versão `3.0.0` existe em dois artefatos distintos dentro do mesmo package, ambos com `content=fragment`. Pela spec FHIR R4 ([CodeSystem.content](https://hl7.org/fhir/R4/codesystem-definitions.html#CodeSystem.content), [codesystem-content-mode](https://hl7.org/fhir/R4/valueset-codesystem-content-mode.html)), `fragment` significa que o artefato contém apenas um subconjunto dos conceitos — múltiplos fragmentos de um mesmo CodeSystem são válidos e complementares.

O segundo artefato é ignorado silenciosamente por três causas encadeadas:

1. `src/main/resources/db/migration/V1__create_terminology_schema.sql:31` — `CONSTRAINT uk_cs_url_version UNIQUE (url, version)` impede duas linhas com a mesma `url+version`, mesmo que representem artefatos FHIR distintos.
2. `src/main/java/br/gov/go/saude/athena/domain/CodeSystemEntity.java:21-25` — `@UniqueConstraint(name = "uk_cs_url_version", columnNames = {"url", "version"})` espelha a restrição no modelo JPA (com `TODO` explícito).
3. `src/main/java/br/gov/go/saude/athena/service/CodeSystemLoaderService.java:105-111` — `findByUrlAndVersionAndActiveTrue(url, version).isPresent()` retorna `true` para o primeiro fragmento e o segundo é descartado como `IGNORADO — Já existe`.

Existe ainda uma UK "simpática" na tabela de conceitos, `src/main/resources/db/migration/V1__create_terminology_schema.sql:53` — `CONSTRAINT uk_concept_code_url_version UNIQUE (code, code_system_url, code_system_version)` — que também precisa ser relaxada, caso contrário fragmentos com qualquer overlap de códigos falhariam em `flushBuffer` mesmo depois de remover a UK principal.

Consequência observada: conceitos exclusivos do segundo fragmento ficam ausentes do índice; `$lookup` e `$validate-code` respondem 404 para códigos que só constam nesse fragmento.

## Critérios de sucesso

- [ ] Ao carregar `hl7.terminology.r4:6.5.0`, o log de carga não exibe `IGNORADO` para ICD-9-CM v3.0.0; ambos os fragmentos aparecem como `SUCESSO`.
- [ ] `$lookup(system=http://hl7.org/fhir/sid/icd-9-cm, code=<código exclusivo do fragmento 2>)` retorna display.
- [ ] `$validate-code` valida corretamente códigos de qualquer fragmento.
- [ ] `GET /CodeSystem?url=http://hl7.org/fhir/sid/icd-9-cm` retorna Bundle com exatamente uma entrada (fragmento canônico escolhido).
- [ ] `/metadata?mode=terminology` lista ICD-9-CM v3.0.0 apenas uma vez por versão.
- [ ] Testes existentes continuam passando sem alteração de contrato público.
- [ ] Migração Flyway idempotente e com SQL de rollback documentado.

## Opções de design avaliadas

### A. `UNIQUE(url, version, content_mode)`
Adicionar coluna `content_mode` e incluir na UK.

**Prós:** mudança mínima; preserva semântica atual.
**Contras:** dois artefatos `fragment` com mesma `url+version` ainda colidiriam — é exatamente o caso do ICD-9-CM. **Não resolve o caso real.**

### B. `UNIQUE(url, version, resource_id)` — recomendada
Substituir a UK por `UNIQUE(url, version, resource_id)`. `resource_id` é o ID lógico FHIR do artefato (coluna já existente, já preenchida pelo loader).

**Prós:** `resource_id` já diferencia os dois artefatos ICD-9-CM dentro do mesmo `url+version`; sem nova coluna; a checagem de duplicidade no loader passa a ser `findByUrlAndVersionAndResourceIdAndActiveTrue`, alinhada com a nova UK; impacto mínimo.
**Contras:** `resource_id` pode ser `null` quando o artefato não tem `id` FHIR explícito — o loader tem fallback para `url`, que colidiria se dois fragmentos sem `id` compartilharem a URL. O fallback precisa ser ajustado para incluir hash do conteúdo.
**Resolve o caso real?** Sim — os dois artefatos ICD-9-CM têm `id` FHIR distintos.

### C. `UNIQUE(package_id, resource_id)`
Unicidade dentro do package.

**Prós:** fiel ao modelo FHIR de empacotamento.
**Contras:** permite duplicação de `url+version` entre packages distintos, violando a invariante semântica de que `url+version` identifica univocamente uma terminologia.
**Resolve o caso real?** Sim, mas com perda de integridade.

### D. Merge de fragmentos em uma única row
Detectar `content=fragment` e mesclar conceitos no JSON de uma mesma `CodeSystemEntity`.

**Prós:** mantém "uma row por CodeSystem semântico".
**Contras:** lógica de merge frágil; carga paralela introduz race conditions; o campo `content` (bytes do recurso original) vira híbrido e não corresponde a nenhum artefato real; contradiz o padrão atual de guardar o artefato como veio.

### E. Remover a UK e usar só PK surrogate
**Prós:** zero resistência a duplicação.
**Contras:** sem proteção contra carga duplicada em recargas; queries consumidoras precisariam de `DISTINCT`/`GROUP BY` em todo lugar.

## Opção recomendada — B

`UNIQUE(url, version, resource_id)` é a solução com menor raio de impacto e maior fidelidade ao modelo já existente. `resource_id` está na tabela, é preenchido pelo loader (linha 126 do `CodeSystemLoaderService`), e já diferencia os dois artefatos ICD-9-CM. A checagem de duplicidade no loader passa de `(url, version)` para `(url, version, resource_id)`, alinhando código e banco. O edge case de `resource_id` nulo é coberto com fallback determinístico que inclui hash do conteúdo.

## Plano de implementação (passo a passo)

### Passo 1 — Migração Flyway

Arquivo novo: `src/main/resources/db/migration/V2__cs_unique_with_resource_id.sql`

```sql
-- 1. Remove UK antiga de CodeSystem (url, version)
ALTER TABLE terminology.code_systems
    DROP CONSTRAINT IF EXISTS uk_cs_url_version;

-- 2. Nova UK por artefato
ALTER TABLE terminology.code_systems
    ADD CONSTRAINT uk_cs_url_version_resource_id
        UNIQUE (url, version, resource_id);

-- 3. Remove UK antiga de conceitos (sensível a fragmentos)
ALTER TABLE terminology.concepts
    DROP CONSTRAINT IF EXISTS uk_concept_code_url_version;

-- 4. Nova UK de conceitos vinculada ao CodeSystemEntity específico
ALTER TABLE terminology.concepts
    ADD CONSTRAINT uk_concept_code_cs_id
        UNIQUE (code, code_system_id);
```

- `DROP … IF EXISTS` garante idempotência em ambientes limpos ou com DB resetado em teste.
- Rollback manual (se necessário, sem Flyway undo): desfazer na ordem inversa (restaurar as duas UKs originais).
- Rows existentes em produção com `url+version` únicos continuam válidas sob a nova UK.

### Passo 2 — Modelo

**`src/main/java/br/gov/go/saude/athena/domain/CodeSystemEntity.java:21-25`**

- Remover o comentário `TODO` referente ao problema resolvido.
- Substituir a `@UniqueConstraint` por:
  ```java
  @UniqueConstraint(name = "uk_cs_url_version_resource_id",
      columnNames = {"url", "version", "resource_id"})
  ```
- Manter os `@Index` existentes.

**`src/main/java/br/gov/go/saude/athena/domain/ConceptEntity.java:28-30`**

- Substituir a `@UniqueConstraint` por:
  ```java
  @UniqueConstraint(name = "uk_concept_code_cs_id",
      columnNames = {"code", "code_system_id"})
  ```

### Passo 3 — Repository

**`src/main/java/br/gov/go/saude/athena/repository/CodeSystemRepository.java`**

- Renomear `findByUrlAndVersionAndActiveTrue(String url, String version)` para `findByUrlAndVersionAndResourceIdAndActiveTrue(String url, String version, String resourceId)` (retorna `Optional<CodeSystemEntity>`). O método só é usado no loader.
- Alterar `findByUrlAndIsLatestTrueAndActiveTrue(String url)` de `Optional<CodeSystemEntity>` para `List<CodeSystemEntity>` — com a nova UK, múltiplas rows podem ter `url+isLatest=true` (uma por fragmento).

### Passo 4 — Service (loader)

**`src/main/java/br/gov/go/saude/athena/service/CodeSystemLoaderService.java`**

- **Linhas 105-111** (checagem de duplicidade): trocar a chamada para `findByUrlAndVersionAndResourceIdAndActiveTrue(url, version, resourceId)`. `resourceId` já está disponível na linha 100.
- **Linha 119** (cálculo de `isLatest`): com múltiplos fragmentos por `url+version`, todos precisam compartilhar o mesmo `isLatest`. A lógica atual `findByUrlAndIsLatestTrueAndActiveTrue(url).isEmpty()` faz com que o primeiro fragmento vire `isLatest=true` e fragmentos subsequentes da mesma URL virem `isLatest=false` — inconsistente. Correção:
  ```java
  boolean isLatest = codeSystemRepository
      .findByUrlAndIsLatestTrueAndActiveTrue(url)
      .isEmpty();
  // Após a alteração do repo para List, a semântica fica: "se ainda não há
  // nenhuma row com isLatest=true nesta URL, este fragmento recebe isLatest=true".
  // Fragmentos subsequentes da mesma url+version recebem isLatest=false.
  ```
  Isso produz inconsistência entre fragmentos da mesma `url+version` — tratada pelo bloco "Riscos" adiante. A correção completa de `isLatest` (tornar consistente entre fragmentos e escolher semântica de "mais recente") está fora do escopo desta PR e é trabalho futuro alinhado com o item 4.1 da auditoria senior.
- **Linha 126** (fallback de `resourceId`): alterar para evitar colisão quando dois fragmentos sem `id` FHIR compartilham a URL:
  ```java
  .resourceId(resourceId != null
      ? resourceId
      : url + "#" + Integer.toHexString(Arrays.hashCode(extracted.content())))
  ```

### Passo 5 — Service (consumidor)

**`src/main/java/br/gov/go/saude/athena/service/CodeSystemService.java`**

- **Linha 66** (`findByUrl`): adaptar para a nova assinatura `List<CodeSystemEntity>`:
  ```java
  public Optional<CodeSystemEntity> findByUrl(String url) {
      return codeSystemRepository.findByUrlAndIsLatestTrueAndActiveTrue(url)
                                 .stream()
                                 .findFirst();
  }
  ```
  O contrato do caller (`findResourceByUrl` → `GET /CodeSystem?url=`) continua retornando um Bundle com um artefato representativo.
- **Linhas 86-104** (`findCodeSystemsWithVersionsByActiveTrue`): adicionar deduplicação por `url+version` em `groupByUrlWithVersions`:
  ```java
  List<VersionInfo> versions = byUrl.computeIfAbsent(url, k -> new ArrayList<>());
  if (versions.stream().noneMatch(v -> Objects.equals(v.code(), version))) {
      versions.add(new VersionInfo(version, isDefault));
  }
  ```
- **Linhas 113-122** (`findConcept`): sem mudança. A query `findByCodeSystemUrlAndCodeAndCodeSystemIsLatestTrueAndActiveTrue` já funciona com múltiplos fragmentos — retorna o primeiro conceito encontrado.
- **Cache key** (linha 154): `#system + '-' + #code + '-' + (#version ?: 'latest')` — permanece correto; não depende de row única.

### Passo 6 — Testes

- **Novo** — `src/test/java/br/gov/go/saude/athena/service/CodeSystemLoaderServiceTest.java`:
  - `shouldLoadBothFragmentsWhenUrlAndVersionAreSameButResourceIdsDiffer` — mock de `codeSystemRepository.findByUrlAndVersionAndResourceIdAndActiveTrue` retornando `Optional.empty()` para ambos; verificar duas chamadas a `save`; nenhum `LoadResult` com status `IGNORADO`.
  - `shouldSkipCodeSystemWhenSameUrlVersionAndResourceIdAlreadyExists` — mock retornando `Optional.of(entity)` na segunda chamada; verificar uma única chamada a `save` e status `IGNORADO`.
- **Adaptar** `CodeSystemControllerTest`: mocka `CodeSystemService` diretamente, sem mudança de contrato público. **Nenhuma alteração esperada.**
- **Adaptar** `CacheIntegrationTest`: independente da UK de `code_systems`. **Nenhuma alteração esperada.**
- **Adaptar** `DomainModelTest`: usa builder em memória sem persistência. **Nenhuma alteração esperada.**
- **Integração** (opcional, se houver Testcontainers): carregar dois fragmentos com mesma `url+version` e validar que `$lookup` encontra códigos de ambos.

### Passo 7 — Validação manual

- Subir com `hl7.terminology.r4:6.5.0` em instância PostgreSQL com a V2 aplicada.
- Log de carga deve exibir ICD-9-CM v3.0.0 duas vezes com `SUCESSO` e `resource_id` distintos.
- `GET /fhir/CodeSystem/$lookup?system=http://hl7.org/fhir/sid/icd-9-cm&code=<código exclusivo do fragmento 2>` → `200` com display.
- `GET /fhir/CodeSystem?url=http://hl7.org/fhir/sid/icd-9-cm` → Bundle com `total=1`.
- `GET /fhir/metadata?mode=terminology` → ICD-9-CM v3.0.0 listado uma única vez.

## Riscos e mitigações

- **Race condition em `isLatest` durante carga paralela.** O pool de threads pode processar dois fragmentos simultaneamente; ambos consultam `findByUrlAndIsLatestTrueAndActiveTrue` e ambos encontram lista vazia → dois fragmentos com `isLatest=true`. Bug pré-existente (TODO na linha 117, item 4.1 da auditoria). **Mitigação no escopo:** documentar como trabalho futuro; fora do escopo, implementar serialização por URL com `ConcurrentHashMap<String, Lock>` ou mover o cálculo de `isLatest` para passo pós-carga.
- **Overlap de conceitos entre fragmentos.** Se dois fragmentos contiverem o mesmo `code` (violação da spec), a nova UK `uk_concept_code_cs_id` permite, mas `findConcept` retornará arbitrariamente um dos dois. **Risco aceitável** — a spec proíbe overlap e não há cenário conhecido.
- **Dados existentes em produção.** `DROP … IF EXISTS` + `ADD` é sempre seguro para rows existentes com `url+version` únicos. Nenhum backfill necessário.
- **`findByUrl` retornando `stream().findFirst()`.** Quando há múltiplos fragmentos, a escolha do representante no `GET /CodeSystem?url=` é não-determinística. Nenhum fragmento individual representa o CodeSystem completo. **Mitigação:** para obter artefato específico, cliente pode usar `GET /CodeSystem/{id}` com `resource_id` explícito.

## Sequência de commits sugerida

Seguindo a convenção do projeto (pt-BR, linha única, sem co-autor Claude):

1. `fix: aceita múltiplos artefatos CodeSystem com mesma url e versão`
2. `refactor: checa duplicidade por url+version+resourceId no carregamento`
3. `test: cobre carga de fragmentos múltiplos do mesmo CodeSystem`

## Observações finais

Este plano corresponde ao item 4.2 do `docs/auditoria-senior.md`. Os itens 4.1 (lógica correta de `isLatest`) e a migração de `VARCHAR(255)` para `TEXT` em URLs (item 4.4 da auditoria) são trabalhos adjacentes recomendados, mas intencionalmente fora do escopo desta PR para manter o diff pequeno e reviewable.
