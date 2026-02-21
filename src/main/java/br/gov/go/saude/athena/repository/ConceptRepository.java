package br.gov.go.saude.athena.repository;

import br.gov.go.saude.athena.domain.ConceptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositório responsável pelas operações de dados da entidade ConceptEntity.
 */
@Repository
public interface ConceptRepository extends JpaRepository<ConceptEntity, Long> {

    Optional<ConceptDisplayProjection>
    findDisplayByCodeSystemUrlAndCodeAndCodeSystemIsLatestTrueAndActiveTrue(
            String codeSystemUrl,
            String code
    );

    Optional<ConceptDisplayProjection>
    findDisplayByCodeSystemUrlAndCodeAndCodeSystemVersionAndActiveTrue(
            String codeSystemUrl,
            String code,
            String codeSystemVersion
    );
}
