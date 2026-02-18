package br.gov.go.saude.athena.controller;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import java.util.Collections;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void shouldReturnOperationOutcomeOnMediaTypeNotAcceptable() {
        // Arrange
        HttpMediaTypeNotAcceptableException exception = new HttpMediaTypeNotAcceptableException(
                Collections.singletonList(MediaType.APPLICATION_JSON));

        // Act
        ResponseEntity<OperationOutcome> response = handler.handleMediaTypeNotAcceptable(exception);

        // Assert
        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
        assertEquals("application/fhir+json", response.getHeaders().getContentType().toString());

        OperationOutcome outcome = response.getBody();
        assertNotNull(outcome);
        assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssueFirstRep().getSeverity());
        assertEquals(OperationOutcome.IssueType.NOTSUPPORTED, outcome.getIssueFirstRep().getCode());
        assertTrue(outcome.getIssueFirstRep().getDetails().getText().contains("Accept header value is not supported"));
    }

    @Test
    void shouldReturnOperationOutcomeOnNoHandlerFound() {
        // Arrange
        NoHandlerFoundException exception = new NoHandlerFoundException("GET", "/fhir/non-existent", new HttpHeaders());

        // Act
        ResponseEntity<OperationOutcome> response = handler.handleNoHandlerFound(exception);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        OperationOutcome outcome = response.getBody();
        assertNotNull(outcome);
        assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssueFirstRep().getSeverity());
        assertEquals(OperationOutcome.IssueType.NOTFOUND, outcome.getIssueFirstRep().getCode());
        assertEquals("Resource or endpoint not found.", outcome.getIssueFirstRep().getDetails().getText());
    }

    @Test
    void shouldReturnOperationOutcomeOnMediaTypeNotSupported() {
        // Arrange
        HttpMediaTypeNotSupportedException exception = new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN,
                Collections.singletonList(MediaType.APPLICATION_JSON));

        // Act
        ResponseEntity<OperationOutcome> response = handler.handleMediaTypeNotSupported(exception);

        // Assert
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals("application/fhir+json", response.getHeaders().getContentType().toString());

        OperationOutcome outcome = response.getBody();
        assertNotNull(outcome);
        assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssueFirstRep().getSeverity());
        assertEquals(OperationOutcome.IssueType.NOTSUPPORTED, outcome.getIssueFirstRep().getCode());
        assertTrue(outcome.getIssueFirstRep().getDetails().getText().contains("Content type is not supported"));
    }
}
