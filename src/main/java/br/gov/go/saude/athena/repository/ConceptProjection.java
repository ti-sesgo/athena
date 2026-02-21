package br.gov.go.saude.athena.repository;

public interface ConceptProjection {
    String getDisplay();

    String getCodeSystemName();

    String getCodeSystemVersion();

    String getDefinition();
}
