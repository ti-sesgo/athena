package br.gov.go.saude.athena.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Representa um conceito (código) de um CodeSystem.
 * Otimizado para operação $lookup com índice composto em (system, code).
 */
@Entity
@Table(name = "concepts", schema = "terminology", indexes = {
        @Index(name = "idx_concept_lookup", columnList = "system, code, active, display"),
        @Index(name = "idx_concept_lookup_version", columnList = "system, code, version"),
        @Index(name = "idx_concept_code_system", columnList = "code_system_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConceptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String system;

    @Column
    private String version;

    @Column(nullable = false)
    private String code;

    @Column(length = 1000)
    private String display;

    @Column(columnDefinition = "TEXT")
    private String definition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_system_id", nullable = false)
    private CodeSystemEntity codeSystem;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
