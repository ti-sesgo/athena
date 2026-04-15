package br.gov.go.saude.athena.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;

/**
 * Representa um ValueSet FHIR armazenado.
 *
 * <p>
 * ValueSet não é fragmentável pela spec FHIR R4, logo uma row por (url, version).
 * O recurso completo (incluindo {@code compose} e {@code expansion}) é preservado
 * em {@code content} como JSON FHIR para operações posteriores ($expand, $validate-code).
 * </p>
 */
@Entity
@Table(
        name = "value_sets",
        schema = "terminology",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_vs_url_version",
                        columnNames = {"url", "version"})
        },
        indexes = {
                @Index(name = "idx_vs_resource_id_active", columnList = "resource_id, active"),
                @Index(name = "idx_vs_url_active_version", columnList = "url, active, version"),
                @Index(name = "idx_vs_url_active_is_latest", columnList = "url, active, is_latest")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor
public class ValueSetEntity {

    public ValueSetEntity() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String resourceId;

    @Column(nullable = false)
    private String url;

    @Column
    private String version;

    @Column
    private String name;

    @Column
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicationStatus status;

    @Column(nullable = false)
    private byte[] content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private PackageEntity packageEntityRef;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isLatest = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
