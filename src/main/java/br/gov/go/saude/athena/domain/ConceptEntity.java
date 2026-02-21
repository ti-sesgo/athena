package br.gov.go.saude.athena.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Representa um conceito (código) de um CodeSystem.
 * Otimizado para operação $lookup com índice composto em (system, code).
 */
@Entity
@Table(
        name = "concepts",
        schema = "terminology",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_concept_code_url_version",
                        columnNames = {"code", "code_system_url", "code_system_version"})
        },
        indexes = {
                @Index(name = "idx_concept_lookup_version",
                        columnList = "code_system_url, code, active, code_system_version"),
                @Index(name = "idx_concept_lookup_is_latesst",
                        columnList = "code_system_url, code, active, code_system_is_latest")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor
public class ConceptEntity {

    public ConceptEntity() {}

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false)
        private String code;

        @Column(length = 1000)
        private String display;

        @Column(columnDefinition = "TEXT")
        private String definition;

        @Column(nullable = false)
        private String codeSystemUrl;

        @Column
        private String codeSystemName;

        @Column
        private String codeSystemVersion;

        @Column(nullable = false)
        @Builder.Default
        private Boolean codeSystemIsLatest = false;

        @Column(nullable = false)
        @Builder.Default
        private Boolean active = true;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "code_system_id", nullable = false)
        private CodeSystemEntity codeSystem;
        // TODO: colocar designations e properties dos conceitos
}
