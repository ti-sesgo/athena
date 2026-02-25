package br.gov.go.saude.athena.repository;

import br.gov.go.saude.athena.domain.CodeSystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeSystemRepository extends JpaRepository<CodeSystemEntity, Long> {

    List<String> findDistinctUrlByActiveTrueOrderByUrl();

    List<UrlVersionLatestProjection> findByActiveTrueOrderByUrlAscVersionAsc();

    Optional<CodeSystemEntity> findByUrlAndVersionAndActiveTrue(String url, String version);

    /**
     * Projeção para url, version e isLatest.
     */
    interface UrlVersionLatestProjection {
        String getUrl();
        String getVersion();
        Boolean getIsLatest();
    }

    Optional<CodeSystemEntity> findByUrlAndIsLatestTrueAndActiveTrue(String url);

    Optional<CodeSystemEntity> findByResourceIdAndIsLatestTrueAndActiveTrue(String resourceId);
}
