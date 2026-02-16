package br.gov.go.saude.athena.controller;

import br.gov.go.saude.athena.domain.CodeSystemEntity;
import br.gov.go.saude.athena.repository.ConceptDisplayProjection;
import br.gov.go.saude.athena.service.CodeSystemService;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeSystemControllerTest {

    @Mock
    private CodeSystemService codeSystemService;

    private CodeSystemController controller;
    private FhirContext fhirContext;
    private IParser jsonParser;

    @BeforeEach
    void setUp() {
        fhirContext = FhirContext.forR4();
        jsonParser = fhirContext.newJsonParser();
        controller = new CodeSystemController(codeSystemService, fhirContext);
    }

    @Test
    void shouldGetCodeSystemById() {
        // Arrange
        String id = "test-cs";
        String jsonContent = "{\"resourceType\":\"CodeSystem\",\"id\":\"test-cs\",\"url\":\"http://test.com\",\"status\":\"active\"}";
        CodeSystemEntity entity = new CodeSystemEntity();
        entity.setContent(jsonContent.getBytes());

        when(codeSystemService.findById(id)).thenReturn(Optional.of(entity));

        // Act
        String result = controller.getCodeSystemById(id);

        // Assert
        assertNotNull(result);
        CodeSystem cs = jsonParser.parseResource(CodeSystem.class, result);
        assertEquals("test-cs", cs.getIdElement().getIdPart());
        assertEquals("http://test.com", cs.getUrl());
    }

    @Test
    void shouldReturnNotFoundWhenCodeSystemIdDoesNotExist() {
        // Arrange
        String id = "non-existent";
        when(codeSystemService.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.getCodeSystemById(id));
        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void shouldGetCodeSystemByUrl() {
        // Arrange
        String url = "http://test.com/cs";
        String jsonContent = "{\"resourceType\":\"CodeSystem\",\"id\":\"cs-1\",\"url\":\"http://test.com/cs\",\"status\":\"active\"}";
        CodeSystemEntity entity = new CodeSystemEntity();
        entity.setContent(jsonContent.getBytes());

        when(codeSystemService.findByUrl(url)).thenReturn(Optional.of(entity));

        // Act
        String result = controller.getCodeSystemByUrl(url);

        // Assert
        assertNotNull(result);
        Bundle bundle = jsonParser.parseResource(Bundle.class, result);
        assertEquals(Bundle.BundleType.SEARCHSET, bundle.getType());
        assertEquals(1, bundle.getTotal());
        assertTrue(bundle.hasEntry());

        CodeSystem cs = (CodeSystem) bundle.getEntryFirstRep().getResource();
        assertEquals("http://test.com/cs", cs.getUrl());
    }

    @Test
    void shouldPerformLookup() {
        // Arrange
        String system = "http://test.com/cs";
        String code = "TEST-CODE";
        String display = "Test Display Name";
        String csName = "Test Code System";
        String csVersion = "1.0.0";
        String definition = "Test Definition";

        ConceptDisplayProjection projection = mock(ConceptDisplayProjection.class);
        when(projection.getDisplay()).thenReturn(display);
        when(projection.getCodeSystemName()).thenReturn(csName);
        when(projection.getCodeSystemVersion()).thenReturn(csVersion);
        when(projection.getDefinition()).thenReturn(definition);

        when(codeSystemService.findConcept(system, code))
                .thenReturn(Optional.of(projection));

        // Act
        ResponseEntity<String> response = controller.lookup(system, code, null);

        // Assert
        assertEquals(200, response.getStatusCode().value());

        String result = response.getBody();
        assertNotNull(result);
        Parameters parameters = jsonParser.parseResource(Parameters.class, result);

        assertTrue(parameters.hasParameter("display"));
        assertEquals(display,
                ((StringType) parameters.getParameter("display").getValue()).getValue());

        assertTrue(parameters.hasParameter("name"));
        assertEquals(csName,
                ((StringType) parameters.getParameter("name").getValue()).getValue());

        assertTrue(parameters.hasParameter("version"));
        assertEquals(csVersion,
                ((StringType) parameters.getParameter("version").getValue()).getValue());

        assertTrue(parameters.hasParameter("definition"));
        assertEquals(definition,
                ((StringType) parameters.getParameter("definition").getValue()).getValue());
    }

    @Test
    void shouldPerformLookupWithVersion() {
        // Arrange
        String system = "http://test.com/cs";
        String code = "TEST-CODE";
        String version = "1.0.0";
        String display = "Test Display Name";
        String csName = "Test Code System";
        String csVersion = "1.0.0";

        ConceptDisplayProjection projection = mock(ConceptDisplayProjection.class);
        when(projection.getDisplay()).thenReturn(display);
        when(projection.getCodeSystemName()).thenReturn(csName);
        when(projection.getCodeSystemVersion()).thenReturn(csVersion);

        when(codeSystemService.findConcept(system, code, version))
                .thenReturn(Optional.of(projection));

        // Act
        ResponseEntity<String> response = controller.lookup(system, code, version);

        // Assert
        assertEquals(200, response.getStatusCode().value());

        String result = response.getBody();
        assertNotNull(result);
        Parameters parameters = jsonParser.parseResource(Parameters.class, result);

        assertTrue(parameters.hasParameter("display"));
        assertEquals(display,
                ((StringType) parameters.getParameter("display").getValue()).getValue());
        assertTrue(parameters.hasParameter("version"));
        assertEquals(version,
                ((StringType) parameters.getParameter("version").getValue()).getValue());
    }

    @Test
    void shouldReturnNotFoundWhenLookupFails() {
        // Arrange
        String system = "http://test.com/cs";
        String code = "UNKNOWN";

        when(codeSystemService.findConcept(system, code))
                .thenReturn(Optional.empty());

        // Act
        ResponseEntity<String> response = controller.lookup(system, code, null);

        // Assert
        assertEquals(404, response.getStatusCode().value());

        String result = response.getBody();
        assertNotNull(result);

        OperationOutcome outcome = jsonParser.parseResource(OperationOutcome.class, result);
        assertNotNull(outcome);
        assertTrue(outcome.hasIssue());
        assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssueFirstRep().getSeverity());
        assertEquals(OperationOutcome.IssueType.NOTFOUND, outcome.getIssueFirstRep().getCode());
        assertTrue(outcome.getIssueFirstRep().getDiagnostics().contains(code));
    }

    @Test
    void shouldReturnBadRequestWhenSystemOrCodeIsMissing() {
        // Arrange
        String system = "http://test.com/cs";
        String code = null;

        // Act
        ResponseEntity<String> response = controller.lookup(system, code, null);

        // Assert
        assertEquals(400, response.getStatusCode().value());

        String result = response.getBody();
        assertNotNull(result);

        OperationOutcome outcome = jsonParser.parseResource(OperationOutcome.class, result);
        assertNotNull(outcome);
        assertTrue(outcome.hasIssue());
        assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssueFirstRep().getSeverity());
        assertEquals(OperationOutcome.IssueType.INVALID, outcome.getIssueFirstRep().getCode());
        assertTrue(outcome.getIssueFirstRep().getDiagnostics().contains("System and code are required"));
    }
}
