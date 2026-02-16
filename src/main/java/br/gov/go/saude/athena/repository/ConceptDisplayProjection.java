package br.gov.go.saude.athena.repository;

/**
 * Projeção para retornar apenas o display name de um conceito.
 * Otimiza a operação de $lookup evitando SELECT * e permitindo Index Only Scan.
 */
public interface ConceptDisplayProjection {
    String getDisplay();

    String getCodeSystemName();

    String getCodeSystemVersion();

    String getDefinition();
}
