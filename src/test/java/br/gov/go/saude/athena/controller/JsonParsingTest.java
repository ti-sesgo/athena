package br.gov.go.saude.athena.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonParsingTest {

    @Test
    void shouldParseParametersJsonWithCoding() {
        String json = """
                {
                  "resourceType": "Parameters",
                  "parameter": [
                    {
                      "name": "coding",
                      "valueCoding": {
                        "code": "T/L",
                        "system": "http://unitsofmeasure.org"
                      }
                    }
                  ]
                }
                """;

        FhirContext ctx = FhirContext.forR4();
        IParser parser = ctx.newJsonParser();
        Parameters parameters = parser.parseResource(Parameters.class, json);

        assertNotNull(parameters);
        Parameters.ParametersParameterComponent codingParam = parameters.getParameter("coding");
        assertNotNull(codingParam, "Coding parameter should be present");

        // Check if value is populated
        assertNotNull(codingParam.getValue(), "Value should not be null");
        assertTrue(codingParam.getValue() instanceof Coding, "Value should be of type Coding");

        Coding coding = (Coding) codingParam.getValue();
        assertEquals("T/L", coding.getCode());
        assertEquals("http://unitsofmeasure.org", coding.getSystem());
    }
}
