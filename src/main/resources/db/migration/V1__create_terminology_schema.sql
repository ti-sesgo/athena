-- Schema para terminologias FHIR
CREATE SCHEMA IF NOT EXISTS terminology;

-- Tabela de packages
CREATE TABLE terminology.packages (
    id BIGSERIAL PRIMARY KEY,
    package_id VARCHAR(255) NOT NULL,
    version VARCHAR(255) NOT NULL,
    registry_url VARCHAR(255),
    loaded_at TIMESTAMP NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (package_id, version)
);

-- Índices para packages
CREATE INDEX idx_package_id_version ON terminology.packages(package_id, version, active);

-- Tabela de CodeSystems
CREATE TABLE terminology.code_systems (
    id BIGSERIAL PRIMARY KEY,
    resource_id VARCHAR(255) NOT NULL,
    url VARCHAR(255) NOT NULL,
    version VARCHAR(255),
    name VARCHAR(255),
    title VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    content BYTEA NOT NULL,
    package_id BIGINT NOT NULL REFERENCES terminology.packages(id),
    is_latest BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uk_cs_url_version UNIQUE (url, version)
);

-- Índices para CodeSystems
CREATE INDEX idx_cs_resource_id_active ON terminology.code_systems(resource_id, active);
CREATE INDEX idx_cs_url_active_version ON terminology.code_systems(url, active, version);
CREATE INDEX idx_cs_url_active_is_latest ON terminology.code_systems(url, active, is_latest);

-- Tabela de conceitos (otimizada para $lookup)
CREATE TABLE terminology.concepts (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(255) NOT NULL,
    display VARCHAR(1000),
    definition TEXT,
    property TEXT,
    code_system_url VARCHAR(255) NOT NULL,
    code_system_name VARCHAR(255),
    code_system_version VARCHAR(255),
    code_system_is_latest BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    code_system_id BIGINT NOT NULL REFERENCES terminology.code_systems(id),
    CONSTRAINT uk_concept_code_url_version UNIQUE (code, code_system_url, code_system_version)
);

-- Índices otimizados para operação $lookup
CREATE INDEX idx_concept_lookup_version ON terminology.concepts(code_system_url, code, active, code_system_version);
CREATE INDEX idx_concept_lookup_is_latest ON terminology.concepts(code_system_url, code, active, code_system_is_latest);

-- Comentários para documentação
COMMENT ON TABLE terminology.packages IS 'Packages FHIR carregados no servidor';
COMMENT ON TABLE terminology.code_systems IS 'CodeSystems extraídos dos packages';
COMMENT ON TABLE terminology.concepts IS 'Conceitos (códigos) dos CodeSystems';

COMMENT ON COLUMN terminology.code_systems.id IS 'Surrogate key (gerado pelo banco)';
COMMENT ON COLUMN terminology.code_systems.resource_id IS 'ID lógico FHIR do recurso (business key)';
COMMENT ON COLUMN terminology.code_systems.url IS 'URL canônica do CodeSystem';
COMMENT ON COLUMN terminology.code_systems.status IS 'Status de publicação FHIR';
