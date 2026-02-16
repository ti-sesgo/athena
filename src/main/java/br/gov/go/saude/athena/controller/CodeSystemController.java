package br.gov.go.saude.athena.controller;

import br.gov.go.saude.athena.domain.CodeSystemEntity;
import br.gov.go.saude.athena.repository.CodeSystemRepository;
import br.gov.go.saude.athena.repository.ConceptDisplayProjection;
import br.gov.go.saude.athena.repository.ConceptRepository;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.http.HttpStatus;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/CodeSystem")
@RequiredArgsConstructor
public class CodeSystemController {

    private final CodeSystemRepository codeSystemRepository;
    private final ConceptRepository conceptRepository;
    private final FhirContext fhirContext;

    /**
     * Recupera um CodeSystem pelo ID lógico.
     * <br>
     * URL: /CodeSystem/{id}
     */
    @GetMapping(value = "/{id}", produces = "application/fhir+json")
    public String getCodeSystemById(@PathVariable String id) {
        log.debug("Get CodeSystem by ID: {}", id);

        CodeSystemEntity entity = codeSystemRepository.findByResourceIdAndIsLatestTrue(id)
                .or(() -> codeSystemRepository.findById(tryParseId(id)) // Fallback se passar ID numérico interno
                        .map(e -> e))
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

        CodeSystemEntity entity = codeSystemRepository.findByUrlAndIsLatestTrue(url)
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
    public String lookup(@RequestParam String system, @RequestParam String code) {
        log.debug("Lookup operation: system={}, code={}", system, code);
        return performLookup(system, code);
    }

    /**
     * Operação $lookup (bypassing body parsing for simplicity, assuming params in
     * query string or standard form).
     * <br>
     * POST /CodeSystem/$lookup
     */
    @PostMapping(value = "/$lookup", produces = "application/fhir+json")
    public String lookupPost(@RequestParam String system, @RequestParam String code) {
        log.debug("Lookup POST operation: system={}, code={}", system, code);
        return performLookup(system, code);
    }

    private String performLookup(String system, String code) {
        // Uso da projeção otimizada: busca APENAS o display name usando Index Only Scan
        Optional<ConceptDisplayProjection> projection = conceptRepository
                .findDisplayBySystemAndCodeAndActiveTrue(system, code);

        if (projection.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conceito não encontrado: " + system + "|" + code);
        }

        // Monta o retorno Parameters conforme spec FHIR
        Parameters parameters = new Parameters();
        parameters.addParameter("display", new StringType(projection.get().getDisplay()));

        // Padrão do projeto: retornar JSON simples e legível
        IParser parser = fhirContext.newJsonParser();
        return parser.encodeResourceToString(parameters);
    }

    private Long tryParseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return -1L; // ID inválido para o banco
        }
    }
}
