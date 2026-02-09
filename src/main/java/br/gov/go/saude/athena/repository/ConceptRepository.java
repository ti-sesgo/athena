package br.gov.go.saude.athena.repository;

import br.gov.go.saude.athena.domain.ConceptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório otimizado para operação $lookup.
 * Índice composto em (system, code) garante performance O(log n).
 */
@Repository
public interface ConceptRepository extends JpaRepository<ConceptEntity, Long> {

    /**
     * Lookup performático: busca conceito por system e code.
     * Usa índice idx_concept_lookup.
     */
    @Query("SELECT c FROM ConceptEntity c WHERE c.system = :system AND c.code = :code AND c.active = true")
    List<ConceptEntity> findBySystemAndCode(@Param("system") String system, @Param("code") String code);

    /**
     * Lookup com versão específica.
     * Usa índice idx_concept_lookup_version.
     */
    @Query("SELECT c FROM ConceptEntity c WHERE c.system = :system AND c.code = :code AND c.version = :version AND c.active = true")
    Optional<ConceptEntity> findBySystemAndCodeAndVersion(
            @Param("system") String system,
            @Param("code") String code,
            @Param("version") String version);

    /**
     * Lookup sem versão: retorna a versão mais recente.
     * Usa índice idx_concept_lookup.
     */
    @Query("SELECT c FROM ConceptEntity c " +
            "WHERE c.system = :system AND c.code = :code AND c.active = true " +
            "ORDER BY c.codeSystem.isLatest DESC, c.version DESC")
    Optional<ConceptEntity> findLatestBySystemAndCode(@Param("system") String system, @Param("code") String code);

    /**
     * Lookup ultra-rápido retornando apenas o display name.
     * Permite Index Only Scan no Postgres usando o índice (system, code, active)
     * INCLUDE (display).
     */
    Optional<ConceptDisplayProjection> findDisplayBySystemAndCodeAndActiveTrue(String system, String code);
}
