-- Persiste o CodeSystem.content (complete, fragment, supplement, not-present, example)
-- para que o loader possa diferenciar merges legítimos de fragments de colisões reais.
ALTER TABLE terminology.code_systems
    ADD COLUMN content_mode VARCHAR(32);

COMMENT ON COLUMN terminology.code_systems.content_mode IS
    'Valor do campo FHIR CodeSystem.content (CodeSystemContentMode)';
