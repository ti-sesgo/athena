package br.gov.go.saude.athena.controller;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FhirExceptionHandlingTest {

    private final FhirExceptionHandler handler = new FhirExceptionHandler();

    @Test
    void shouldReturnOperationOutcomeOnParsingException() {
        // Arrange
        String errorMessage = "JSON parse error: Unexpected character";
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(errorMessage, null, null);

        // Act
        ResponseEntity<OperationOutcome> response = handler.handleParsingException(exception);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        OperationOutcome outcome = response.getBody();
        assertNotNull(outcome);
        assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssueFirstRep().getSeverity());
        assertEquals(OperationOutcome.IssueType.INVALID, outcome.getIssueFirstRep().getCode());
        assertEquals(errorMessage, outcome.getIssueFirstRep().getDiagnostics());
    }
}
