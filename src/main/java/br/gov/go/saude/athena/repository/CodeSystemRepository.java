package br.gov.go.saude.athena.repository;

import br.gov.go.saude.athena.domain.CodeSystemEntity;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeSystemRepository extends JpaRepository<CodeSystemEntity, Long> {

    Optional<CodeSystemEntity> findByUrlAndVersion(String url, String version);

    /**
     * Busca a versão mais recente usando índice otimizado (url, isLatest).
     */
    Optional<CodeSystemEntity> findByUrlAndIsLatestTrue(String url);

    /**
     * Busca por resource ID mas garante que é a versão mais recente (se houver
     * múltiplas com mesmo ID lógico).
     */
    Optional<CodeSystemEntity> findByResourceIdAndIsLatestTrue(String resourceId);

    @Query("SELECT cs FROM CodeSystemEntity cs WHERE cs.url = :url AND cs.isLatest = true")
    Optional<CodeSystemEntity> findLatestByUrl(@Param("url") String url);

    /**
     * Busca CodeSystems por status.
     */
    List<CodeSystemEntity> findByStatus(PublicationStatus status);

    /**
     * Busca CodeSystems ativos (status = ACTIVE).
     */
    @Query("SELECT cs FROM CodeSystemEntity cs WHERE cs.status = 'ACTIVE'")
    List<CodeSystemEntity> findAllActive();

    /**
     * Busca por resource ID FHIR.
     */
    Optional<CodeSystemEntity> findByResourceId(String resourceId);
}
