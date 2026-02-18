package br.gov.go.saude.athena.controller;

import br.gov.go.saude.athena.repository.ConceptDisplayProjection;
import br.gov.go.saude.athena.service.CodeSystemService;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.hl7.fhir.r4.model.OperationOutcome;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeSystemControllerTest {

        @Mock
        private CodeSystemService codeSystemService;

        private CodeSystemController controller;

        @BeforeEach
        void setUp() {
                controller = new CodeSystemController(codeSystemService);
        }

        @Test
        void shouldGetCodeSystemById() {
                // Arrange
                String id = "test-cs";
                CodeSystem cs = new CodeSystem();
                cs.setId(id);
                cs.setUrl("http://test.com/cs");
                cs.setName("Test Code System");
                cs.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);

                when(codeSystemService.findResourceById(id)).thenReturn(Optional.of(cs));

                // Act
                ResponseEntity<IBaseResource> response = controller.getCodeSystemById(id);

                // Assert
                assertEquals(200, response.getStatusCode().value());
                IBaseResource result = response.getBody();
                assertNotNull(result);
                assertTrue(result instanceof CodeSystem);
                CodeSystem resultCs = (CodeSystem) result;

                assertEquals(id, resultCs.getId());
                assertEquals(cs.getUrl(), resultCs.getUrl());
                assertEquals(cs.getName(), resultCs.getName());
                assertEquals(CodeSystem.CodeSystemContentMode.COMPLETE, resultCs.getContent());
        }

        @Test
        void shouldReturnNotFoundWhenCodeSystemIdDoesNotExist() {
                // Arrange
                String id = "unknown";
                when(codeSystemService.findResourceById(id)).thenReturn(Optional.empty());

                // Act
                assertThrows(ResponseStatusException.class, () -> controller.getCodeSystemById(id));
        }

        @Test
        void shouldGetCodeSystemByUrl() {
                // Arrange
                String url = "http://test.com/cs";
                CodeSystem cs = new CodeSystem();
                cs.setId("test-cs");
                cs.setUrl(url);
                cs.setName("Test Code System");
                cs.setContent(CodeSystem.CodeSystemContentMode.NOTPRESENT);

                when(codeSystemService.findResourceByUrl(url)).thenReturn(Optional.of(cs));

                // Act
                ResponseEntity<IBaseResource> response = controller.getCodeSystemByUrl(url);

                // Assert
                assertEquals(200, response.getStatusCode().value());
                IBaseResource result = response.getBody();
                assertNotNull(result);
                assertTrue(result instanceof Bundle);
                Bundle bundle = (Bundle) result;

                assertEquals(1, bundle.getTotal());
                assertTrue(bundle.hasEntry());
                assertTrue(bundle.getEntryFirstRep().getResource() instanceof CodeSystem);

                CodeSystem resultCs = (CodeSystem) bundle.getEntryFirstRep().getResource();
                assertEquals(url, resultCs.getUrl());
                assertEquals(cs.getName(), resultCs.getName());
                assertEquals(CodeSystem.CodeSystemContentMode.NOTPRESENT, resultCs.getContent());
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
                ResponseEntity<IBaseResource> response = controller.lookup(system, code, null);

                // Assert
                assertEquals(200, response.getStatusCode().value());

                IBaseResource result = response.getBody();
                assertNotNull(result);
                assertTrue(result instanceof Parameters);
                Parameters parameters = (Parameters) result;

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
                ResponseEntity<IBaseResource> response = controller.lookup(system, code, version);

                // Assert
                assertEquals(200, response.getStatusCode().value());

                IBaseResource result = response.getBody();
                assertNotNull(result);
                assertTrue(result instanceof Parameters);
                Parameters parameters = (Parameters) result;

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
                ResponseEntity<IBaseResource> response = controller.lookup(system, code, null);

                // Assert
                assertEquals(404, response.getStatusCode().value());

                IBaseResource result = response.getBody();
                assertNotNull(result);
                assertTrue(result instanceof OperationOutcome);
                OperationOutcome outcome = (OperationOutcome) result;

                assertTrue(outcome.hasIssue());
                assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssueFirstRep().getSeverity());
                assertEquals(OperationOutcome.IssueType.NOTFOUND, outcome.getIssueFirstRep().getCode());

                String diagnostic = outcome.getIssueFirstRep().getDiagnostics();
                assertTrue(diagnostic.contains("code[" + code + "]"));
                assertTrue(diagnostic.contains("system[" + system + "]"));
                assertFalse(diagnostic.contains("version"));

                assertTrue(outcome.getIssueFirstRep().hasDetails());
                assertEquals("Concept not found.", outcome.getIssueFirstRep().getDetails().getText());
        }

        @Test
        void shouldReturnNotFoundWhenLookupFailsWithVersion() {
                // Arrange
                String system = "http://test.com/cs";
                String code = "UNKNOWN";
                String version = "1.0.0";

                when(codeSystemService.findConcept(system, code, version))
                                .thenReturn(Optional.empty());

                // Act
                ResponseEntity<IBaseResource> response = controller.lookup(system, code, version);

                // Assert
                assertEquals(404, response.getStatusCode().value());

                IBaseResource result = response.getBody();
                assertNotNull(result);
                assertTrue(result instanceof OperationOutcome);
                OperationOutcome outcome = (OperationOutcome) result;

                assertTrue(outcome.hasIssue());
                assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssueFirstRep().getSeverity());
                assertEquals(OperationOutcome.IssueType.NOTFOUND, outcome.getIssueFirstRep().getCode());

                String diagnostic = outcome.getIssueFirstRep().getDiagnostics();
                assertTrue(diagnostic.contains("code[" + code + "]"));
                assertTrue(diagnostic.contains("system[" + system + "]"));
                assertTrue(diagnostic.contains("version[" + version + "]"));
        }

        @Test
        void shouldReturnBadRequestWhenSystemOrCodeIsMissing() {
                // Arrange
                String system = "http://test.com/cs";
                String code = null;

                // Act
                ResponseEntity<IBaseResource> response = controller.lookup(system, code, null);

                // Assert
                assertEquals(400, response.getStatusCode().value());

                IBaseResource result = response.getBody();
                assertNotNull(result);
                assertTrue(result instanceof OperationOutcome);
                OperationOutcome outcome = (OperationOutcome) result;

                assertTrue(outcome.hasIssue());
                assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssueFirstRep().getSeverity());
                assertEquals(OperationOutcome.IssueType.REQUIRED, outcome.getIssueFirstRep().getCode());
                assertTrue(outcome.getIssueFirstRep().getDiagnostics()
                                .contains("For lookup operation a client SHALL provide both"));

                assertTrue(outcome.getIssueFirstRep().hasDetails());
                assertEquals("Invalid or missing search parameters.",
                                outcome.getIssueFirstRep().getDetails().getText());
        }

        @Test
        void shouldPerformLookupViaPost() {
                // Arrange
                String system = "http://loinc.org";
                String code = "1963-8";
                String display = "Bicarbonate [Moles/volume] in Serum or Plasma";
                String csName = "LOINC";
                String csVersion = "2.69";

                ConceptDisplayProjection projection = mock(ConceptDisplayProjection.class);
                when(projection.getDisplay()).thenReturn(display);
                when(projection.getCodeSystemName()).thenReturn(csName);
                when(projection.getCodeSystemVersion()).thenReturn(csVersion);

                when(codeSystemService.findConcept(system, code)).thenReturn(Optional.of(projection));

                Parameters parameters = new Parameters();
                parameters.addParameter("coding", new Coding(system, code, null));

                // Act
                ResponseEntity<IBaseResource> response = controller.lookup(parameters);

                // Assert
                assertEquals(200, response.getStatusCode().value());
                IBaseResource result = response.getBody();
                assertNotNull(result);
                assertTrue(result instanceof Parameters);
                Parameters resultParams = (Parameters) result;

                assertTrue(resultParams.hasParameter("display"));
                assertEquals(display, ((StringType) resultParams.getParameter("display").getValue()).getValue());
        }

        @Test
        void shouldReturnBadRequestWhenPostParametersAreMissing() {
                // Act
                ResponseEntity<IBaseResource> response = controller.lookup(new Parameters());

                // Assert
                assertEquals(400, response.getStatusCode().value());
        }

        @Test
        void shouldReturnBadRequestWhenPostIsNull() {
                // Act
                ResponseEntity<IBaseResource> response = controller.lookup((Parameters) null);

                // Assert
                assertEquals(400, response.getStatusCode().value());
        }

        @Test
        void shouldReturnBadRequestWhenCodingIsMissingSystem() {
                // Arrange
                Parameters parameters = new Parameters();
                // Coding without system
                parameters.addParameter("coding", new Coding(null, "some-code", null));

                // Act
                ResponseEntity<IBaseResource> response = controller.lookup(parameters);

                // Assert
                assertEquals(400, response.getStatusCode().value());
        }

        @Test
        void shouldReturnBadRequestWhenCodingIsMissingCode() {
                // Arrange
                Parameters parameters = new Parameters();
                // Coding without code
                parameters.addParameter("coding", new Coding("http://some-system", null, null));

                // Act
                ResponseEntity<IBaseResource> response = controller.lookup(parameters);

                // Assert
                assertEquals(400, response.getStatusCode().value());
        }
}
