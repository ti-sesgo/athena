package br.gov.go.saude.athena.loader;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import lombok.Builder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.zip.GZIPInputStream;

/**
 * Extrai recursos FHIR de packages .tgz.
 * Suporta extração paralela de múltiplos tipos de recursos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceExtractor {

    private final FhirContext fhirContext;
    private final ExecutorService executorService;

    /**
     * Resultado da extração de todas as terminologias.
     */
    @Builder
    public record TerminologyResources(
            List<ExtractedResource> codeSystems,
            List<ExtractedResource> valueSets,
            List<ExtractedResource> conceptMaps) {
    }

    /**
     * Extrai CodeSystems de um package.
     * 
     * @param packageBytes conteúdo do package
     * @return lista de CodeSystems extraídos
     */
    public List<ExtractedResource> extractCodeSystems(byte[] packageBytes) throws IOException {
        return extract(packageBytes, "CodeSystem");
    }

    /**
     * Extrai ValueSets de um package.
     * 
     * @param packageBytes conteúdo do package
     * @return lista de ValueSets extraídos
     */
    public List<ExtractedResource> extractValueSets(byte[] packageBytes) throws IOException {
        return extract(packageBytes, "ValueSet");
    }

    /**
     * Extrai ConceptMaps de um package.
     * 
     * @param packageBytes conteúdo do package
     * @return lista de ConceptMaps extraídos
     */
    public List<ExtractedResource> extractConceptMaps(byte[] packageBytes) throws IOException {
        return extract(packageBytes, "ConceptMap");
    }

    /**
     * Extrai todas as terminologias de um package em paralelo.
     * Processa CodeSystems, ValueSets e ConceptMaps simultaneamente.
     * 
     * @param packageBytes conteúdo do package
     * @return todas as terminologias extraídas
     */
    public TerminologyResources extractAllTerminologies(byte[] packageBytes) {
        log.info("Extraindo todas as terminologias em paralelo");

        CompletableFuture<List<ExtractedResource>> csFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return extractCodeSystems(packageBytes);
            } catch (IOException e) {
                log.error("Erro ao extrair CodeSystems", e);
                return List.of();
            }
        }, executorService);

        CompletableFuture<List<ExtractedResource>> vsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return extractValueSets(packageBytes);
            } catch (IOException e) {
                log.error("Erro ao extrair ValueSets", e);
                return List.of();
            }
        }, executorService);

        CompletableFuture<List<ExtractedResource>> cmFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return extractConceptMaps(packageBytes);
            } catch (IOException e) {
                log.error("Erro ao extrair ConceptMaps", e);
                return List.of();
            }
        }, executorService);

        // Aguarda todas as extrações
        CompletableFuture.allOf(csFuture, vsFuture, cmFuture).join();

        TerminologyResources result = TerminologyResources.builder()
                .codeSystems(csFuture.join())
                .valueSets(vsFuture.join())
                .conceptMaps(cmFuture.join())
                .build();

        log.info("Extração concluída: {} CodeSystems, {} ValueSets, {} ConceptMaps",
                result.codeSystems().size(),
                result.valueSets().size(),
                result.conceptMaps().size());

        return result;
    }

    /**
     * Extrai recursos de um package .tgz.
     * 
     * @param packageBytes conteúdo do package
     * @param resourceType tipo de recurso a extrair (ex: "CodeSystem")
     * @return lista de recursos extraídos
     */
    public List<ExtractedResource> extract(byte[] packageBytes, String resourceType) throws IOException {
        List<ExtractedResource> resources = new ArrayList<>();
        IParser jsonParser = fhirContext.newJsonParser();

        try (InputStream bis = new ByteArrayInputStream(packageBytes);
                GZIPInputStream gzis = new GZIPInputStream(bis);
                TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {

            TarArchiveEntry entry;
            while ((entry = tais.getNextEntry()) != null) {
                if (entry.isFile() && entry.getName().endsWith(".json")) {
                    try {
                        byte[] content = tais.readNBytes((int) entry.getSize());
                        String jsonContent = new String(content);

                        // Parse como IBaseResource e faz cast para Resource
                        IBaseResource baseResource = jsonParser.parseResource(jsonContent);

                        if (baseResource instanceof Resource resource) {
                            if (resource.getResourceType().name().equals(resourceType)) {
                                resources.add(ExtractedResource.builder()
                                        .resourceType(resource.getResourceType().name())
                                        .content(content)
                                        .resource(resource)
                                        .build());

                                log.debug("Extraído recurso: {} - {}",
                                        resource.getResourceType(),
                                        resource.getIdElement().getIdPart());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Erro ao processar arquivo {}: {}", entry.getName(), e.getMessage());
                    }
                }
            }
        }

        log.info("Extraídos {} recursos do tipo {}", resources.size(), resourceType);
        return resources;
    }
}
