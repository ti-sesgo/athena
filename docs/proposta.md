# Proposta 

O servidor é configurado com packages FHIR R4. A partir desses packages, ele monta um serviço de terminologia FHIR capaz de responder às operações de forma eficiente.

# Arquitetura

## Diagrama de Contexto

```mermaid
graph LR
    Cliente[Cliente] -->|Operações FHIR| Athena[Athena]
    Athena -->|Importa packages| Registry[FHIR Registry]
    
    style Cliente fill:#999,stroke:#666,color:#fff
    style Athena fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style Registry fill:#999,stroke:#666,color:#fff
```

## Diagrama de Container

```mermaid
graph TB
    Cliente["Cliente"];
    Registry["FHIR Registry"];
    API["API REST (Spring Boot)"];
    DB["PostgreSQL"];
    
    API -->|"Consulta/Persiste"| DB;
    Cliente -->|"HTTPS / JSON ($lookup...)"| API;
    API -->|"Carrega packages"| Registry;
        
    style Cliente fill:#999,stroke:#666,color:#fff
    style API fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style DB fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style Registry fill:#999,stroke:#666,color:#fff
```

> Trabalho em andamento...
