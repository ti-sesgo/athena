# Plano de Correção — CodeSystems Fragment

Branch: `fix/codesystem-fragment-loading`. Endereça o item 4.2 de `docs/auditoria-senior.md`.

## Problema

A constraint `UNIQUE (url, version)` em `terminology.code_systems` assume um recurso por `url+version`. Mas a spec FHIR R4 permite que um CodeSystem com `content=fragment` seja distribuído em **várias peças complementares** compartilhando a mesma `url+version` ([codesystem-content-mode](https://hl7.org/fhir/R4/valueset-codesystem-content-mode.html)):

> If the value is fragment, then the resource describes part of a code system. (...) Fragments cannot contain any codes, concepts or properties that would not be found in a complete representation of the code system.

`CodeSystemLoaderService` detecta duplicata via `findByUrlAndVersionAndActiveTrue` e descarta os fragments subsequentes como `IGNORADO`.

### Exemplo

No package `hl7.terminology.r4:6.5.0`, `http://hl7.org/fhir/sid/icd-9-cm` v3.0.0 aparece em dois artefatos `content=fragment`. O segundo é silenciosamente ignorado, e `$lookup`/`$validate-code` respondem 404 para códigos exclusivos dele.

## Solução

**Normalização**: reutilizar a mesma `CodeSystemEntity` para todos os fragments de uma `url+version`, acoplando seus conceitos. A spec garante ausência de overlap, então `uk_concept_code_url_version` não é violada.

- Já existe `CodeSystemEntity` para a `url+version`? → acopla conceitos, retorna `status="ACOPLADO"`.
- Não existe? → cria normalmente com `status="SUCESSO"`.
- Concorrência: `saveAndFlush` + catch de `DataIntegrityViolationException` recupera a entity vencedora e segue como acoplamento (mesmo padrão de `PackageLoaderService`).

## Implementação

Mudança isolada em `CodeSystemLoaderService`. **Não muda**: schema, entidades JPA, repositórios, `CodeSystemService`, controllers, caches.

1. `loadCodeSystem` reutiliza `existing` ao invés de retornar `IGNORADO`.
2. `persistNewCodeSystem` extraído com `saveAndFlush` tolerante a carga paralela.
3. `LoadResult.status` aceita `"ACOPLADO"`; `logSummary` ganha a coluna.

### Critérios de sucesso

- [x] `hl7.terminology.r4:6.5.0` carrega ICD-9-CM v3.0.0 como `SUCESSO` + `ACOPLADO`.
- [x] `$lookup` e `$validate-code` respondem para códigos de qualquer fragment.
- [x] `GET /CodeSystem?url=...icd-9-cm` retorna Bundle com 1 entrada; `/metadata?mode=terminology` lista a versão uma única vez.
- [x] Schema inalterado; testes existentes passam; novos testes cobrem o acoplamento.

## Consequências e trade-offs

Limitações aceitas (Athena é servidor de **terminologia**, não de distribuição fiel de artefatos):

1. **`GET /CodeSystem/{id}` devolve recurso incompleto.** `content BYTEA` guarda o JSON do **primeiro** fragment; `$lookup`/`$validate-code` ignoram esse campo e continuam corretos.
2. **`resource_id` de fragments posteriores responde 404.** Só o id lógico do primeiro fragment é preservado.
3. **Idempotência parcial.** Ordem diferente numa recarga muda o `content` persistido; o conjunto de conceitos indexados permanece o mesmo.
