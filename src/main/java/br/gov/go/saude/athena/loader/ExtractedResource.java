package br.gov.go.saude.athena.loader;

import lombok.Builder;
import org.hl7.fhir.r4.model.Resource;

/**
 * Representa um recurso FHIR extraído de um package.
 */
@Builder
public record ExtractedResource(
        String resourceType,
        byte[] content,
        Resource resource) {
}
