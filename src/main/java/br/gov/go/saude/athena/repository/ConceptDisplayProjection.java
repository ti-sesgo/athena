package br.gov.go.saude.athena.repository;

public interface ConceptDisplayProjection {
    String getDisplay();

    String getCodeSystemName();

    String getCodeSystemVersion();

    String getDefinition();
}
