package br.gov.go.saude.athena.controller;

import br.gov.go.saude.athena.exception.ConceptNotFoundException;
import br.gov.go.saude.athena.service.CodeSystemService;

import org.hl7.fhir.instance.model.api.IBaseResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import br.gov.go.saude.athena.dto.ValidateCodeRequest;
import br.gov.go.saude.athena.dto.ValidateCodeResult;

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
    @GetMapping(value = "/{id}")
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
    @GetMapping
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
    @PostMapping(value = "/$lookup")
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
    @GetMapping(value = "/$lookup")
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
            String code = codeParam.getValue().primitiveValue();
            String system = systemParam.getValue().primitiveValue();
            String version = versionParam != null ? versionParam.getValue().primitiveValue() : null;
            return Optional.of(new LookupCriteria(system, code, version));
        }

        return Optional.empty();
    }

    private ResponseEntity<IBaseResource> processLookup(LookupCriteria criteria) {
        log.debug("Processing lookup for criteria: {}", criteria);
        Parameters parameters = codeSystemService.lookup(criteria.system(), criteria.code(), criteria.version());
        return ResponseEntity.ok(parameters);
    }

    @ExceptionHandler(ConceptNotFoundException.class)
    public ResponseEntity<IBaseResource> handleConceptNotFound(
            ConceptNotFoundException ex) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(OperationOutcome.IssueType.NOTFOUND)
                .setDiagnostics(ex.getMessage())
                .setDetails(new CodeableConcept().setText("Concept not found."));

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(outcome);
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

    private record LookupCriteria(String system, String code, String version) {
    }

    // --- $validate-code (FHIR R4) ---

    /**
     * Operação $validate-code (FHIR R4).
     * <p>
     * Valida se um código pertence ao CodeSystem. Retorna result (true/false),
     * message opcional e display recomendado.
     * </p>
     * GET /CodeSystem/$validate-code?url={url}&code={code}&version={version}&display={display}
     */
    @GetMapping(value = "/$validate-code")
    public ResponseEntity<IBaseResource> validateCode(
            @RequestParam(required = false) String url,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String display) {

        log.debug("Validate-code GET: url={}, code={}, version={}, display={}", url, code, version, display);

        if (!StringUtils.hasText(url) || !StringUtils.hasText(code)) {
            String msg = !StringUtils.hasText(url) && !StringUtils.hasText(code)
                    ? "No url and code parameters provided in request"
                    : !StringUtils.hasText(url)
                            ? "No url parameter provided in request"
                            : "No code parameter provided in request";
            return buildValidateCodeBadRequestError(msg);
        }
        return executeValidateCode(url, code, version, display);
    }

    /**
     * Operação $validate-code no nível da instância.
     * GET /CodeSystem/{id}/$validate-code?code={code}&version={version}&display={display}
     */
    @GetMapping(value = "/{id}/$validate-code")
    public ResponseEntity<IBaseResource> validateCodeById(
            @PathVariable String id,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String display) {

        log.debug("Validate-code by id: id={}, code={}, version={}, display={}", id, code, version, display);

        if (!StringUtils.hasText(code)) {
            return buildValidateCodeBadRequestError("No code parameter provided in request");
        }

        String systemUrl = codeSystemService.findResourceById(id)
                .map(CodeSystem::getUrl)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CodeSystem not found: " + id));

        return executeValidateCode(systemUrl, code, version, display);
    }

    /**
     * Operação $validate-code via POST.
     * Spec: um (e apenas um) de code+url, coding ou codeableConcept. url ou codeSystem quando não no nível da instância.
     */
    @PostMapping(value = "/$validate-code")
    public ResponseEntity<IBaseResource> validateCode(@RequestBody Parameters parameters) {
        if (parameters == null) {
            return buildValidateCodeBadRequestError("No parameters provided in request");
        }

        if (ValidateCodeRequest.hasCodeSystemParam(parameters)) {
            return buildValidateCodeBadRequestError("Server does not support codeSystem parameter");
        }

        var requests = ValidateCodeRequest.fromParameters(parameters);
        if (requests.isEmpty()) {
            return buildValidateCodeInvalidParamsError();
        }
        return processValidateCodeRequests(requests);
    }

    /**
     * Operação $validate-code via POST no nível da instância.
     * POST /CodeSystem/{id}/$validate-code
     * Spec: url é opcional no nível da instância (inferido do id).
     */
    @PostMapping(value = "/{id}/$validate-code")
    public ResponseEntity<IBaseResource> validateCodeByIdPost(
            @PathVariable String id,
            @RequestBody Parameters parameters) {

        if (parameters == null) {
            return buildValidateCodeBadRequestError("No parameters provided in request");
        }

        if (ValidateCodeRequest.hasCodeSystemParam(parameters)) {
            return buildValidateCodeBadRequestError("Server does not support codeSystem parameter");
        }

        String systemUrl = codeSystemService.findResourceById(id)
                .map(CodeSystem::getUrl)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CodeSystem not found: " + id));

        var requests = ValidateCodeRequest.fromParameters(parameters, systemUrl);
        if (requests.isEmpty()) {
            return buildValidateCodeInvalidParamsError();
        }
        return processValidateCodeRequests(requests);
    }

    private ResponseEntity<IBaseResource> executeValidateCode(String system, String code, String version, String display) {
        ValidateCodeResult result = codeSystemService.validateCode(system, code, version, display);
        return ResponseEntity.ok(result.toParameters());
    }

    private ResponseEntity<IBaseResource> processValidateCodeRequests(List<ValidateCodeRequest> requests) {
        ValidateCodeResult lastResult = null;
        for (var req : requests) {
            lastResult = codeSystemService.validateCode(req.system(), req.code(), req.version(), req.display());
            if (lastResult.result()) {
                return ResponseEntity.ok(lastResult.toParameters());
            }
        }
        return ResponseEntity.ok(lastResult.toParameters());
    }

    private ResponseEntity<IBaseResource> buildValidateCodeInvalidParamsError() {
        return buildValidateCodeBadRequestError(
                "Invalid parameters for $validate-code operation",
                "Provide one of code+url, coding, or codeableConcept");
    }

    private ResponseEntity<IBaseResource> buildValidateCodeBadRequestError(String message) {
        return buildValidateCodeBadRequestError(message, null);
    }

    /**
     * Spec: 400 quando o servidor não consegue determinar validade.
     * Código inválido retorna 200 + result=false, não 400.
     */
    private ResponseEntity<IBaseResource> buildValidateCodeBadRequestError(String message, String details) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(OperationOutcome.IssueType.REQUIRED)
                .setDiagnostics(message)
                .setDetails(StringUtils.hasText(details) ? new CodeableConcept().setText(details) : null);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(outcome);
    }
}
