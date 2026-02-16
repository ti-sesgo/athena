package br.gov.go.saude.athena.controller;

import br.gov.go.saude.athena.domain.CodeSystemEntity;
import br.gov.go.saude.athena.repository.ConceptDisplayProjection;
import br.gov.go.saude.athena.service.CodeSystemService;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.hl7.fhir.r4.model.OperationOutcome;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/CodeSystem")
@RequiredArgsConstructor
public class CodeSystemController {

    private final CodeSystemService codeSystemService;
    private final FhirContext fhirContext;

    /**
     * Recupera um CodeSystem pelo ID lógico.
     * <br>
     * URL: /CodeSystem/{id}
     */
    @GetMapping(value = "/{id}", produces = "application/fhir+json")
    public String getCodeSystemById(@PathVariable String id) {
        log.debug("Get CodeSystem by ID: {}", id);

        CodeSystemEntity entity = codeSystemService.findById(id)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CodeSystem não encontrado: " + id));

        return new String(entity.getContent());
    }

    /**
     * Busca CodeSystem por URL canônica.
     * <br>
     * URL: /CodeSystem?url={url}
     */
    @GetMapping(produces = "application/fhir+json")
    public String getCodeSystemByUrl(@RequestParam(required = false) String url) {
        if (url == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parâmetro 'url' é obrigatório para busca.");
        }
        log.debug("Search CodeSystem by URL: {}", url);

        CodeSystemEntity entity = codeSystemService.findByUrl(url)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "CodeSystem com URL " + url + " não encontrado."));

        // Se for um search, tecnicamente deveria retornar um Bundle, mas
        // para simplificar e atender "leitura ... pela url", retornamos o recurso
        // direto se for único.
        // Se quisermos ser estritos FHIR search, retornaríamos Bundle.
        // Dado o requisito "leitura ... pela url", vou retornar um Bundle com 1
        // entrada.

        // Parse do conteúdo salvo para CodeSystem HAPI
        IParser parser = fhirContext.newJsonParser();
        CodeSystem codeSystem = parser.parseResource(CodeSystem.class, new String(entity.getContent()));

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.addEntry().setResource(codeSystem);
        bundle.setTotal(1);

        return parser.encodeResourceToString(bundle);
    }

    /**
     * Operação $lookup.
     * <br>
     * GET /CodeSystem/$lookup?system={system}&code={code}
     */
    @GetMapping(value = "/$lookup", produces = "application/fhir+json")
    public ResponseEntity<String> lookup(@RequestParam(required = false) String system,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String version) {

        log.debug("Lookup GET operation: system={}, code={}, version={}", system, code, version);

        if (version != null && !version.isEmpty()) {
            return performLookup(system, code, version);
        }
        return performLookup(system, code);
    }

    private ResponseEntity<String> performLookup(String system, String code) {
        if (system == null || code == null) {
            return buildLookupBadRequestError(system, code);
        }
        // Uso da projeção otimizada: busca APENAS o display name usando Index Only Scan
        return buildLookupResponse(codeSystemService.findConcept(system, code), system, code);
    }

    private ResponseEntity<String> performLookup(String system, String code, String version) {
        if (system == null || code == null) {
            return buildLookupBadRequestError(system, code);
        }
        return buildLookupResponse(
                codeSystemService.findConcept(system, code, version), system,
                code);
    }

    private ResponseEntity<String> buildLookupBadRequestError(String system, String code) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(OperationOutcome.IssueType.INVALID)
                .setDiagnostics(
                        "System and code are required for lookup operation | system=" + system + ", code=" + code);

        IParser parser = fhirContext.newJsonParser();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(parser.encodeResourceToString(outcome));
    }

    private ResponseEntity<String> buildLookupResponse(Optional<ConceptDisplayProjection> projection, String system,
            String code) {
        if (projection.isEmpty()) {
            OperationOutcome outcome = new OperationOutcome();
            outcome.addIssue()
                    .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                    .setCode(OperationOutcome.IssueType.NOTFOUND)
                    .setDiagnostics("Unable to find code[" + code + "] in system[" + system + "]");

            IParser parser = fhirContext.newJsonParser();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(parser.encodeResourceToString(outcome));
        }

        ConceptDisplayProjection p = projection.get();

        // Monta o retorno Parameters conforme spec FHIR
        Parameters parameters = new Parameters();

        // 1. Name (Obrigatório)
        if (p.getCodeSystemName() != null) {
            parameters.addParameter("name", new StringType(p.getCodeSystemName()));
        }

        // 2. Version (Obrigatório se o CodeSystem tiver versão)
        if (p.getCodeSystemVersion() != null) {
            parameters.addParameter("version", new StringType(p.getCodeSystemVersion()));
        }

        // 3. Display (Obrigatório)
        parameters.addParameter("display", new StringType(p.getDisplay()));

        // 4. Definition (Opcional/Recomendado)
        if (p.getDefinition() != null) {
            parameters.addParameter("definition", new StringType(p.getDefinition()));
        }

        // Padrão do projeto: retornar JSON simples e legível
        IParser parser = fhirContext.newJsonParser();
        return ResponseEntity.ok(parser.encodeResourceToString(parameters));
    }
}
