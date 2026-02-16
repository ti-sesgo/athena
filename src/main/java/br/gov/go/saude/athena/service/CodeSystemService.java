package br.gov.go.saude.athena.service;

import br.gov.go.saude.athena.domain.CodeSystemEntity;
import br.gov.go.saude.athena.repository.CodeSystemRepository;
import br.gov.go.saude.athena.repository.ConceptDisplayProjection;
import br.gov.go.saude.athena.repository.ConceptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeSystemService {

    private final CodeSystemRepository codeSystemRepository;
    private final ConceptRepository conceptRepository;

    /**
     * Busca um CodeSystem pelo ID lógico ou ID interno.
     */
    public Optional<CodeSystemEntity> findById(String id) {
        return codeSystemRepository.findByResourceIdAndIsLatestTrue(id)
                .or(() -> codeSystemRepository.findById(tryParseId(id)));
    }

    /**
     * Busca um CodeSystem pela URL canônica.
     */
    public Optional<CodeSystemEntity> findByUrl(String url) {
        return codeSystemRepository.findByUrlAndIsLatestTrue(url);
    }

    /**
     * Busca conceito por sistema e código (versão mais recente/aleatória ativa).
     */
    public Optional<ConceptDisplayProjection> findConcept(String system, String code) {
        return conceptRepository.findDisplayBySystemAndCodeAndActiveTrue(system, code);
    }

    /**
     * Busca conceito por sistema, código e versão.
     */
    public Optional<ConceptDisplayProjection> findConcept(String system, String code, String version) {
        return conceptRepository.findDisplayBySystemAndCodeAndVersionAndActiveTrue(system, code, version);
    }

    private Long tryParseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
