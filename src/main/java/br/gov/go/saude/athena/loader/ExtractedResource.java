package br.gov.go.saude.athena.loader;

import lombok.Builder;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * Representa um recurso FHIR extraído de um package.
 */
@Builder
public record ExtractedResource(
        byte[] content,
        IBaseResource resource) {
}
