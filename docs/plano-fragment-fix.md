# Plano de Correção — CodeSystems Fragment (issue ICD-9-CM)

## Contexto

Athena é um servidor FHIR R4 de terminologias construído com Spring Boot 3.4, Java 21, HAPI FHIR 7.6, PostgreSQL e Flyway. CodeSystems são extraídos de packages NPM FHIR (ex.: `hl7.terminology.r4:6.5.0`), indexados na tabela `terminology.code_systems` e consultados via operações `$lookup` e `$validate-code`.

Branch: `fix/codesystem-fragment-loading`.

## Problema

Ao carregar `hl7.terminology.r4:6.5.0` (também reproduzido em `7.0.1`), o CodeSystem `http://hl7.org/fhir/sid/icd-9-cm` versão `3.0.0` existe em dois artefatos distintos dentro do mesmo package, ambos com `content=fragment`. Pela especificação FHIR R4 ([CodeSystem.content](https://hl7.org/fhir/R4/codesystem-definitions.html#CodeSystem.content), [codesystem-content-mode](https://hl7.org/fhir/R4/valueset-codesystem-content-mode.html)):

> If the value is **fragment**, then the resource describes part of a code system. Code systems may be distributed by the code system authority in a set of fragments. Fragments cannot contain any codes, concepts or properties that would not be found in a complete representation of the code system.

Em outras palavras, fragments de uma mesma `url+version` são **peças complementares sem overlap** de um único CodeSystem lógico. Consolidar suas informações num único registro é semanticamente válido.

O segundo fragment é ignorado silenciosamente no projeto atual por três causas encadeadas:

1. `src/main/resources/db/migration/V1__create_terminology_schema.sql:31` — `CONSTRAINT uk_cs_url_version UNIQUE (url, version)` impede duas linhas com a mesma `url+version`.
2. `src/main/java/br/gov/go/saude/athena/domain/CodeSystemEntity.java:21-25` — `@UniqueConstraint` espelha a restrição no modelo JPA.
3. `src/main/java/br/gov/go/saude/athena/service/CodeSystemLoaderService.java:105-111` — `findByUrlAndVersionAndActiveTrue(url, version).isPresent()` retorna `true` para o primeiro fragment e o segundo é descartado com status `IGNORADO`.

Consequência observada: conceitos exclusivos do segundo fragment ficam ausentes do índice; `$lookup` e `$validate-code` respondem 404 para códigos que só constam nesse fragment.

## Critérios de sucesso

- [x] Ao carregar `hl7.terminology.r4:6.5.0`, o log de carga não exibe `IGNORADO` para ICD-9-CM v3.0.0; os fragments aparecem como `SUCESSO` (primeiro) e `ACOPLADO` (demais).
- [x] `$lookup(system=http://hl7.org/fhir/sid/icd-9-cm, code=<código de qualquer fragment>)` retorna display.
- [x] `$validate-code` valida corretamente códigos de qualquer fragment.
- [x] `GET /CodeSystem?url=http://hl7.org/fhir/sid/icd-9-cm` retorna Bundle com exatamente uma entrada.
- [x] `/metadata?mode=terminology` lista ICD-9-CM v3.0.0 apenas uma vez.
- [x] Schema do banco não muda; testes existentes continuam passando.
- [x] Novos testes cobrem o cenário de acoplamento.

## Solução escolhida — Normalização de fragments

Reutilizar a mesma `CodeSystemEntity` para todos os fragments de uma mesma `url+version`, acoplando seus conceitos.

- Já existe CodeSystem para essa `url+version`? → acopla os novos conceitos à entity existente, retornando `LoadResult.status="ACOPLADO"`.
- Não existe? → cria nova entity normalmente com `status="SUCESSO"`.
- Concorrência (dois fragments carregando em paralelo): o `saveAndFlush` captura `DataIntegrityViolationException`, recupera a entity que venceu a corrida e segue a carga como acoplamento — mesmo padrão já usado entre instâncias em `PackageLoaderService`.

Essa escolha é **simples, pouco invasiva e semanticamente correta** para o propósito do Athena: as operações `$lookup` e `$validate-code` operam sobre `ConceptEntity`, que passa a conter os conceitos de todos os fragments apontando para a mesma `CodeSystemEntity`. A spec garante ausência de overlap, então `uk_concept_code_url_version` não é violada.

### O que NÃO muda

- Schema do banco (Flyway permanece em V1).
- Entidades JPA (`CodeSystemEntity`, `ConceptEntity`).
- Repositórios (nenhuma nova query).
- Service consumidor (`CodeSystemService`): `findByUrl`, `findConcept`, `findCodeSystemsWithVersionsByActiveTrue`, caches — tudo permanece igual.
- Controllers e CapabilityStatement.

### O que muda

Apenas `CodeSystemLoaderService`:

1. `loadCodeSystem` passa a reutilizar `existing` quando já há uma `CodeSystemEntity` para a mesma `url+version`, ao invés de retornar `IGNORADO`.
2. `persistNewCodeSystem` extraído como método privado; usa `saveAndFlush` + catch de `DataIntegrityViolationException` para tolerar carga paralela.
3. `LoadResult.status` aceita o novo valor `"ACOPLADO"`; `logSummary` imprime a nova coluna.

## Perdas funcionais registradas

A normalização implica três limitações explícitas, que devem constar também no README:

1. **`GET /CodeSystem/{id}` retorna recurso incompleto.** A coluna `content BYTEA` guarda o JSON FHIR do **primeiro fragment** carregado. Quando um cliente lê o artefato original via `GET /CodeSystem/{id}` ou `GET /CodeSystem?url=...`, recebe um recurso que pode conter apenas um subconjunto dos conceitos realmente indexados pelo servidor. As operações `$lookup` e `$validate-code` ignoram este campo e continuam corretas.
2. **`resource_id` de fragments subsequentes não é acessível.** `GET /CodeSystem/{resource_id}` onde `{resource_id}` seja o id lógico de um fragment que chegou depois do primeiro responde **404**. O id lógico preservado no índice é o do primeiro fragment; os demais são descartados em favor do acoplamento.
3. **Idempotência parcial de recarga.** Se os fragments chegam em ordem diferente numa recarga, o `content` persistido representa um fragment distinto do da carga anterior. O conjunto de conceitos indexados permanece o mesmo; apenas a representação canônica do artefato no `content` muda.

Estas limitações são aceitáveis porque o Athena é um servidor de **terminologia** (foco em códigos, não em distribuição fiel de artefatos FHIR).

## Sequência de commits sugerida

Seguindo a convenção do projeto (pt-BR, linha única, sem co-autor):

- `fix: acopla fragments do mesmo CodeSystem em vez de ignorá-los`
- `test: cobre acoplamento de fragments no CodeSystemLoaderService`
- `docs: registra estratégia de normalização de fragments`

## Observações finais

Este plano endereça o item 4.2 do `docs/auditoria-senior.md`. Itens adjacentes — 4.1 (lógica semântica de `isLatest`), 4.4 (tipos de coluna `TEXT` para URLs) — seguem como trabalhos futuros documentados na auditoria, intencionalmente fora do escopo desta correção.
