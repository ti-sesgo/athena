package br.gov.go.saude.athena.controller;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.CodeableConcept;
import ca.uhn.fhir.parser.DataFormatException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;

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
        .contentType(MediaType.parseMediaType("application/fhir+json"))
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
        .contentType(MediaType.parseMediaType("application/fhir+json"))
        .body(outcome);
  }

  @ExceptionHandler({ NoHandlerFoundException.class,
      NoResourceFoundException.class })
  public ResponseEntity<OperationOutcome> handleNoHandlerFound(Exception e) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(OperationOutcome.IssueType.NOTFOUND)
        .setDiagnostics(e.getMessage())
        .setDetails(new CodeableConcept().setText("Resource or endpoint not found."));

    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.parseMediaType("application/fhir+json"))
        .body(outcome);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<OperationOutcome> handleResponseStatusException(ResponseStatusException e) {
    HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
    OperationOutcome.IssueType code = switch (status.series()) {
      case CLIENT_ERROR -> status == HttpStatus.NOT_FOUND
              ? OperationOutcome.IssueType.NOTFOUND
              : OperationOutcome.IssueType.INVALID;
      case SERVER_ERROR -> OperationOutcome.IssueType.EXCEPTION;
      default -> OperationOutcome.IssueType.PROCESSING;
    };
    OperationOutcome.IssueSeverity severity = status.is5xxServerError()
            ? OperationOutcome.IssueSeverity.FATAL
            : OperationOutcome.IssueSeverity.ERROR;

    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(severity)
        .setCode(code)
        .setDiagnostics(e.getReason() != null ? e.getReason() : e.getMessage())
        .setDetails(new CodeableConcept().setText(status.getReasonPhrase()));

    return ResponseEntity
        .status(status)
        .contentType(MediaType.parseMediaType("application/fhir+json"))
        .body(outcome);
  }

  @ExceptionHandler(IncorrectResultSizeDataAccessException.class)
  public ResponseEntity<OperationOutcome> handleIncorrectResultSize(IncorrectResultSizeDataAccessException e) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(OperationOutcome.IssueType.MULTIPLEMATCHES)
        .setDiagnostics("Multiple resources matched the query; expected unique result.")
        .setDetails(new CodeableConcept().setText("Ambiguous query result."));

    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .contentType(MediaType.parseMediaType("application/fhir+json"))
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
        .contentType(MediaType.parseMediaType("application/fhir+json"))
        .body(outcome);
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  public ResponseEntity<OperationOutcome> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException e) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(OperationOutcome.IssueType.NOTSUPPORTED)
        .setDiagnostics(e.getMessage())
        .setDetails(new CodeableConcept().setText("Accept header value is not supported. Supported media types: " +
            (e.getSupportedMediaTypes() != null ? e.getSupportedMediaTypes().toString() : "unknown")));

    return ResponseEntity
        .status(HttpStatus.NOT_ACCEPTABLE)
        .contentType(MediaType.parseMediaType("application/fhir+json"))
        .body(outcome);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<OperationOutcome> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
    OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setCode(OperationOutcome.IssueType.NOTSUPPORTED)
        .setDiagnostics(e.getMessage())
        .setDetails(new CodeableConcept().setText("Content type is not supported. Supported content types: " +
            (e.getSupportedMediaTypes() != null ? e.getSupportedMediaTypes().toString() : "unknown")));

    return ResponseEntity
        .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .contentType(MediaType.parseMediaType("application/fhir+json"))
        .body(outcome);
  }
}
