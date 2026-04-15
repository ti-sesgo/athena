package br.gov.go.saude.athena.repository;

import br.gov.go.saude.athena.domain.ValueSetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ValueSetRepository extends JpaRepository<ValueSetEntity, Long> {

    Optional<ValueSetEntity> findByUrlAndVersionAndActiveTrue(String url, String version);

    Optional<ValueSetEntity> findByUrlAndIsLatestTrueAndActiveTrue(String url);

    Optional<ValueSetEntity> findByResourceIdAndIsLatestTrueAndActiveTrue(String resourceId);
}
