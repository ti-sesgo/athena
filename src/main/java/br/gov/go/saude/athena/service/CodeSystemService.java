package br.gov.go.saude.athena.service;

import br.gov.go.saude.athena.domain.CodeSystemEntity;
import br.gov.go.saude.athena.repository.CodeSystemRepository;
import br.gov.go.saude.athena.repository.ConceptProjection;
import br.gov.go.saude.athena.repository.ConceptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CodeSystem;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeSystemService {

    private final CodeSystemRepository codeSystemRepository;
    private final ConceptRepository conceptRepository;
    private final ca.uhn.fhir.context.FhirContext fhirContext;

    /**
     * Busca um CodeSystem pelo ID lógico ou ID interno e retorna como recurso HAPI.
     */
    public Optional<CodeSystem> findResourceById(String id) {
        return findById(id).map(this::parseEntity);
    }

    /**
     * Busca um CodeSystem pela URL canônica e retorna como recurso HAPI.
     */
    public Optional<CodeSystem> findResourceByUrl(String url) {
        return findByUrl(url).map(this::parseEntity);
    }

    private CodeSystem parseEntity(CodeSystemEntity entity) {
        return fhirContext.newJsonParser().parseResource(CodeSystem.class, new String(entity.getContent()));
    }

    /**
     * Busca um CodeSystem pelo ID lógico ou ID interno.
     */
    public Optional<CodeSystemEntity> findById(String id) {
        return codeSystemRepository.findByResourceIdAndIsLatestTrueAndActiveTrue(id)
                .or(() -> codeSystemRepository.findById(tryParseId(id)));
    }

    /**
     * Busca um CodeSystem pela URL canônica.
     */
    public Optional<CodeSystemEntity> findByUrl(String url) {
        return codeSystemRepository.findByUrlAndIsLatestTrueAndActiveTrue(url);
    }

    /**
     * Busca conceito por sistema e código (versão mais recente/aleatória ativa).
     */
    public Optional<ConceptProjection> findConcept(String system, String code) {
        return conceptRepository.findByCodeSystemUrlAndCodeAndCodeSystemIsLatestTrueAndActiveTrue(system, code);
    }

    /**
     * Busca conceito por sistema, código e versão.
     */
    public Optional<ConceptProjection> findConcept(String system, String code, String version) {
        return conceptRepository.findByCodeSystemUrlAndCodeAndCodeSystemVersionAndActiveTrue(system, code,
                version);
    }

    private Long tryParseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
