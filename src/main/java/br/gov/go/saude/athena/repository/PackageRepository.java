package br.gov.go.saude.athena.repository;

import br.gov.go.saude.athena.domain.PackageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PackageRepository extends JpaRepository<PackageEntity, Long> {

    Optional<PackageEntity> findByPackageIdAndVersionAndActiveTrue(String packageId, String version);
}
