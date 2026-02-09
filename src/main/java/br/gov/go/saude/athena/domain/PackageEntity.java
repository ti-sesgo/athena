package br.gov.go.saude.athena.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Representa um package FHIR carregado no servidor.
 */
@Entity
@Table(name = "packages", schema = "terminology")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
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
