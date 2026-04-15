package br.gov.go.saude.athena.service;

import br.gov.go.saude.athena.domain.PackageEntity;
import br.gov.go.saude.athena.domain.ValueSetEntity;
import br.gov.go.saude.athena.loader.ExtractedResource;
import br.gov.go.saude.athena.loader.ResourceExtractor;
import br.gov.go.saude.athena.repository.ValueSetRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

/**
 * Serviço responsável pelo carregamento de ValueSets de um package.
 * <p>
 * ValueSet não tem overlap semântico entre artefatos (diferente de CodeSystem fragment),
 * então uma linha por (url, version) é suficiente. O JSON completo do recurso é
 * preservado em {@code content} para operações futuras ($expand, $validate-code em ValueSet).
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValueSetLoaderService {

    private final ResourceExtractor resourceExtractor;
    private final ValueSetRepository valueSetRepository;
    private final ExecutorService executorService;
    private final TransactionTemplate transactionTemplate;

    public void loadValueSets(byte[] packageBytes, PackageEntity pkg) throws Exception {
        log.info("Iniciando extração e carregamento de ValueSets...");
        List<ExtractedResource> valueSets = resourceExtractor.extractValueSets(packageBytes);

        List<LoadResult> results = new CopyOnWriteArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ExtractedResource extracted : valueSets) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                LoadResult result;
                try {
                    result = transactionTemplate.execute(status -> loadValueSet(extracted, pkg));
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    result = LoadResult.builder()
                            .url(extracted.resource().getIdElement().getIdPart())
                            .status("ERRO")
                            .message(cause.getMessage())
                            .build();
                    log.error("Falha ao carregar ValueSet {}: {}", result.url, cause.getMessage());
                }
                if (result != null) {
                    results.add(result);
                }
            }, executorService);

            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        logSummary(results, pkg);
    }

    private LoadResult loadValueSet(ExtractedResource extracted, PackageEntity pkgEntity) {
        ValueSet valueSet = (ValueSet) extracted.resource();

        String url = valueSet.getUrl();
        String version = valueSet.getVersion();

        if (valueSetRepository.findByUrlAndVersionAndActiveTrue(url, version).isPresent()) {
            return LoadResult.builder()
                    .url(url)
                    .version(version)
                    .status("IGNORADO")
                    .message("Já existe")
                    .build();
        }

        String resourceId = valueSet.getIdElement().getIdPart();
        PublicationStatus status = valueSet.getStatus() != null ? valueSet.getStatus() : PublicationStatus.NULL;
        boolean isLatest = valueSetRepository.findByUrlAndIsLatestTrueAndActiveTrue(url).isEmpty();

        ValueSetEntity entity = ValueSetEntity.builder()
                .resourceId(resourceId != null ? resourceId : url)
                .url(url)
                .version(version)
                .name(valueSet.getName())
                .title(valueSet.getTitle())
                .status(status)
                .content(extracted.content())
                .packageEntityRef(pkgEntity)
                .isLatest(isLatest)
                .build();

        try {
            valueSetRepository.saveAndFlush(entity);
            return LoadResult.builder()
                    .url(url)
                    .version(version)
                    .status("SUCESSO")
                    .build();
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Carga paralela: outra thread/instância acabou de persistir este ValueSet.
            return LoadResult.builder()
                    .url(url)
                    .version(version)
                    .status("IGNORADO")
                    .message("Concorrência resolvida por DataIntegrityViolationException")
                    .build();
        }
    }

    private void logSummary(List<LoadResult> results, PackageEntity pkg) {
        String separator = "\n==================================================================================================================================";
        StringBuilder sb = new StringBuilder(separator);
        sb.append("\n RELATÓRIO DE CARGA (ValueSet) - ").append(pkg.getPackageId()).append(" : ").append(pkg.getVersion());
        sb.append(separator);
        sb.append(String.format("\n| %-99s | %-10s | %-10s |", "URL", "VERSÃO", "STATUS"));
        sb.append("\n|-----------------------------------------------------------------------------------------------------|------------|------------|");

        results.stream()
                .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.url != null ? a.url : "",
                        b.url != null ? b.url : ""))
                .forEach(r -> {
                    String subUrl = r.url != null && r.url.length() > 99 ? "..." + r.url.substring(r.url.length() - 96) : r.url;
                    sb.append(String.format("\n| %-99s | %-10s | %-10s |",
                            subUrl,
                            r.version != null ? r.version : "-",
                            r.status));
                });

        sb.append(separator);
        int total = results.size();
        long success = results.stream().filter(r -> "SUCESSO".equals(r.status)).count();
        long ignored = results.stream().filter(r -> "IGNORADO".equals(r.status)).count();
        long errors = results.stream().filter(r -> "ERRO".equals(r.status)).count();

        sb.append(String.format("\n TOTAL: %d  |  SUCESSO: %d  |  IGNORADO: %d  |  ERRO: %d",
                total, success, ignored, errors));
        sb.append(separator);

        log.info(sb.toString());
    }

    @Builder
    @Getter
    private static class LoadResult {
        String url;
        String version;
        String status;
        String message;
    }
}
