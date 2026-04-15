-- Tabela de ValueSets
-- ValueSet não é fragmentável (spec FHIR R4). Uma linha por (url, version).
CREATE TABLE terminology.value_sets (
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
    CONSTRAINT uk_vs_url_version UNIQUE (url, version)
);

CREATE INDEX idx_vs_resource_id_active ON terminology.value_sets(resource_id, active);
CREATE INDEX idx_vs_url_active_version ON terminology.value_sets(url, active, version);
CREATE INDEX idx_vs_url_active_is_latest ON terminology.value_sets(url, active, is_latest);

COMMENT ON TABLE terminology.value_sets IS 'ValueSets extraídos dos packages FHIR';
COMMENT ON COLUMN terminology.value_sets.resource_id IS 'ID lógico FHIR do recurso (business key)';
COMMENT ON COLUMN terminology.value_sets.url IS 'URL canônica do ValueSet';
COMMENT ON COLUMN terminology.value_sets.content IS 'Conteúdo completo do recurso FHIR em JSON';
