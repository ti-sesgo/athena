package br.gov.go.saude.athena.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Representa um package FHIR carregado no servidor.
 */
@Entity
@Table(
        name = "packages",
        schema = "terminology",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"package_id", "version"})},

        indexes = {
                @Index(name = "idx_package_id_version",
                        columnList = "package_id, version, active")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor
public class PackageEntity {

    public PackageEntity() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String packageId;

    @Column(nullable = false)
    private String version;

    @Column
    private String registryUrl;

    @Column(nullable = false)
    private LocalDateTime loadedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
