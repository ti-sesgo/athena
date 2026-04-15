package br.gov.go.saude.athena.service;

import br.gov.go.saude.athena.domain.CodeSystemEntity;
import br.gov.go.saude.athena.domain.ConceptEntity;
import br.gov.go.saude.athena.domain.PackageEntity;
import br.gov.go.saude.athena.loader.ExtractedResource;
import br.gov.go.saude.athena.loader.ResourceExtractor;
import br.gov.go.saude.athena.repository.CodeSystemRepository;
import br.gov.go.saude.athena.repository.ConceptRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeSystem.CodeSystemContentMode;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

/**
 * Serviço responsável pelo carregamento de CodeSystems e seus conceitos.
 * Otimizado para performance com processamento concorrente.
 */
@Slf4j
@Service
public class CodeSystemLoaderService {

    private final ResourceExtractor resourceExtractor;
    private final CodeSystemRepository codeSystemRepository;
    private final ConceptRepository conceptRepository;
    private final ExecutorService executorService;
    private final TransactionTemplate transactionTemplate;

    public CodeSystemLoaderService(ResourceExtractor resourceExtractor,
            CodeSystemRepository codeSystemRepository,
            ConceptRepository conceptRepository,
            ExecutorService executorService,
            TransactionTemplate transactionTemplate) {
        this.resourceExtractor = resourceExtractor;
        this.codeSystemRepository = codeSystemRepository;
        this.conceptRepository = conceptRepository;
        this.executorService = executorService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Carrega todos os CodeSystems de um package.
     */
    public void loadCodeSystems(byte[] packageBytes, PackageEntity pkg) throws Exception {
        log.info("Iniciando extração e carregamento de CodeSystems...");
        List<ExtractedResource> codeSystems = resourceExtractor.extractCodeSystems(packageBytes);

        // Lista thread-safe para coletar resultados
        List<LoadResult> results = new CopyOnWriteArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ExtractedResource extracted : codeSystems) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                LoadResult result;
                try {
                    // Garante atomicidade por CodeSystem usando TransactionTemplate
                    result = transactionTemplate.execute(status -> {
                        try {
                            return loadCodeSystem(extracted, pkg);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (Exception e) {
                    // Captura erro real (desembrulha RuntimeException se necessário)
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    result = LoadResult.builder()
                            .url(extracted.resource().getIdElement().getIdPart()) // Fallback id
                            .status("ERRO")
                            .message(cause.getMessage())
                            .build();
                    log.error("Falha ao carregar {}: {}", result.url, cause.getMessage());
                }
                if (result != null) {
                    results.add(result);
                }
            }, executorService);

            futures.add(future);
        }

        // Aguarda todos
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        logSummary(results, pkg);
    }

    private LoadResult loadCodeSystem(ExtractedResource extracted, PackageEntity pkgEntity) {
        CodeSystem codeSystem = (CodeSystem) extracted.resource();

        String url = codeSystem.getUrl();
        String version = codeSystem.getVersion();
        CodeSystemContentMode incomingMode = codeSystem.getContent();

        // Fragments (CodeSystem.content=fragment) de um mesmo CodeSystem chegam em artefatos
        // distintos com a mesma url+version e códigos complementares, sem overlap (spec FHIR R4).
        // Estratégia: reutilizar a CodeSystemEntity existente e acoplar os conceitos,
        // mas APENAS quando ambos os artefatos são fragment. Qualquer outra combinação indica
        // duplicação real e retorna ERRO explícito.
        CodeSystemEntity existing = codeSystemRepository
                .findByUrlAndVersionAndActiveTrue(url, version)
                .orElse(null);

        if (existing != null) {
            String conflictReason = validateFragmentMerge(existing.getContentMode(), incomingMode);
            if (conflictReason != null) {
                return LoadResult.builder()
                        .url(url)
                        .version(version)
                        .status("ERRO")
                        .message(conflictReason)
                        .build();
            }
        }

        boolean merged = existing != null;
        CodeSystemEntity csEntity = merged
                ? existing
                : persistNewCodeSystem(codeSystem, pkgEntity, extracted, url, version, incomingMode);

        int conceptsLoaded = 0;
        if (codeSystem.hasConcept()) {
            loadConcepts(codeSystem, csEntity);
            conceptsLoaded = codeSystem.getConcept().size();
            // TODO: loadConcepts é recursivo; size() conta apenas o nível raiz.
        }

        return LoadResult.builder()
                .url(url)
                .version(version)
                .conceptCount(conceptsLoaded)
                .status(merged ? "ACOPLADO" : "SUCESSO")
                .build();
    }

    /**
     * Valida que a colisão de url+version representa de fato dois fragments complementares.
     * Retorna null quando o merge é legítimo; caso contrário, a razão do conflito.
     */
    private static String validateFragmentMerge(CodeSystemContentMode existingMode,
                                                CodeSystemContentMode incomingMode) {
        if (existingMode == CodeSystemContentMode.FRAGMENT
                && incomingMode == CodeSystemContentMode.FRAGMENT) {
            return null;
        }
        return String.format(
                "Conflito em url+version: conteúdos incompatíveis para acoplamento (existente=%s, novo=%s). Apenas fragment+fragment é aceitável.",
                modeLabel(existingMode), modeLabel(incomingMode));
    }

    private static String modeLabel(CodeSystemContentMode mode) {
        return mode != null ? mode.toCode() : "null";
    }

    private CodeSystemEntity persistNewCodeSystem(CodeSystem codeSystem, PackageEntity pkgEntity,
                                                  ExtractedResource extracted, String url, String version,
                                                  CodeSystemContentMode contentMode) {
        String resourceId = codeSystem.getIdElement().getIdPart();

        /*
         * TODO: identificar a versão "mais recente" de forma semântica. Hoje, a primeira versão a
         * chegar para uma URL recebe isLatest=true.
         */
        boolean isLatest = codeSystemRepository.findByUrlAndIsLatestTrueAndActiveTrue(url).isEmpty();

        PublicationStatus status = codeSystem.getStatus() != null ? codeSystem.getStatus() : PublicationStatus.NULL;

        CodeSystemEntity csEntity = CodeSystemEntity.builder()
                .resourceId(resourceId != null ? resourceId : url)
                .url(url)
                .version(version)
                .name(codeSystem.getName())
                .title(codeSystem.getTitle())
                .status(status)
                .contentMode(contentMode)
                .content(extracted.content())
                .packageEntityRef(pkgEntity)
                .isLatest(isLatest)
                .build();

        try {
            return codeSystemRepository.saveAndFlush(csEntity);
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Carga paralela: outro fragment acabou de persistir a mesma url+version.
            // Recupera a entity que venceu a corrida para acoplar os conceitos deste fragment.
            return codeSystemRepository.findByUrlAndVersionAndActiveTrue(url, version)
                    .orElseThrow(() -> concurrentInsert);
        }
    }

    private static final int BATCH_SIZE = 1000;

    private void loadConcepts(CodeSystem codeSystem, CodeSystemEntity csEntity) {
        // Buffer iniciado com tamanho exato para evitar realocação
        List<ConceptEntity> buffer = new ArrayList<>(BATCH_SIZE);

        for (CodeSystem.ConceptDefinitionComponent concept : codeSystem.getConcept()) {
            processConceptRecursive(concept, codeSystem, csEntity, buffer);
        }

        // Garante que o restante do buffer seja salvo
        flushBuffer(buffer);
    }

    private void processConceptRecursive(
            CodeSystem.ConceptDefinitionComponent concept,
            CodeSystem codeSystem,
            CodeSystemEntity csEntity,
            List<ConceptEntity> buffer) {

        // Fallback: title > name > url (CodeSystem name é obrigatório)
        String csName = codeSystem.hasTitle() ? codeSystem.getTitle()
                : (codeSystem.hasName() ? codeSystem.getName() : codeSystem.getUrl());

        ConceptEntity conceptEntity = ConceptEntity.builder()
                .codeSystemUrl(codeSystem.getUrl())
                .code(concept.getCode())
                .display(concept.getDisplay())
                .definition(concept.getDefinition())
                .codeSystemName(csName)
                .codeSystemVersion(codeSystem.getVersion())
                .codeSystem(csEntity)
                .codeSystemIsLatest(csEntity.getIsLatest())
                .active(csEntity.getActive())
                .build();

        conceptEntity.setProperty(concept.getProperty());
        conceptEntity.setDesignation(concept.getDesignation());

        buffer.add(conceptEntity);

        // Se atingiu o limite, descarrega no banco
        if (buffer.size() >= BATCH_SIZE) {
            flushBuffer(buffer);
        }

        // Recursão para conceitos filhos
        if (concept.hasConcept()) {
            for (CodeSystem.ConceptDefinitionComponent child : concept.getConcept()) {
                processConceptRecursive(child, codeSystem, csEntity, buffer);
            }
        }
    }

    private void flushBuffer(List<ConceptEntity> buffer) {
        if (!buffer.isEmpty()) {
            conceptRepository.saveAll(buffer);
            buffer.clear();
        }
    }

    private void logSummary(List<LoadResult> results, PackageEntity pkg) {
        StringBuilder sb = new StringBuilder();
        // 135 chars separator
        String separator = "\n==================================================================================================================================";
        sb.append(separator);
        sb.append("\n RELATÓRIO DE CARGA (CodeSystem) - ").append(pkg.getPackageId()).append(" : ")
                .append(pkg.getVersion());
        sb.append(separator);
        sb.append(String.format("\n| %-87s | %-10s | %-10s | %-10s |", "URL / ID", "VERSÃO", "CONCEITOS", "STATUS"));
        sb.append(
                "\n|-----------------------------------------------------------------------------------------|------------|------------|------------|");

        // Ordena por URL para facilitar leitura
        results.stream()
                .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.url != null ? a.url : "",
                        b.url != null ? b.url : ""))
                .forEach(r -> {
                    String subUrl = r.url != null && r.url.length() > 87 ? "..." + r.url.substring(r.url.length() - 84)
                            : r.url;
                    sb.append(String.format("\n| %-87s | %-10s | %-10d | %-10s |",
                            subUrl,
                            r.version != null ? r.version : "-",
                            r.conceptCount,
                            r.status));
                });

        sb.append(separator);
        int total = results.size();
        long success = results.stream().filter(r -> "SUCESSO".equals(r.status)).count();
        long merged = results.stream().filter(r -> "ACOPLADO".equals(r.status)).count();
        long ignored = results.stream().filter(r -> "IGNORADO".equals(r.status)).count();
        long errors = results.stream().filter(r -> "ERRO".equals(r.status)).count();

        sb.append(String.format("\n TOTAL: %d  |  SUCESSO: %d  |  ACOPLADO: %d  |  IGNORADO: %d  |  ERRO: %d",
                total, success, merged, ignored, errors));
        sb.append(separator);

        log.info(sb.toString());
    }

    @Builder
    @Getter
    private static class LoadResult {
        String url;
        String version;
        int conceptCount;
        String status;
        String message;
    }
}
