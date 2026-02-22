package br.gov.go.saude.athena.domain;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConceptEntityTest {

    @Test
    void testGetPropertyDeserializesJsonCorrectly() {
        ConceptEntity entity = new ConceptEntity();

        String json = """
                [
                  {"code": "status", "valueCode": "active"},
                  {"code": "category", "valueString": "clinico"},
                  {"code": "isActive", "valueBoolean": true},
                  {"code": "priority", "valueInteger": 1},
                  {"code": "amount", "valueDecimal": "12.5"},
                  {"code": "date", "valueDateTime": "2024-01-01T10:00:00Z"},
                  {"code": "codingProp", "valueCoding": {"system": "http://example.com/system", "version": "1.0", "code": "exCode", "display": "Example Code", "userSelected": true}}
                ]
                """;

        entity.setPropertyJson(json);

        List<CodeSystem.ConceptPropertyComponent> properties = entity.getProperty();

        assertEquals(7, properties.size());

        // Assert Status (CodeType)
        CodeSystem.ConceptPropertyComponent prop0 = properties.get(0);
        assertEquals("status", prop0.getCode());
        assertTrue(prop0.getValue() instanceof CodeType);
        assertEquals("active", ((CodeType) prop0.getValue()).getValue());

        // Assert Category (StringType)
        CodeSystem.ConceptPropertyComponent prop1 = properties.get(1);
        assertEquals("category", prop1.getCode());
        assertTrue(prop1.getValue() instanceof StringType);
        assertEquals("clinico", ((StringType) prop1.getValue()).getValue());

        // Assert isActive (BooleanType)
        CodeSystem.ConceptPropertyComponent prop2 = properties.get(2);
        assertEquals("isActive", prop2.getCode());
        assertTrue(prop2.getValue() instanceof BooleanType);
        assertTrue(((BooleanType) prop2.getValue()).getValue());

        // Assert Priority (IntegerType)
        CodeSystem.ConceptPropertyComponent prop3 = properties.get(3);
        assertEquals("priority", prop3.getCode());
        assertTrue(prop3.getValue() instanceof IntegerType);
        assertEquals(1, ((IntegerType) prop3.getValue()).getValue());

        // Assert Amount (DecimalType)
        CodeSystem.ConceptPropertyComponent prop4 = properties.get(4);
        assertEquals("amount", prop4.getCode());
        assertTrue(prop4.getValue() instanceof DecimalType);
        assertEquals("12.5", ((DecimalType) prop4.getValue()).getValueAsString());

        // Assert Date (DateTimeType)
        CodeSystem.ConceptPropertyComponent prop5 = properties.get(5);
        assertEquals("date", prop5.getCode());
        assertTrue(prop5.getValue() instanceof DateTimeType);
        assertEquals("2024-01-01T10:00:00Z", ((DateTimeType) prop5.getValue()).getValueAsString());

        // Assert CodingProp (Coding)
        CodeSystem.ConceptPropertyComponent prop6 = properties.get(6);
        assertEquals("codingProp", prop6.getCode());
        assertTrue(prop6.getValue() instanceof Coding);
        Coding coding = (Coding) prop6.getValue();
        assertEquals("http://example.com/system", coding.getSystem());
        assertEquals("1.0", coding.getVersion());
        assertEquals("exCode", coding.getCode());
        assertEquals("Example Code", coding.getDisplay());
        assertTrue(coding.getUserSelected());
    }

    @Test
    void testSetPropertySerializesToJsonCorrectly() {
        ConceptEntity entity = new ConceptEntity();

        List<CodeSystem.ConceptPropertyComponent> properties = new ArrayList<>();

        // Add CodeType Component
        CodeSystem.ConceptPropertyComponent prop0 = new CodeSystem.ConceptPropertyComponent();
        prop0.setCode("status");
        prop0.setValue(new CodeType("active"));
        properties.add(prop0);

        // Add StringType Component
        CodeSystem.ConceptPropertyComponent prop1 = new CodeSystem.ConceptPropertyComponent();
        prop1.setCode("category");
        prop1.setValue(new StringType("clinico"));
        properties.add(prop1);

        // Add Coding Component
        CodeSystem.ConceptPropertyComponent prop2 = new CodeSystem.ConceptPropertyComponent();
        prop2.setCode("codingProp");
        Coding coding = new Coding();
        coding.setSystem("http://example.com/system");
        coding.setVersion("1.0");
        coding.setCode("exCode");
        coding.setDisplay("Example Code");
        coding.setUserSelected(true);
        prop2.setValue(coding);
        properties.add(prop2);

        entity.setProperty(properties);

        String jsonResult = entity.getPropertyJson();
        assertNotNull(jsonResult);
        assertTrue(jsonResult.contains("\"code\":\"status\""));
        assertTrue(jsonResult.contains("\"valueCode\":\"active\""));
        assertTrue(jsonResult.contains("\"code\":\"category\""));
        assertTrue(jsonResult.contains("\"valueString\":\"clinico\""));
        assertTrue(jsonResult.contains("\"code\":\"codingProp\""));
        assertTrue(jsonResult.contains("\"valueCoding\":{"));
        assertTrue(jsonResult.contains("\"system\":\"http://example.com/system\""));
        assertTrue(jsonResult.contains("\"version\":\"1.0\""));
        assertTrue(jsonResult.contains("\"code\":\"exCode\""));
        assertTrue(jsonResult.contains("\"display\":\"Example Code\""));
        assertTrue(jsonResult.contains("\"userSelected\":true"));
    }

    @Test
    void testGetPropertyWithNullOrEmptyJson() {
        ConceptEntity entity = new ConceptEntity();
        entity.setPropertyJson(null);
        assertTrue(entity.getProperty().isEmpty());

        entity.setPropertyJson("   ");
        assertTrue(entity.getProperty().isEmpty());
    }

    @Test
    void testSetPropertyWithNullOrEmptyList() {
        ConceptEntity entity = new ConceptEntity();
        entity.setProperty(null);
        assertNull(entity.getPropertyJson());

        entity.setProperty(new ArrayList<>());
        assertNull(entity.getPropertyJson());
    }
}
