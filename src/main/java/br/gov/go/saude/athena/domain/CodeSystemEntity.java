package br.gov.go.saude.athena.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
@Table(name = "code_systems", schema = "terminology", indexes = {
        @Index(name = "idx_cs_url", columnList = "url"),
        @Index(name = "idx_cs_url_version", columnList = "url, version"),
        @Index(name = "idx_cs_url_latest", columnList = "url, isLatest"),
        @Index(name = "idx_cs_status", columnList = "status"),
        @Index(name = "idx_cs_resource_id", columnList = "resourceId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeSystemEntity {

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
    @Column(nullable = false, length = 255)
    private String resourceId;

    /**
     * URL canônica do CodeSystem.
     * Exemplo: "http://loinc.org"
     */
    @Column(nullable = false, length = 500)
    private String url;

    /**
     * Versão do CodeSystem.
     * Exemplo: "2.75"
     */
    @Column(length = 100)
    private String version;

    @Column(length = 255)
    private String name;

    @Column(length = 500)
    private String title;

    /**
     * Status de publicação FHIR.
     * Usa enum do HAPI FHIR diretamente.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublicationStatus status;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Conteúdo completo do recurso FHIR em JSON.
     */
    @Lob
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
}
