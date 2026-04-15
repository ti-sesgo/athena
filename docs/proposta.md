# Proposta — Arquitetura do Athena

O Athena é um servidor de terminologias FHIR R4 que carrega *packages* NPM FHIR (ex.: `hl7.terminology.r4`, `hl7.fhir.r4.core`), indexa CodeSystems/ValueSets/ConceptMaps em PostgreSQL e responde às operações padrão do serviço de terminologia definidas em [FHIR R4 Terminology Service](https://hl7.org/fhir/R4/terminology-service.html).

O objetivo de projeto é ser **simples e rápido**: nada de servidor HAPI completo; apenas Spring MVC + HAPI como modelo de recurso, tabelas desnormalizadas para `$lookup`/`$validate-code`, cache heap e virtual threads.

## Diagrama de contexto (quem fala com quem)

```mermaid
%%{init: {'flowchart': {'curve': 'linear'}}}%%
flowchart LR
    cliente(["Cliente FHIR<br/>sistemas clínicos, LIS, EHR"])
    athena["Athena<br/>Servidor de Terminologias"]
    registry(["FHIR Registry<br/>packages.fhir.org"])

    cliente -->|"$lookup · $validate-code<br/>FHIR R4 JSON/XML"| athena
    athena -->|"download de packages<br/>HTTPS"| registry

    classDef external fill:#f1f5f9,stroke:#64748b,color:#0f172a
    classDef system fill:#1e3a8a,stroke:#1e3a8a,color:#f8fafc
    class cliente,registry external
    class athena system
```

## Diagrama de container (o que roda dentro)

```mermaid
flowchart TB
    cliente(["Cliente FHIR"])
    registry(["FHIR Registry"])
    db[("PostgreSQL<br/>schema terminology")]

    subgraph athena ["Athena · Spring Boot 3.4 · JVM 21"]
        direction TB
        api["API REST<br/>Spring MVC + HAPI FHIR"]
        loader["Package Loader<br/>ResourceExtractor"]
        cache[("Cache Caffeine<br/>TTL 2h")]
    end

    cliente -->|"HTTPS · /fhir/**"| api
    api -. consulta .-> cache
    api -->|"JPA"| db
    loader -->|"batch insert"| db
    loader -->|"GET package.tgz"| registry

    classDef external fill:#f1f5f9,stroke:#64748b,color:#0f172a
    classDef system fill:#1e3a8a,stroke:#1e3a8a,color:#f8fafc
    class cliente,registry,db external
    class api,loader,cache system
```

## Fluxos principais

| Fluxo | Entrada | Processamento | Saída |
|---|---|---|---|
| **Carga na inicialização** | Lista de packages (`athena.packages.items`) | Baixa do registry OU lê local, extrai recursos FHIR via `.tgz`, persiste em `terminology.*` | Logs de carga com status `SUCESSO` / `ACOPLADO` / `IGNORADO` / `ERRO` |
| **`$lookup`** | `system` + `code` [+ `version`] | Consulta `concepts` (cache Caffeine na chave `system-code-version`); parseia `property` / `designation` do JSON | `Parameters` com `display`, `definition`, `property`, `designation` |
| **`$validate-code`** | `url` + `code` [+ `display`] | Mesma consulta do `$lookup`; compara `display` de forma leniente (case-insensitive, trim) | `Parameters` com `result` / `message` / `display` |
| **Metadata** | `GET /metadata` [`?mode=terminology`] | Devolve `CapabilityStatement` estático ou `TerminologyCapabilities` dinâmico construído a partir do banco | Recurso FHIR serializado |

## Princípios de design

- **Spring MVC puro**, sem `RestfulServer` do HAPI. HAPI é usado apenas como modelo de recurso (parser + classes FHIR R4) e como serializer via `FhirHttpMessageConverter`. Prefixo `/fhir` é aplicado automaticamente via `WebConfig.configurePathMatch`.
- **Tabelas desnormalizadas**. `concepts` replica `code_system_url`, `code_system_version` e `code_system_is_latest` para que `$lookup` faça uma única query por `(system, code)`.
- **Carga paralela** com virtual threads (Java 21) via `ExecutorService`. Cada CodeSystem numa transação própria (`TransactionTemplate`). Entre instâncias, concorrência resolvida por `DataIntegrityViolationException` no insert do `PackageEntity`.
- **Cache heap** com Caffeine (TTL 2 h) em `concepts` e `codeSystem`; invalidado explicitamente ao final da carga.
- **Conformidade FHIR R4** declarada em `CapabilityStatement` e `TerminologyCapabilities`; respostas de erro sempre como `OperationOutcome`.

## Estado atual e limitações conhecidas

Trabalho em andamento. O README lista as operações implementadas e pendentes. Duas limitações merecem destaque aqui:

- **Normalização de fragments de CodeSystem.** Múltiplos artefatos com mesma `url+version` e `content=fragment` são consolidados numa única row — `GET /CodeSystem/{id}` pode retornar o recurso incompleto do primeiro fragment, embora `$lookup`/`$validate-code` cubram todos os conceitos. Detalhes em `docs/plano-fragment-fix.md`.
- **`isLatest` por ordem de chegada.** A primeira versão a ser carregada para uma mesma URL é marcada como default; a lógica semântica (por `package.json.date` ou comparação SemVer) é trabalho futuro.
