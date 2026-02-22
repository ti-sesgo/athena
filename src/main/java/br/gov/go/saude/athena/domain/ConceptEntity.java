package br.gov.go.saude.athena.domain;

import jakarta.persistence.*;
import lombok.*;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa um conceito (código) de um CodeSystem.
 */
@Entity
@Table(
        name = "concepts",
        schema = "terminology",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_concept_code_url_version",
                        columnNames = {"code", "code_system_url", "code_system_version"})
        },
        indexes = {
                @Index(name = "idx_concept_lookup_version",
                        columnList = "code_system_url, code, active, code_system_version"),
                @Index(name = "idx_concept_lookup_is_latest",
                        columnList = "code_system_url, code, active, code_system_is_latest")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor
public class ConceptEntity {

    public ConceptEntity() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(length = 1000)
    private String display;

    @Column(columnDefinition = "TEXT")
    private String definition;

    @Column(name = "property", columnDefinition = "TEXT")
    private String propertyJson;

    @Column(nullable = false)
    private String codeSystemUrl;

    @Column
    private String codeSystemName;

    @Column
    private String codeSystemVersion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean codeSystemIsLatest = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_system_id", nullable = false)
    private CodeSystemEntity codeSystem;
    // TODO: colocar designations e properties dos conceitos

    @Transient
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public List<CodeSystem.ConceptPropertyComponent> getProperty() {
        if (this.propertyJson == null || this.propertyJson.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<CodeSystem.ConceptPropertyComponent> result = new ArrayList<>();
            JsonNode arrayNode = OBJECT_MAPPER.readTree(this.propertyJson);
            if (arrayNode.isArray()) {
                for (JsonNode node : arrayNode) {
                    CodeSystem.ConceptPropertyComponent prop = new CodeSystem.ConceptPropertyComponent();
                    if (node.hasNonNull("code")) {
                        prop.setCode(node.get("code").asText());
                    }
                    if (node.hasNonNull("valueCode")) {
                        prop.setValue(new CodeType(
                                node.get("valueCode").asText()));
                    } else if (node.hasNonNull("valueCoding")) {
                        JsonNode codingNode = node.get("valueCoding");
                        Coding coding = new Coding();
                        if (codingNode.hasNonNull("system"))
                            coding.setSystem(codingNode.get("system").asText());
                        if (codingNode.hasNonNull("version"))
                            coding.setVersion(codingNode.get("version").asText());
                        if (codingNode.hasNonNull("code"))
                            coding.setCode(codingNode.get("code").asText());
                        if (codingNode.hasNonNull("display"))
                            coding.setDisplay(codingNode.get("display").asText());
                        if (codingNode.hasNonNull("userSelected"))
                            coding.setUserSelected(
                                    codingNode.get("userSelected").asBoolean());
                        prop.setValue(coding);
                    } else if (node.hasNonNull("valueString")) {
                        prop.setValue(new StringType(
                                node.get("valueString").asText()));
                    } else if (node.hasNonNull("valueBoolean")) {
                        prop.setValue(new BooleanType(
                                node.get("valueBoolean").asBoolean()));
                    } else if (node.hasNonNull("valueInteger")) {
                        prop.setValue(new IntegerType(
                                node.get("valueInteger").asInt()));
                    } else if (node.hasNonNull("valueDateTime")) {
                        prop.setValue(new DateTimeType(
                                node.get("valueDateTime").asText()));
                    } else if (node.hasNonNull("valueDecimal")) {
                        prop.setValue(new DecimalType(
                                node.get("valueDecimal").asText()));
                    }
                    result.add(prop);
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing property JSON", e);
        }
    }

    public void setProperty(List<CodeSystem.ConceptPropertyComponent> properties) {
        if (properties == null || properties.isEmpty()) {
            this.propertyJson = null;
            return;
        }
        try {
            ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
            for (CodeSystem.ConceptPropertyComponent prop : properties) {
                ObjectNode node = OBJECT_MAPPER.createObjectNode();
                if (prop.hasCode()) {
                    node.put("code", prop.getCode());
                }
                if (prop.hasValue()) {
                    Type value = prop.getValue();
                    if (value instanceof CodeType) {
                        node.put("valueCode", ((CodeType) value)
                                .asStringValue());
                    } else if (value instanceof Coding) {
                        Coding coding = (Coding) value;
                        ObjectNode codingNode = OBJECT_MAPPER.createObjectNode();
                        if (coding.hasSystem())
                            codingNode.put("system", coding.getSystem());
                        if (coding.hasVersion())
                            codingNode.put("version", coding.getVersion());
                        if (coding.hasCode())
                            codingNode.put("code", coding.getCode());
                        if (coding.hasDisplay())
                            codingNode.put("display", coding.getDisplay());
                        if (coding.hasUserSelected())
                            codingNode.put("userSelected", coding.getUserSelected());
                        node.set("valueCoding", codingNode);
                    } else if (value instanceof StringType) {
                        node.put("valueString", ((StringType) value)
                                .asStringValue());
                    } else if (value instanceof BooleanType) {
                        node.put("valueBoolean",
                                ((BooleanType) value).getValue());
                    } else if (value instanceof IntegerType) {
                        node.put("valueInteger",
                                ((IntegerType) value).getValue());
                    } else if (value instanceof DateTimeType) {
                        node.put("valueDateTime", ((DateTimeType) value)
                                .asStringValue());
                    } else if (value instanceof DecimalType) {
                        node.put("valueDecimal", ((DecimalType) value)
                                .getValueAsString());
                    }
                }
                arrayNode.add(node);
            }
            this.propertyJson = OBJECT_MAPPER.writeValueAsString(arrayNode);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing property JSON", e);
        }
    }
}
