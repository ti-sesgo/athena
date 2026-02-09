package br.gov.go.saude.athena.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários para FHIR Context.
 */
class AppConfigTest {

    @Test
    void deveConfigurarFhirContextR4() {
        FhirContext context = FhirContext.forR4();
        
        assertNotNull(context);
        assertEquals("R4", context.getVersion().getVersion().name());
    }

    @Test
    void deveParsearCodeSystemJson() {
        FhirContext context = FhirContext.forR4();
        IParser parser = context.newJsonParser();
        
        String json = """
            {
              "resourceType": "CodeSystem",
              "id": "test",
              "url": "http://test.org",
              "status": "active",
              "content": "complete"
            }
            """;
        
        CodeSystem cs = parser.parseResource(CodeSystem.class, json);
        
        assertNotNull(cs);
        assertEquals("test", cs.getIdElement().getIdPart());
        assertEquals("http://test.org", cs.getUrl());
    }
}
