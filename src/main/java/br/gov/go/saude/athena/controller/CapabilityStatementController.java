package br.gov.go.saude.athena.controller;

import ca.uhn.fhir.parser.IParser;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.Enumerations.FHIRVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;

import java.util.Date;

@RestController
@RequiredArgsConstructor
public class CapabilityStatementController {

        private final IParser jsonParser;

        private String cachedCapabilityStatement;

        @PostConstruct
        public void init() {
                CapabilityStatement capabilityStatement = new CapabilityStatement();
                capabilityStatement.setStatus(PublicationStatus.ACTIVE);
                capabilityStatement.setDate(new Date());
                capabilityStatement.setPublisher("SES-GO");
                capabilityStatement.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);
                capabilityStatement.setSoftware(new CapabilityStatement.CapabilityStatementSoftwareComponent()
                                .setName("Athena Terminology Server")
                                .setVersion("0.0.1"));
                capabilityStatement.setFhirVersion(FHIRVersion._4_0_1);
                capabilityStatement.setFormat(java.util.List.of(
                                new CodeType("application/fhir+json"),
                                new CodeType("json"),
                                new CodeType("application/xml"),
                                new CodeType("xml")));

                CapabilityStatement.CapabilityStatementRestComponent rest = capabilityStatement.addRest();
                rest.setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);

                // CodeSystem Support
                CapabilityStatement.CapabilityStatementRestResourceComponent codeSystem = rest.addResource();
                codeSystem.setType("CodeSystem");
                codeSystem.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.READ);
                codeSystem.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE);

                // Operations
                codeSystem.addOperation()
                                .setName("lookup")
                                .setDefinition("http://hl7.org/fhir/OperationDefinition/CodeSystem-lookup");

                this.cachedCapabilityStatement = jsonParser.encodeResourceToString(capabilityStatement);
        }

        @GetMapping(value = "/metadata", produces = "application/fhir+json")
        public String getMetadata() {
                return cachedCapabilityStatement;
        }
}
