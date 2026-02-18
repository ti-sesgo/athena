package br.gov.go.saude.athena.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.CodeableConcept;
import ca.uhn.fhir.parser.DataFormatException;

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
}
