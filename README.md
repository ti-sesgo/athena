# Athena Servidor de Terminologias FHIR

O servidor de terminologias FHIR mais rápido e simples do mundo!

[Proposta de solução mais simples do mundo](docs/proposta.md)

## Operações do Servidor de Terminologia



| Operação                 | Endpoint                            | Descrição                                                                             | Requisito Mínimo | Status             |
|--------------------------|-------------------------------------|---------------------------------------------------------------------------------------|------------------|--------------------|
| **Expand**               | `GET /ValueSet/{id}/$expand`        | Expande um ValueSet retornando a lista de códigos que atendem aos critérios de filtro | ✅ SHALL          | ❌ Não implementado |
| **Expand**               | `POST /ValueSet/$expand`            | Expande um ValueSet fornecido no corpo da requisição                                  | ✅ SHALL          | ❌ Não implementado |
| **Lookup**               | `GET /CodeSystem/$lookup`           | Retorna informações sobre um código específico (display, propriedades, designações)   | ✅ SHALL          | ❌ Não implementado |
| **Lookup**               | `POST /CodeSystem/$lookup`          | Retorna informações sobre um código usando Coding no corpo da requisição              | ✅ SHALL          | ❌ Não implementado |
| **Validate Code**        | `GET /ValueSet/{id}/$validate-code` | Valida se um código pertence a um ValueSet específico                                 | ✅ SHALL          | ❌ Não implementado |
| **Validate Code**        | `POST /ValueSet/$validate-code`     | Valida um CodeableConcept contra um ValueSet                                          | ✅ SHALL          | ❌ Não implementado |
| **Translate**            | `GET /ConceptMap/$translate`        | Traduz um código de um ValueSet para outro usando ConceptMap                          | ✅ SHALL          | ❌ Não implementado |
| **Translate**            | `POST /ConceptMap/$translate`       | Traduz conceitos entre sistemas de códigos                                            | ✅ SHALL          | ❌ Não implementado |
| **Closure**              | `POST /ConceptMap/$closure`         | Mantém tabela de fechamento transitivo para consultas de subsunção                    | ⚠️ SHOULD        | ❌ Não implementado |
| **Read ValueSet**        | `GET /ValueSet/{id}`                | Recupera um ValueSet específico por ID                                                | ✅ SHALL          | ❌ Não implementado |
| **Search ValueSet**      | `GET /ValueSet?url={url}`           | Busca ValueSet por URL canônica                                                       | ✅ SHALL          | ❌ Não implementado |
| **Search ValueSet**      | `GET /ValueSet?name={name}`         | Busca ValueSet por nome                                                               | ✅ SHALL          | ❌ Não implementado |
| **Search ValueSet**      | `GET /ValueSet?date={date}`         | Busca ValueSet por data                                                               | ✅ SHALL          | ❌ Não implementado |
| **Search ValueSet**      | `GET /ValueSet?status={status}`     | Busca ValueSet por status (active, draft, retired)                                    | ✅ SHALL          | ❌ Não implementado |
| **Search ValueSet**      | `GET /ValueSet?version={version}`   | Busca ValueSet por versão                                                             | ✅ SHALL          | ❌ Não implementado |
| **Read ConceptMap**      | `GET /ConceptMap/{id}`              | Recupera um ConceptMap específico por ID                                              | ✅ SHALL          | ❌ Não implementado |
| **Search ConceptMap**    | `GET /ConceptMap?url={url}`         | Busca ConceptMap por URL canônica                                                     | ✅ SHALL          | ❌ Não implementado |
| **Search ConceptMap**    | `GET /ConceptMap?name={name}`       | Busca ConceptMap por nome                                                             | ✅ SHALL          | ❌ Não implementado |
| **Search ConceptMap**    | `GET /ConceptMap?source={url}`      | Busca ConceptMap por ValueSet de origem                                               | ✅ SHALL          | ❌ Não implementado |
| **Search ConceptMap**    | `GET /ConceptMap?target={url}`      | Busca ConceptMap por ValueSet de destino                                              | ✅ SHALL          | ❌ Não implementado |
| **Search ConceptMap**    | `GET /ConceptMap?date={date}`       | Busca ConceptMap por data                                                             | ✅ SHALL          | ❌ Não implementado |
| **Search ConceptMap**    | `GET /ConceptMap?status={status}`   | Busca ConceptMap por status                                                           | ✅ SHALL          | ❌ Não implementado |
| **Search ConceptMap**    | `GET /ConceptMap?version={version}` | Busca ConceptMap por versão                                                           | ✅ SHALL          | ❌ Não implementado |
| **Capability Statement** | `GET /metadata`                     | Retorna as capacidades do servidor de terminologia                                    | ✅ SHALL          | ❌ Não implementado |
| **Subsumes**             | `GET /CodeSystem/$subsumes`         | Testa se o conceito A subsume o conceito B                                            | MAY              | ❌ Não implementado |
| **Subsumes**             | `POST /CodeSystem/$subsumes`        | Testa relação de subsunção usando Codings                                             | MAY              | ❌ Não implementado |
| **Read CodeSystem**      | `GET /CodeSystem/{id}`              | Recupera um CodeSystem específico por ID                                              | MAY              | ❌ Não implementado |
| **Search CodeSystem**    | `GET /CodeSystem?url={url}`         | Busca CodeSystem por URL canônica                                                     | MAY              | ❌ Não implementado |
| **Search CodeSystem**    | `GET /CodeSystem?name={name}`       | Busca CodeSystem por nome                                                             | MAY              | ❌ Não implementado |
| **Batch Operations**     | `POST /`                            | Executa múltiplas operações de validação ou tradução em lote                          | MAY              | ❌ Não implementado |

### Conformance Verbs (RFC 2119)

Conforme [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt)
e [FHIR Conformance Rules](https://hl7.org/fhir/R4/conformance-rules.html#conflang):

- **SHALL** (obrigatório): Requisito absoluto da especificação. Implementação deve suportar para estar em conformidade.
- **SHOULD** (recomendado): Pode haver razões válidas para não implementar, mas as implicações devem ser compreendidas e
  cuidadosamente avaliadas.
- **MAY** (opcional): Item verdadeiramente opcional. Um fornecedor pode escolher incluir porque melhora o produto ou
  porque um mercado específico requer.

## Documentação de Referência

- [FHIR Terminology Service](https://hl7.org/fhir/R4/terminology-service.html)
- [Basic Capability Statement](https://hl7.org/fhir/R4/capabilitystatement-terminology-server.html)