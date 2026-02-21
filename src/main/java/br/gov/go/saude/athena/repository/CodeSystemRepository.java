package br.gov.go.saude.athena.repository;

import br.gov.go.saude.athena.domain.CodeSystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CodeSystemRepository extends JpaRepository<CodeSystemEntity, Long> {

    Optional<CodeSystemEntity> findByUrlAndVersionAndActiveTrue(String url, String version);

    Optional<CodeSystemEntity> findByUrlAndIsLatestTrueAndActiveTrue(String url);

    Optional<CodeSystemEntity> findByResourceIdAndIsLatestTrueAndActiveTrue(String resourceId);
}
