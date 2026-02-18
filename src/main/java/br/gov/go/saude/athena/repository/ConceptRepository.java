package br.gov.go.saude.athena.repository;

import br.gov.go.saude.athena.domain.ConceptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório responsável pelas operações de dados da entidade ConceptEntity.
 *
 * <p>
 * Otimizado para a operação $lookup com índices compostos para garantir alta
 * performance.
 */
@Repository
public interface ConceptRepository extends JpaRepository<ConceptEntity, Long> {

        /**
         * Busca lista de conceitos filtrando por sistema e código.
         * Utiliza o índice idx_concept_lookup.
         */
        @Query("SELECT c FROM ConceptEntity c WHERE c.system = :system AND c.code = :code AND c.active = true")
        List<ConceptEntity> findBySystemAndCode(@Param("system") String system, @Param("code") String code);

        /**
         * Busca um conceito específico filtrando por sistema, código e versão.
         * Utiliza o índice idx_concept_lookup_version.
         */
        @Query("SELECT c FROM ConceptEntity c WHERE c.system = :system AND c.code = :code AND c.version = :version AND c.active = true")
        Optional<ConceptEntity> findBySystemAndCodeAndVersion(
                        @Param("system") String system,
                        @Param("code") String code,
                        @Param("version") String version);

        /**
         * Busca a versão mais recente de um conceito (sem filtrar por versão
         * específica).
         * Ordena pelo flag isLatest do CodeSystem e versão do conceito.
         */
        @Query("SELECT c FROM ConceptEntity c " +
                        "WHERE c.system = :system AND c.code = :code AND c.active = true " +
                        "ORDER BY c.codeSystem.isLatest DESC, c.version DESC")
        Optional<ConceptEntity> findLatestBySystemAndCode(@Param("system") String system, @Param("code") String code);

        /**
         * Projeção otimizada para recuperar dados de um conceito
         * Permite execução via Index Only Scan no banco de dados.
         */
        Optional<ConceptDisplayProjection> findDisplayBySystemAndCodeAndActiveTrue(String system, String code);

        /**
         * Projeção otimizada para recuperar dados de um conceito com versão específica.
         * Utiliza índice específico para garantir performance na busca versionada.
         */
        Optional<ConceptDisplayProjection> findDisplayBySystemAndCodeAndVersionAndActiveTrue(
                        String system, String code, String version);
}
