package br.gov.go.saude.athena.exception;

public class ConceptNotFoundException extends RuntimeException {
    public ConceptNotFoundException(String message) {
        super(message);
    }
}
