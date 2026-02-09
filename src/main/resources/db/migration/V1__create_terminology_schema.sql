-- Schema para terminologias FHIR
CREATE SCHEMA IF NOT EXISTS terminology;

-- Tabela de packages
CREATE TABLE terminology.packages (
    id BIGSERIAL PRIMARY KEY,
    package_id VARCHAR(255) NOT NULL,
    version VARCHAR(100) NOT NULL,
    registry_url VARCHAR(500),
    loaded_at TIMESTAMP NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (package_id, version)
);

-- Tabela de CodeSystems
CREATE TABLE terminology.code_systems (
    id BIGSERIAL PRIMARY KEY,
    resource_id VARCHAR(255) NOT NULL,
    url VARCHAR(500) NOT NULL,
    version VARCHAR(100),
    name VARCHAR(255),
    title VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    description TEXT,
    content BYTEA NOT NULL,
    package_id BIGINT NOT NULL REFERENCES terminology.packages(id),
    is_latest BOOLEAN NOT NULL DEFAULT false
);

-- Índices para CodeSystems
CREATE INDEX idx_cs_url ON terminology.code_systems(url);
CREATE INDEX idx_cs_url_version ON terminology.code_systems(url, version);
CREATE INDEX idx_cs_status ON terminology.code_systems(status);
CREATE INDEX idx_cs_resource_id ON terminology.code_systems(resource_id);

-- Tabela de conceitos (otimizada para $lookup)
CREATE TABLE terminology.concepts (
    id BIGSERIAL PRIMARY KEY,
    system VARCHAR(500) NOT NULL,
    version VARCHAR(100),
    code VARCHAR(500) NOT NULL,
    display VARCHAR(1000),
    definition TEXT,
    designations JSONB,
    properties JSONB,
    code_system_id BIGINT NOT NULL REFERENCES terminology.code_systems(id),
    active BOOLEAN NOT NULL DEFAULT true
);

-- Índices otimizados para operação $lookup
-- Índice principal: lookup por system + code (caso mais comum)
CREATE INDEX idx_concept_lookup ON terminology.concepts(system, code) WHERE active = true;

-- Índice para lookup com versão específica
CREATE INDEX idx_concept_lookup_version ON terminology.concepts(system, code, version) WHERE active = true;

-- Índice para buscar conceitos por CodeSystem
CREATE INDEX idx_concept_code_system ON terminology.concepts(code_system_id);

-- Índice GIN para busca em designations e properties (futuras operações)
CREATE INDEX idx_concept_designations ON terminology.concepts USING GIN(designations);
CREATE INDEX idx_concept_properties ON terminology.concepts USING GIN(properties);

-- Comentários para documentação
COMMENT ON TABLE terminology.packages IS 'Packages FHIR carregados no servidor';
COMMENT ON TABLE terminology.code_systems IS 'CodeSystems extraídos dos packages';
COMMENT ON TABLE terminology.concepts IS 'Conceitos (códigos) dos CodeSystems - otimizado para operação $lookup';

COMMENT ON COLUMN terminology.code_systems.id IS 'Surrogate key (gerado pelo banco)';
COMMENT ON COLUMN terminology.code_systems.resource_id IS 'ID lógico FHIR do recurso (business key)';
COMMENT ON COLUMN terminology.code_systems.url IS 'URL canônica do CodeSystem';
COMMENT ON COLUMN terminology.code_systems.status IS 'Status de publicação FHIR: DRAFT, ACTIVE, RETIRED, NULL (usa enum org.hl7.fhir.r4.model.Enumerations.PublicationStatus)';

COMMENT ON INDEX idx_concept_lookup IS 'Índice principal para operação $lookup (system, code)';
COMMENT ON INDEX idx_concept_lookup_version IS 'Índice para $lookup com versão específica';
COMMENT ON INDEX idx_cs_status IS 'Índice para filtrar por status de publicação';
COMMENT ON INDEX idx_cs_resource_id IS 'Índice para buscar por ID FHIR do recurso';
