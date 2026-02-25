package br.gov.go.saude.athena.controller;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.Enumerations.SearchParamType;
import org.hl7.fhir.r4.model.TerminologyCapabilities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.gov.go.saude.athena.service.CodeSystemService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Controller para o endpoint de capabilities FHIR.
 * <p>
 * Conforme <a href="https://hl7.org/fhir/R4/http.html#capabilities">FHIR HTTP - capabilities</a>:
 * <ul>
 * <li>{@code GET /metadata} ou {@code mode=full} → CapabilityStatement</li>
 * <li>{@code GET /metadata?mode=terminology} → TerminologyCapabilities</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class CapabilityStatementController {

    private static final String TERMINOLOGY_SERVER_PROFILE = "http://hl7.org/fhir/terminology-server";
    private static final String OP_LOOKUP = "http://hl7.org/fhir/OperationDefinition/CodeSystem-lookup";
    private static final String OP_VALIDATE_CODE = "http://hl7.org/fhir/OperationDefinition/CodeSystem-validate-code";

    @Value("${athena.server-base-url:https://terminologias.saude.go.gov.br/fhir}")
    private String serverBaseUrl;

    private final CodeSystemService codeSystemService;

    private CapabilityStatement capabilityStatement;

    @PostConstruct
    public void init() {
        capabilityStatement = new CapabilityStatement();
        capabilityStatement.setUrl(serverBaseUrl + "/metadata");
        capabilityStatement.setName("Athena Terminology Server");
        capabilityStatement.setTitle("Athena - Servidor de Terminologias SES-GO");
        capabilityStatement.setStatus(PublicationStatus.DRAFT);
        capabilityStatement.setDate(new Date());
        capabilityStatement.setPublisher("SES-GO");
        capabilityStatement.setDescription(
                "Servidor de terminologias FHIR R4 da SES-GO.");
        capabilityStatement.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);
        capabilityStatement.addInstantiates(TERMINOLOGY_SERVER_PROFILE);
        capabilityStatement.setImplementation(new CapabilityStatement.CapabilityStatementImplementationComponent()
                .setDescription("Athena Terminology Server - SES-GO")
                .setUrl(serverBaseUrl));
        capabilityStatement.setSoftware(new CapabilityStatement.CapabilityStatementSoftwareComponent()
                .setName("Athena Terminology Server")
                .setVersion("0.0.1"));
        capabilityStatement.setFhirVersion(FHIRVersion._4_0_1);
        capabilityStatement.setFormat(List.of(
                new CodeType("application/fhir+json"),
                new CodeType("application/fhir+xml")));

        CapabilityStatement.CapabilityStatementRestComponent rest = capabilityStatement.addRest();
        rest.setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);
        rest.setDocumentation("RESTful Terminology Server");

        CapabilityStatement.CapabilityStatementRestResourceComponent codeSystem = rest.addResource();
        codeSystem.setType("CodeSystem");
        codeSystem.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.READ);
        codeSystem.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE);
        codeSystem.addSearchParam()
                .setName("url")
                .setType(SearchParamType.URI)
                .setDocumentation("URL canônica do CodeSystem");
        codeSystem.addOperation()
                .setName("lookup")
                .setDefinition(OP_LOOKUP);
        codeSystem.addOperation()
                .setName("validate-code")
                .setDefinition(OP_VALIDATE_CODE);
    }

    /**
     * Retorna CapabilityStatement ou TerminologyCapabilities conforme o parâmetro mode.
     *
     * @param mode full (default) | normative | terminology
     */
    @GetMapping(value = "/metadata")
    public IBaseResource getMetadata(@RequestParam(required = false) String mode) {
        if ("terminology".equals(mode)) {
            return buildTerminologyCapabilities();
        }
        return capabilityStatement;
    }

    private TerminologyCapabilities buildTerminologyCapabilities() {
        TerminologyCapabilities tc = new TerminologyCapabilities();
        tc.setUrl(serverBaseUrl + "/metadata");
        tc.setName("Athena Terminology Capabilities");
        tc.setTitle("Athena - Capacidades de Terminologia SES-GO");
        tc.setStatus(PublicationStatus.ACTIVE);
        tc.setDate(new Date());
        tc.setPublisher("SES-GO");
        tc.setDescription(
                "Capacidades do serviço de terminologia Athena. Suporta $lookup e $validate-code em CodeSystems.");
        tc.setKind(TerminologyCapabilities.CapabilityStatementKind.INSTANCE);
        tc.setImplementation(new TerminologyCapabilities.TerminologyCapabilitiesImplementationComponent()
                .setDescription("Athena Terminology Server - SES-GO")
                .setUrl(serverBaseUrl));
        tc.setSoftware(new TerminologyCapabilities.TerminologyCapabilitiesSoftwareComponent()
                .setName("Athena Terminology Server")
                .setVersion("0.0.1"));

        tc.setValidateCode(new TerminologyCapabilities.TerminologyCapabilitiesValidateCodeComponent()
                .setTranslations(false));

        List<CodeSystemService.CodeSystemWithVersions> codeSystems = codeSystemService.findCodeSystemsWithVersionsByActiveTrue();
        for (CodeSystemService.CodeSystemWithVersions cs : codeSystems) {
            var component = new TerminologyCapabilities.TerminologyCapabilitiesCodeSystemComponent().setUri(cs.uri());
            for (CodeSystemService.VersionInfo v : cs.versions()) {
                var versionComp = new TerminologyCapabilities.TerminologyCapabilitiesCodeSystemVersionComponent()
                        .setIsDefault(v.isDefault());
                if (v.code() != null && !v.code().isBlank()) {
                    versionComp.setCode(v.code());
                }
                component.addVersion(versionComp);
            }
            tc.addCodeSystem(component);
        }

        return tc;
    }
}
