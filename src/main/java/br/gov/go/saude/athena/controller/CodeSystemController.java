package br.gov.go.saude.athena.controller;

import br.gov.go.saude.athena.repository.ConceptDisplayProjection;
import br.gov.go.saude.athena.service.CodeSystemService;

import org.hl7.fhir.instance.model.api.IBaseResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/CodeSystem")
@RequiredArgsConstructor
public class CodeSystemController {

    private final CodeSystemService codeSystemService;

    /**
     * Recupera um CodeSystem pelo ID lógico.
     * <br>
     * URL: /CodeSystem/{id}
     */
    @GetMapping(value = "/{id}", produces = { "application/fhir+json", "application/json", "application/fhir+xml",
            "application/xml" })
    public ResponseEntity<IBaseResource> getCodeSystemById(@PathVariable String id) {
        log.debug("Get CodeSystem by ID: {}", id);

        CodeSystem resource = codeSystemService.findResourceById(id)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CodeSystem not found: " + id));

        return ResponseEntity.ok(resource);
    }

    /**
     * Busca CodeSystem por URL canônica.
     * <br>
     * URL: /CodeSystem?url={url}
     */
    @GetMapping(produces = { "application/fhir+json", "application/json", "application/fhir+xml", "application/xml" })
    public ResponseEntity<IBaseResource> getCodeSystemByUrl(@RequestParam(required = false) String url) {
        if (url == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parameter 'url' is required for search.");
        }
        log.debug("Search CodeSystem by URL: {}", url);

        CodeSystem resource = codeSystemService.findResourceByUrl(url)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "CodeSystem with URL " + url + " not found."));

        // Wrap in Bundle for search result consistency (implied requirement for
        // search-like op)
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.addEntry().setResource(resource);
        bundle.setTotal(1);

        return ResponseEntity.ok(bundle);
    }

    /**
     * Operação $lookup (FHIR R4).
     * <p>
     * Retorna detalhes de um conceito (display, status e propriedades) a partir de
     * um Coding.
     * </p>
     *
     * <b>Exemplo de Payload (POST):</b>
     *
     * <pre>
     * {
     *   "resourceType": "Parameters",
     *   "parameter": [
     *     {
     *       "name": "coding",
     *       "valueCoding": {
     *         "system": "http://loinc.org",
     *         "code": "1963-8"
     *       }
     *     }
     *   ]
     * }
     * </pre>
     *
     * @param parameters Recurso {@code Parameters} contendo, pelo menos, ou o campo
     *                   {@code coding}
     *                   ou os campos code e system.
     * @return Detalhes do conceito em formato Parameters.
     */
    @PostMapping(value = "/$lookup", produces = { "application/fhir+json", "application/json", "application/fhir+xml",
            "application/xml" })
    public ResponseEntity<IBaseResource> lookup(@RequestBody Parameters parameters) {
        if (parameters == null) {
            return buildLookupBadRequestError();
        }

        return extractLookupCriteria(parameters)
                .map(this::processLookup)
                .orElseGet(this::buildLookupBadRequestError);
    }

    /**
     * Operação $lookup.
     * <br>
     * GET /CodeSystem/$lookup?system={system}&code={code}&version={version}
     */
    @GetMapping(value = "/$lookup", produces = { "application/fhir+json", "application/json", "application/fhir+xml",
            "application/xml" })
    public ResponseEntity<IBaseResource> lookup(@RequestParam(required = false) String system,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String version) {

        log.debug("Lookup GET operation: system={}, code={}, version={}", system, code, version);

        if (system == null || code == null) {
            return buildLookupBadRequestError();
        }

        return processLookup(new LookupCriteria(system, code, version));
    }

    private Optional<LookupCriteria> extractLookupCriteria(Parameters parameters) {
        Parameters.ParametersParameterComponent codingParam = parameters.getParameter("coding");

        if (codingParam != null && codingParam.getValue() instanceof Coding coding) {
            if (coding.hasSystem() && coding.hasCode()) {
                return Optional.of(new LookupCriteria(coding.getSystem(), coding.getCode(), coding.getVersion()));
            }
        }

        Parameters.ParametersParameterComponent codeParam = parameters.getParameter("code");
        Parameters.ParametersParameterComponent systemParam = parameters.getParameter("system");
        Parameters.ParametersParameterComponent versionParam = parameters.getParameter("version");

        if (codeParam != null && systemParam != null) {
            String code = codeParam.getValue().toString();
            String system = systemParam.getValue().toString();
            String version = versionParam != null ? versionParam.getValue().toString() : null;
            return Optional.of(new LookupCriteria(system, code, version));
        }

        return Optional.empty();
    }

    private ResponseEntity<IBaseResource> processLookup(LookupCriteria criteria) {
        log.debug("Processing lookup for criteria: {}", criteria);

        Optional<ConceptDisplayProjection> result;
        if (criteria.version() != null && !criteria.version().isEmpty()) {
            result = codeSystemService.findConcept(criteria.system(), criteria.code(), criteria.version());
        } else {
            result = codeSystemService.findConcept(criteria.system(), criteria.code());
        }

        return buildLookupResponse(result, criteria);
    }

    private ResponseEntity<IBaseResource> buildLookupBadRequestError() {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(OperationOutcome.IssueType.REQUIRED)
                .setDiagnostics(
                        "For lookup operation a client SHALL provide both a system and a code, either using the system+code parameters, or in the coding parameter.")
                .setDetails(new CodeableConcept().setText("Invalid or missing search parameters."));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(outcome);
    }

    private ResponseEntity<IBaseResource> buildLookupResponse(Optional<ConceptDisplayProjection> projection,
            LookupCriteria criteria) {
        if (projection.isEmpty()) {
            String diagnostic = "Unable to find code[" + criteria.code() + "] in system[" + criteria.system() + "]";
            if (criteria.version() != null && !criteria.version().isEmpty()) {
                diagnostic += " version[" + criteria.version() + "]";
            }

            OperationOutcome outcome = new OperationOutcome();
            outcome.addIssue()
                    .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                    .setCode(OperationOutcome.IssueType.NOTFOUND)
                    .setDiagnostics(diagnostic)
                    .setDetails(new CodeableConcept().setText("Concept not found."));

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(outcome);
        }

        ConceptDisplayProjection p = projection.get();

        // Monta o retorno Parameters conforme spec FHIR
        Parameters parameters = new Parameters();

        // 1. Name (Obrigatório)
        parameters.addParameter("name", new StringType(p.getCodeSystemName()));

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

        return ResponseEntity.ok(parameters);
    }

    private record LookupCriteria(String system, String code, String version) {
    }
}
