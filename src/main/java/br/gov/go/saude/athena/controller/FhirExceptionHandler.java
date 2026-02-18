package br.gov.go.saude.athena.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.CodeableConcept;
import ca.uhn.fhir.parser.DataFormatException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class FhirExceptionHandler {

  @ExceptionHandler({ HttpMessageNotReadableException.class, DataFormatException.class })
  public ResponseEntity<OperationOutcome> handleParsingException(Exception e) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(OperationOutcome.IssueType.INVALID)
        .setDiagnostics(e.getMessage())
        .setDetails(new CodeableConcept().setText("Invalid or incompatible payload."));

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(outcome);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<OperationOutcome> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(OperationOutcome.IssueType.NOTSUPPORTED) // Or PROCESSING
        .setDiagnostics(e.getMessage())
        .setDetails(new CodeableConcept().setText("Method not allowed."));

    return ResponseEntity
        .status(HttpStatus.METHOD_NOT_ALLOWED)
        .header("Allow", e.getSupportedHttpMethods() == null ? "" : e.getSupportedHttpMethods().toString())
        .body(outcome);
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<OperationOutcome> handleNoHandlerFound(NoHandlerFoundException e) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(OperationOutcome.IssueType.NOTFOUND)
        .setDiagnostics(e.getMessage())
        .setDetails(new CodeableConcept().setText("Resource or endpoint not found."));

    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(outcome);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<OperationOutcome> handleGenericException(Exception e) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.FATAL)
        .setCode(OperationOutcome.IssueType.EXCEPTION)
        .setDiagnostics(e.getMessage())
        .setDetails(new CodeableConcept().setText("Internal server error."));

    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(outcome);
  }
}
