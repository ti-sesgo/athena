package br.gov.go.saude.athena.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.Enumerations.FHIRVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityStatementControllerTest {

    private CapabilityStatementController controller;
    private IParser jsonParser;

    @BeforeEach
    void setUp() {
        // Use real HAPI FHIR Context and Parser
        FhirContext fhirContext = FhirContext.forR4();
        jsonParser = fhirContext.newJsonParser();

        controller = new CapabilityStatementController(jsonParser);
    }

    @Test
    void shouldCacheAndReturnValidCapabilityStatement() {
        // Act
        controller.init(); // Initialize the controller (trigger @PostConstruct logic)
        String jsonResult = controller.getMetadata();

        // Assert - Basic non-null check
        assertNotNull(jsonResult);
        assertFalse(jsonResult.isEmpty());

        // Assert - Parse back to object to verify content validity
        CapabilityStatement cs = jsonParser.parseResource(CapabilityStatement.class, jsonResult);

        assertNotNull(cs);
        assertEquals(PublicationStatus.ACTIVE, cs.getStatus());
        assertEquals("SES-GO", cs.getPublisher());
        assertEquals(CapabilityStatement.CapabilityStatementKind.INSTANCE, cs.getKind());
        assertEquals(FHIRVersion._4_0_1, cs.getFhirVersion());

        // Setup Check
        assertNotNull(cs.getSoftware());
        assertEquals("Athena Terminology Server", cs.getSoftware().getName());
        assertEquals("0.0.1", cs.getSoftware().getVersion());

        // Formats
        assertTrue(cs.getFormat().stream().anyMatch(f -> f.getCode().equals("application/fhir+json")));
        assertTrue(cs.getFormat().stream().anyMatch(f -> f.getCode().equals("json")));

        // REST Resources
        assertTrue(cs.hasRest());
        assertEquals(1, cs.getRest().size());
        assertEquals(CapabilityStatement.RestfulCapabilityMode.SERVER, cs.getRestFirstRep().getMode());

        // CodeSystem Resource
        CapabilityStatement.CapabilityStatementRestResourceComponent codeSystem = cs.getRestFirstRep().getResource()
                .stream()
                .filter(r -> "CodeSystem".equals(r.getType()))
                .findFirst()
                .orElse(null);

        assertNotNull(codeSystem, "Should contain CodeSystem resource definition");

        // Interactions
        assertTrue(codeSystem.getInteraction().stream()
                .anyMatch(i -> i.getCode() == CapabilityStatement.TypeRestfulInteraction.READ));
        assertTrue(codeSystem.getInteraction().stream()
                .anyMatch(i -> i.getCode() == CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE));

        // Operations
        assertTrue(codeSystem.getOperation().stream()
                .anyMatch(op -> "lookup".equals(op.getName()) &&
                        "http://hl7.org/fhir/OperationDefinition/CodeSystem-lookup".equals(op.getDefinition())));
    }
}
