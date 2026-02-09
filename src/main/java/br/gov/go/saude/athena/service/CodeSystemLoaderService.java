package br.gov.go.saude.athena.service;

import br.gov.go.saude.athena.domain.CodeSystemEntity;
import br.gov.go.saude.athena.domain.ConceptEntity;
import br.gov.go.saude.athena.domain.PackageEntity;
import br.gov.go.saude.athena.loader.ExtractedResource;
import br.gov.go.saude.athena.loader.ResourceExtractor;
import br.gov.go.saude.athena.repository.CodeSystemRepository;
import br.gov.go.saude.athena.repository.ConceptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Serviço responsável pelo carregamento de CodeSystems e seus conceitos.
 * Otimizado para performance com processamento concorrente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeSystemLoaderService {

    private final ResourceExtractor resourceExtractor;
    private final CodeSystemRepository codeSystemRepository;
    private final ConceptRepository conceptRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Carrega todos os CodeSystems de um package.
     */
    public void loadCodeSystems(byte[] packageBytes, PackageEntity pkg) throws Exception {
        List<ExtractedResource> codeSystems = resourceExtractor.extractCodeSystems(packageBytes);

        log.info("Encontrados {} CodeSystems no package {}", codeSystems.size(), pkg.getPackageId());

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ExtractedResource extracted : codeSystems) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // Garante atomicidade por CodeSystem usando TransactionTemplate
                    transactionTemplate.executeWithoutResult(status -> {
                        try {
                            loadCodeSystem(extracted, pkg);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (Exception e) {
                    log.error("Erro ao carregar CodeSystem: {}", e.getMessage(), e);
                }
            }, executorService);

            futures.add(future);
        }

        // Aguarda todos os CodeSystems serem processados
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    public void loadCodeSystem(ExtractedResource extracted, PackageEntity pkgEntity) throws Exception {
        CodeSystem codeSystem = (CodeSystem) extracted.resource();

        String resourceId = codeSystem.getIdElement().getIdPart();
        String url = codeSystem.getUrl();
        String version = codeSystem.getVersion();

        log.info("Processando CodeSystem: {} (url: {}, versão: {})", url, url, version);

        // Verifica se já existe
        if (codeSystemRepository.findByUrlAndVersion(url, version).isPresent()) {
            log.info("CodeSystem {} {} já existe, pulando", url, version);
            return;
        }

        /*
         * TODO: melhoroar implementação. É uma tarefa difícl identificar a última
         * versão.
         */
        // Determina se é a versão mais recente
        boolean isLatest = codeSystemRepository.findLatestByUrl(url).isEmpty();

        // Usa status de publicação FHIR
        PublicationStatus status = codeSystem.getStatus() != null ? codeSystem.getStatus() : PublicationStatus.NULL;

        // Salva CodeSystem
        CodeSystemEntity csEntity = CodeSystemEntity.builder()
                .resourceId(resourceId != null ? resourceId : url) // Fallback para URL se não tiver ID
                .url(url)
                .version(version)
                .name(codeSystem.getName())
                .title(codeSystem.getTitle())
                .status(status)
                .description(codeSystem.getDescription())
                .content(extracted.content())
                .packageEntityRef(pkgEntity)
                .isLatest(isLatest)
                .build();

        csEntity = codeSystemRepository.save(csEntity);

        // Carrega conceitos
        if (codeSystem.hasConcept()) {
            loadConcepts(codeSystem, csEntity);
        }

        log.info("CodeSystem {} carregado com {} conceitos",
                url, codeSystem.getConcept() != null ? codeSystem.getConcept().size() : 0);
    }

    private static final int BATCH_SIZE = 1000;

    private void loadConcepts(CodeSystem codeSystem, CodeSystemEntity csEntity) throws Exception {
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
            List<ConceptEntity> buffer) throws Exception {

        /*
         * Serializa estruturas complexas para JSON apenas se existirem.
         */
        String designationsJson = null;
        if (concept.hasDesignation()) {
            designationsJson = objectMapper.writeValueAsString(concept.getDesignation());
        }

        String propertiesJson = null;
        if (concept.hasProperty()) {
            Map<String, Object> props = new HashMap<>();
            for (CodeSystem.ConceptPropertyComponent prop : concept.getProperty()) {
                /*
                 * TODO: Avaliar comportamento para valueCoding e outros tipos complexos.
                 * Atualmente pegamos apenas o valor primitivo.
                 */
                props.put(prop.getCode(), prop.getValue().primitiveValue());
            }
            propertiesJson = objectMapper.writeValueAsString(props);
        }

        ConceptEntity conceptEntity = ConceptEntity.builder()
                .system(codeSystem.getUrl())
                .version(codeSystem.getVersion())
                .code(concept.getCode())
                .display(concept.getDisplay())
                .definition(concept.getDefinition())
                .designations(designationsJson)
                .properties(propertiesJson)
                .codeSystem(csEntity)
                .active(true)
                .build();

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
}
