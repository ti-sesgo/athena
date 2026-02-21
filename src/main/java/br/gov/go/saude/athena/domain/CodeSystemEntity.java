package br.gov.go.saude.athena.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;

/**
 * Representa um CodeSystem FHIR armazenado.
 *
 * <p>
 * Separação de IDs:
 * <ul>
 * <li><b>id</b>: Surrogate key (gerado pelo banco)</li>
 * <li><b>resourceId</b>: ID lógico FHIR do recurso (ex: "loinc")</li>
 * </ul>
 */
@Entity
@Table(
        name = "code_systems",
        schema = "terminology",
        // TODO: CodeSystem.content = #fragment quebram essa lógica
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cs_url_version",
                        columnNames = {"url", "version"})
        },
        indexes = {
                @Index(name = "idx_cs_resource_id_active", columnList = "resource_id, active"),
                @Index(name="idx_cs_url_active_version", columnList="url, active, version"),
                @Index(name="idx_cs_url_active_is_latest", columnList="url, active, is_latest")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor
public class CodeSystemEntity {

    public CodeSystemEntity() {}

    /**
     * ID interno do banco (surrogate key).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID lógico FHIR do recurso (business key).
     * Exemplo: "loinc", "snomed-ct", "icd-10"
     */
    @Column(nullable = false)
    private String resourceId;

    /**
     * URL canônica do CodeSystem.
     * Exemplo: "http://loinc.org"
     */
    @Column(nullable = false)
    private String url;

    /**
     * Versão do CodeSystem.
     * Exemplo: "2.75"
     */
    @Column
    private String version;

    @Column
    private String name;

    @Column
    private String title;

    /**
     * Status de publicação FHIR.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicationStatus status;

    /**
     * Conteúdo completo do recurso FHIR em JSON.
     */
    @Column(nullable = false)
    private byte[] content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private PackageEntity packageEntityRef;

    /**
     * Indica se é a versão mais recente deste CodeSystem.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isLatest = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
