package br.gov.go.saude.athena.controller;

import br.gov.go.saude.athena.domain.CodeSystemEntity;
import br.gov.go.saude.athena.repository.CodeSystemRepository;
import br.gov.go.saude.athena.repository.ConceptDisplayProjection;
import br.gov.go.saude.athena.repository.ConceptRepository;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Parameters;
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
    private CodeSystemRepository codeSystemRepository;

    @Mock
    private ConceptRepository conceptRepository;

    private CodeSystemController controller;
    private FhirContext fhirContext;
    private IParser jsonParser;

    @BeforeEach
    void setUp() {
        fhirContext = FhirContext.forR4();
        jsonParser = fhirContext.newJsonParser();
        controller = new CodeSystemController(codeSystemRepository, conceptRepository, fhirContext);
    }

    @Test
    void shouldGetCodeSystemById() {
        // Arrange
        String id = "test-cs";
        String jsonContent = "{\"resourceType\":\"CodeSystem\",\"id\":\"test-cs\",\"url\":\"http://test.com\",\"status\":\"active\"}";
        CodeSystemEntity entity = new CodeSystemEntity();
        entity.setContent(jsonContent.getBytes());

        when(codeSystemRepository.findByResourceIdAndIsLatestTrue(id)).thenReturn(Optional.of(entity));

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
        when(codeSystemRepository.findByResourceIdAndIsLatestTrue(id)).thenReturn(Optional.empty());
        when(codeSystemRepository.findById(-1L)).thenReturn(Optional.empty());

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

        when(codeSystemRepository.findByUrlAndIsLatestTrue(url)).thenReturn(Optional.of(entity));

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

        ConceptDisplayProjection projection = mock(ConceptDisplayProjection.class);
        when(projection.getDisplay()).thenReturn(display);

        when(conceptRepository.findDisplayBySystemAndCodeAndActiveTrue(system, code))
                .thenReturn(Optional.of(projection));

        // Act
        String result = controller.lookup(system, code);

        // Assert
        assertNotNull(result);
        Parameters parameters = jsonParser.parseResource(Parameters.class, result);

        assertTrue(parameters.hasParameter("display"));
        assertEquals(display,
                ((org.hl7.fhir.r4.model.StringType) parameters.getParameter("display").getValue()).getValue());
    }

    @Test
    void shouldReturnNotFoundWhenLookupFails() {
        // Arrange
        String system = "http://test.com/cs";
        String code = "UNKNOWN";

        when(conceptRepository.findDisplayBySystemAndCodeAndActiveTrue(system, code))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.lookup(system, code));
        assertEquals(404, exception.getStatusCode().value());
    }
}
