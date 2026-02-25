package br.gov.go.saude.athena.controller;

import br.gov.go.saude.athena.dto.ValidateCodeResult;
import br.gov.go.saude.athena.exception.ConceptNotFoundException;
import br.gov.go.saude.athena.service.CodeSystemService;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.UriType;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
                assertInstanceOf(CodeSystem.class, result);
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
                assertInstanceOf(Bundle.class, result);
                Bundle bundle = (Bundle) result;

                assertEquals(1, bundle.getTotal());
                assertTrue(bundle.hasEntry());
                assertInstanceOf(CodeSystem.class, bundle.getEntryFirstRep().getResource());

                CodeSystem resultCs = (CodeSystem) bundle.getEntryFirstRep().getResource();
                assertEquals(url, resultCs.getUrl());
                assertEquals(cs.getName(), resultCs.getName());
                assertEquals(CodeSystem.CodeSystemContentMode.NOTPRESENT, resultCs.getContent());
        }

        @Test
        void shouldPerformLookup() {
                // Arrange
                Parameters mockParams = new Parameters();
                mockParams.addParameter("name", new StringType("Test Code System"));
                mockParams.addParameter("version", new StringType("1.0.0"));
                mockParams.addParameter("display", new StringType("Test Display Name"));
                mockParams.addParameter("definition", new StringType("Test Definition"));

                when(codeSystemService.lookup("http://test.com/cs", "TEST-CODE", null)).thenReturn(mockParams);

                // Act
                Parameters parameters = (Parameters) controller.lookup("http://test.com/cs", "TEST-CODE", null)
                                .getBody();

                // Assert
                assertEquals("Test Display Name",
                                ((StringType) parameters.getParameter("display").getValue()).getValue());
                assertEquals("Test Code System", ((StringType) parameters.getParameter("name").getValue()).getValue());
                assertEquals("1.0.0", ((StringType) parameters.getParameter("version").getValue()).getValue());
                assertEquals("Test Definition",
                                ((StringType) parameters.getParameter("definition").getValue()).getValue());
        }

        @Test
        void shouldPerformLookupWithVersion() {
                // Arrange
                Parameters mockParams = new Parameters();
                mockParams.addParameter("version", new StringType("1.0.0"));
                mockParams.addParameter("display", new StringType("Test Display Name"));

                when(codeSystemService.lookup("http://test.com/cs", "TEST-CODE", "1.0.0")).thenReturn(mockParams);

                // Act
                Parameters parameters = (Parameters) controller.lookup("http://test.com/cs", "TEST-CODE", "1.0.0")
                                .getBody();

                // Assert
                assertEquals("Test Display Name",
                                ((StringType) parameters.getParameter("display").getValue()).getValue());
                assertEquals("1.0.0", ((StringType) parameters.getParameter("version").getValue()).getValue());
        }

        @Test
        void shouldReturnNotFoundWhenLookupFails() {
                // Arrange
                when(codeSystemService.lookup("http://test.com/cs", "UNKNOWN", null))
                                .thenThrow(new ConceptNotFoundException(
                                                "Unable to find code[UNKNOWN] in system[http://test.com/cs]"));

                // Act
                ResponseEntity<IBaseResource> response;
                try {
                        response = controller.lookup("http://test.com/cs", "UNKNOWN", null);
                } catch (ConceptNotFoundException e) {
                        response = controller.handleConceptNotFound(e);
                }

                // Assert
                assertEquals(404, response.getStatusCode().value());
                OperationOutcome outcome = (OperationOutcome) response.getBody();

                assertEquals(OperationOutcome.IssueType.NOTFOUND, outcome.getIssueFirstRep().getCode());
                assertTrue(outcome.getIssueFirstRep().getDiagnostics().contains("code[UNKNOWN]"));
                assertFalse(outcome.getIssueFirstRep().getDiagnostics().contains("version"));
        }

        @Test
        void shouldReturnNotFoundWhenLookupFailsWithVersion() {
                // Arrange
                when(codeSystemService.lookup("http://test.com/cs", "UNKNOWN", "1.0.0"))
                                .thenThrow(new ConceptNotFoundException(
                                                "Unable to find code[UNKNOWN] in system[http://test.com/cs] version[1.0.0]"));

                // Act
                ResponseEntity<IBaseResource> response;
                try {
                        response = controller.lookup("http://test.com/cs", "UNKNOWN", "1.0.0");
                } catch (ConceptNotFoundException e) {
                        response = controller.handleConceptNotFound(e);
                }

                // Assert
                assertEquals(404, response.getStatusCode().value());
                OperationOutcome outcome = (OperationOutcome) response.getBody();

                assertTrue(outcome.getIssueFirstRep().getDiagnostics().contains("version[1.0.0]"));
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
                assertInstanceOf(OperationOutcome.class, result);
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
                Parameters mockParams = new Parameters();
                mockParams.addParameter("display", new StringType("Bicarbonate [Moles/volume] in Serum or Plasma"));

                when(codeSystemService.lookup("http://loinc.org", "1963-8", null)).thenReturn(mockParams);

                Parameters parameters = new Parameters();
                parameters.addParameter("coding", new Coding("http://loinc.org", "1963-8", null));

                // Act
                Parameters resultParams = (Parameters) controller.lookup(parameters).getBody();

                // Assert
                assertEquals("Bicarbonate [Moles/volume] in Serum or Plasma",
                                ((StringType) resultParams.getParameter("display").getValue()).getValue());
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
                ResponseEntity<IBaseResource> response = controller.lookup(null);

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

        @Test
        void shouldPerformLookupViaPostWithIndividualParameters() {
                // Arrange — POST com system e code (sem ser Coding)
                Parameters mockParams = new Parameters();
                mockParams.addParameter("display", new StringType("Médico clínico"));
                mockParams.addParameter("name", new StringType("CBO"));

                when(codeSystemService.lookup("http://www.saude.gov.br/fhir/r4/CodeSystem/BRCBO", "225125", null))
                                .thenReturn(mockParams);

                Parameters parameters = new Parameters();
                parameters.addParameter("system", new UriType("http://www.saude.gov.br/fhir/r4/CodeSystem/BRCBO"));
                parameters.addParameter("code", new CodeType("225125"));

                // Act
                Parameters resultParams = (Parameters) controller.lookup(parameters).getBody();

                // Assert
                assertEquals("Médico clínico",
                                ((StringType) resultParams.getParameter("display").getValue()).getValue());
        }

        @Test
        void shouldNotLeakUriTypeInDiagnosticsWhenPostNotFound() {
                // Arrange — ensures primitiveValue() is used, not toString()
                String system = "http://www.saude.gov.br/fhir/r4/CodeSystem/BRCBO";
                String code = "INVALID";

                when(codeSystemService.lookup(system, code, null))
                                .thenThrow(new br.gov.go.saude.athena.exception.ConceptNotFoundException(
                                                "Unable to find code[" + code + "] in system[" + system + "]"));

                Parameters parameters = new Parameters();
                parameters.addParameter("system", new UriType(system));
                parameters.addParameter("code", new CodeType(code));

                // Act
                ResponseEntity<IBaseResource> response;
                try {
                        response = controller.lookup(parameters);
                } catch (ConceptNotFoundException e) {
                        response = controller.handleConceptNotFound(e);
                }

                // Assert
                assertEquals(404, response.getStatusCode().value());
                OperationOutcome outcome = (OperationOutcome) response.getBody();
                assertNotNull(outcome);

                String diagnostic = outcome.getIssueFirstRep().getDiagnostics();
                // Must contain raw system URL, never UriType[...] wrapper
                assertTrue(diagnostic.contains("system[" + system + "]"),
                                "Diagnostic should contain raw system URL, got: " + diagnostic);
                assertFalse(diagnostic.contains("UriType"),
                                "Diagnostic must not contain UriType wrapper, got: " + diagnostic);
                assertTrue(diagnostic.contains("code[" + code + "]"));
        }

        @Test
        void shouldPerformLookupViaPostWithIndividualParametersAndVersion() {
                // Arrange
                Parameters mockParams = new Parameters();
                mockParams.addParameter("version", new StringType("2.0"));
                when(codeSystemService.lookup("http://www.saude.gov.br/fhir/r4/CodeSystem/BRCBO", "225125", "2.0"))
                                .thenReturn(mockParams);

                Parameters parameters = new Parameters();
                parameters.addParameter("system", new UriType("http://www.saude.gov.br/fhir/r4/CodeSystem/BRCBO"));
                parameters.addParameter("code", new CodeType("225125"));
                parameters.addParameter("version", new StringType("2.0"));

                // Act
                Parameters resultParams = (Parameters) controller.lookup(parameters).getBody();

                // Assert
                assertEquals("2.0", ((StringType) resultParams.getParameter("version").getValue()).getValue());
        }

        @Test
        void shouldPerformLookupWithProperties() {
                // Arrange
                Parameters mockParams = new Parameters();
                Parameters.ParametersParameterComponent paramProperty = mockParams.addParameter().setName("property");
                paramProperty.addPart().setName("code").setValue(new CodeType("status"));
                paramProperty.addPart().setName("value").setValue(new StringType("active"));

                when(codeSystemService.lookup("system", "code", null)).thenReturn(mockParams);

                // Act
                Parameters responseParams = (Parameters) controller.lookup("system", "code", null).getBody();

                // Assert
                Parameters.ParametersParameterComponent property = responseParams.getParameter("property");
                assertNotNull(property);

                String actualCode = ((CodeType) property.getPart().get(0).getValue()).getValue();
                String actualValue = ((StringType) property.getPart().get(1).getValue()).getValue();

                assertEquals("status", actualCode);
                assertEquals("active", actualValue);
        }

        @Test
        void shouldPerformLookupWithDesignation() {
                // Arrange
                Parameters mockParams = new Parameters();
                Parameters.ParametersParameterComponent paramDesignation = mockParams.addParameter()
                                .setName("designation");
                paramDesignation.addPart().setName("language").setValue(new CodeType("pt-BR"));
                paramDesignation.addPart().setName("use")
                                .setValue(new Coding("http://snomed.info/sct", "900000000000013009", "Synonym"));
                paramDesignation.addPart().setName("value").setValue(new StringType("Paracetamol"));

                when(codeSystemService.lookup("system", "code", null)).thenReturn(mockParams);

                // Act
                Parameters responseParams = (Parameters) controller.lookup("system", "code", null).getBody();

                // Assert
                Parameters.ParametersParameterComponent designation = responseParams.getParameter("designation");
                assertNotNull(designation);

                String actualLanguage = ((CodeType) designation.getPart().get(0).getValue()).getValue();
                Coding actualUse = (Coding) designation.getPart().get(1).getValue();
                String actualValue = ((StringType) designation.getPart().get(2).getValue()).getValue();

                assertEquals("pt-BR", actualLanguage);
                assertEquals("http://snomed.info/sct", actualUse.getSystem());
                assertEquals("900000000000013009", actualUse.getCode());
                assertEquals("Synonym", actualUse.getDisplay());
                assertEquals("Paracetamol", actualValue);
        }

        // --- $validate-code ---

        @Test
        void shouldReturnResultTrueWhenCodeIsValid() {
                when(codeSystemService.validateCode("http://test.com/cs", "TEST-CODE", null, null))
                                .thenReturn(new ValidateCodeResult(true, null, "Test Display"));

                var response = controller.validateCode("http://test.com/cs", "TEST-CODE", null, null);

                assertEquals(200, response.getStatusCode().value());
                Parameters params = (Parameters) response.getBody();
                assertTrue(result(params));
                assertEquals("Test Display", display(params));
                assertNull(params.getParameter("message"));
        }

        @Test
        void shouldReturnResultFalseWhenCodeIsInvalid() {
                when(codeSystemService.validateCode("http://test.com/cs", "UNKNOWN", null, null))
                                .thenReturn(new ValidateCodeResult(false, "Unable to find code[UNKNOWN] in system[http://test.com/cs]", null));

                var response = controller.validateCode("http://test.com/cs", "UNKNOWN", null, null);

                assertEquals(200, response.getStatusCode().value());
                assertFalse(result((Parameters) response.getBody()));
                assertEquals("Unable to find code[UNKNOWN] in system[http://test.com/cs]", message((Parameters) response.getBody()));
        }

        @Test
        void shouldReturn400WhenUrlMissing() {
                var response = controller.validateCode(null, "code", null, null);

                assertEquals(400, response.getStatusCode().value());
                assertTrue(((OperationOutcome) response.getBody()).getIssueFirstRep().getDiagnostics().contains("url"));
        }

        @Test
        void shouldReturn400WhenCodeMissing() {
                var response = controller.validateCode("http://test.com/cs", null, null, null);

                assertEquals(400, response.getStatusCode().value());
                assertTrue(((OperationOutcome) response.getBody()).getIssueFirstRep().getDiagnostics().contains("code"));
        }

        @Test
        void shouldValidateCodeById() {
                CodeSystem cs = new CodeSystem();
                cs.setId("loinc");
                cs.setUrl("http://loinc.org");
                when(codeSystemService.findResourceById("loinc")).thenReturn(Optional.of(cs));
                when(codeSystemService.validateCode("http://loinc.org", "1963-8", null, null))
                                .thenReturn(new ValidateCodeResult(true, null, "Bicarbonate [Moles/volume] in Serum"));

                var response = controller.validateCodeById("loinc", "1963-8", null, null);

                assertEquals(200, response.getStatusCode().value());
                Parameters params = (Parameters) response.getBody();
                assertTrue(result(params));
                assertEquals("Bicarbonate [Moles/volume] in Serum", display(params));
        }

        @Test
        void shouldValidateCodePostWithUrlAndCode() {
                when(codeSystemService.validateCode("http://test.com/cs", "225125", "2.0", null))
                                .thenReturn(new ValidateCodeResult(true, null, "Médico clínico"));

                Parameters input = params("url", "http://test.com/cs");
                input.addParameter("code", new CodeType("225125"));
                input.addParameter("version", new StringType("2.0"));

                var response = controller.validateCode(input);

                assertEquals(200, response.getStatusCode().value());
                assertTrue(result((Parameters) response.getBody()));
                assertEquals("Médico clínico", display((Parameters) response.getBody()));
        }

        @Test
        void shouldValidateCodePostWithCoding() {
                when(codeSystemService.validateCode("http://loinc.org", "1963-8", null, "test"))
                                .thenReturn(new ValidateCodeResult(false, "The display \"test\" is incorrect.", "Bicarbonate [Moles/volume] in Serum"));

                Parameters input = params("url", "http://loinc.org");
                input.addParameter("coding", new Coding("http://loinc.org", "1963-8", "test"));

                var response = controller.validateCode(input);

                assertEquals(200, response.getStatusCode().value());
                Parameters params = (Parameters) response.getBody();
                assertFalse(result(params));
                assertEquals("The display \"test\" is incorrect.", message(params));
                assertEquals("Bicarbonate [Moles/volume] in Serum", display(params));
        }

        @Test
        void shouldValidateCodePostWithCodeableConcept() {
                when(codeSystemService.validateCode("http://example.org", "code", null, "test"))
                                .thenReturn(new ValidateCodeResult(true, null, "test"));

                Parameters input = params("url", "http://example.org");
                input.addParameter("codeableConcept", new CodeableConcept().addCoding(new Coding("http://example.org", "code", "test")));

                var response = controller.validateCode(input);

                assertEquals(200, response.getStatusCode().value());
                assertTrue(result((Parameters) response.getBody()));
        }

        @Test
        void shouldReturn400WhenPostParametersEmpty() {
                var response = controller.validateCode(new Parameters());

                assertEquals(400, response.getStatusCode().value());
        }

        @Test
        void shouldReturn400WhenPostParametersNull() {
                var response = controller.validateCode(null);

                assertEquals(400, response.getStatusCode().value());
        }

        @Test
        void shouldReturn400WhenCodingSystemDoesNotMatchUrl() {
                Parameters input = params("url", "http://loinc.org");
                input.addParameter("coding", new Coding("http://wrong.system.org", "1963-8", null));

                var response = controller.validateCode(input);

                assertEquals(400, response.getStatusCode().value());
        }

        @Test
        void shouldReturn400WhenCodeSystemParameterProvided() {
                Parameters input = new Parameters();
                input.addParameter().setName("codeSystem").setResource(new CodeSystem());
                input.addParameter("code", new CodeType("CODE-1"));

                var response = controller.validateCode(input);

                assertEquals(400, response.getStatusCode().value());
                assertTrue(((OperationOutcome) response.getBody()).getIssueFirstRep().getDiagnostics().contains("codeSystem"));
                verify(codeSystemService, never()).validateCode(any(), any(), any(), any());
        }

        @Test
        void shouldReturn400WhenCodingAndCodeableConceptBothProvided() {
                Parameters input = params("url", "http://example.org");
                input.addParameter("coding", new Coding("http://example.org", "code1", null));
                input.addParameter("codeableConcept", new CodeableConcept().addCoding(new Coding("http://example.org", "code2", null)));

                var response = controller.validateCode(input);

                assertEquals(400, response.getStatusCode().value());
                verify(codeSystemService, never()).validateCode(any(), any(), any(), any());
        }

        private static Parameters params(String name, String value) {
                Parameters p = new Parameters();
                p.addParameter(name, new UriType(value));
                return p;
        }

        private static boolean result(Parameters params) {
                return ((BooleanType) params.getParameter("result").getValue()).getValue();
        }

        private static String display(Parameters params) {
                var p = params.getParameter("display");
                return p != null ? ((StringType) p.getValue()).getValue() : null;
        }

        private static String message(Parameters params) {
                var p = params.getParameter("message");
                return p != null ? ((StringType) p.getValue()).getValue() : null;
        }
}
