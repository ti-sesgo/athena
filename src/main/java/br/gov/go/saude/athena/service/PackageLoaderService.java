package br.gov.go.saude.athena.service;

import br.gov.go.saude.athena.config.AthenaProperties;
import br.gov.go.saude.athena.domain.PackageEntity;
import br.gov.go.saude.athena.loader.LocalPackageSource;
import br.gov.go.saude.athena.loader.PackageSource;
import br.gov.go.saude.athena.loader.RegistryPackageSource;
import br.gov.go.saude.athena.repository.PackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Serviço responsável pelo carregamento de packages FHIR.
 * Suporta carregamento local e remoto do registry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PackageLoaderService {

    private final PackageRepository packageRepository;
    private final CodeSystemLoaderService codeSystemLoaderService;
    private final ExecutorService executorService;
    private final AthenaProperties athenaProperties;

    /**
     * Carrega packages a partir de configuração.
     * Formato: "path/to/file.tgz" ou "packageId:version"
     */
    public void loadPackages(String[] packagesRef) {
        long startTime = System.nanoTime();
        log.info("Iniciando carregamento de {} packages", packagesRef.length);
        List<PackageSource> pkgs = new ArrayList<>();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String packageRef : packagesRef) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    pkgs.add(loadPackage(packageRef));
                } catch (Exception e) {
                    log.error("Erro ao carregar package {}: {}", packageRef, e.getMessage(), e);
                }
            }, executorService);

            futures.add(future);
        }

        // Aguarda todos os packages serem carregados
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        log.info("Carregamento de packages concluído em {} s", duration / 1_000_000_000L);
    }

    public PackageSource loadPackage(String packageRef) throws Exception {
        PackageSource source = createPackageSource(packageRef);

        // Verifica se já está carregado
        if (packageRepository.findByPackageIdAndVersionAndActiveTrue(source.getPackageId(), source.getVersion()).isPresent()) {
            log.info("Package {}:{} já carregado, pulando", source.getPackageId(), source.getVersion());
            return source;
        }

        log.info("Tentando criar registro do package: {}:{}", source.getPackageId(), source.getVersion());

        String registryUrl = String.format("%s/%s/%s", athenaProperties.getRegistryUrl(), source.getPackageId(),
                source.getVersion());

        PackageEntity pkg = PackageEntity.builder()
                .packageId(source.getPackageId())
                .version(source.getVersion())
                .registryUrl(source instanceof RegistryPackageSource ? registryUrl : null)
                .loadedAt(LocalDateTime.now())
                .active(true)
                .build();

        try {
            pkg = packageRepository.save(pkg);
            // Em algumas configuraçoes, o insert físico não ocorre até o flush.
            // Para garantir a detecção do conflito imediatamente, disparamos o flush:
            packageRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // Se cairmos aqui, significa que o Container A inseriu milissegundos antes,
            // e Container B tomou a exclusão de Constraint do JPA para Packages.
            // Consequentemente, encerramos imediatamente. NEM tentamos recuperar
            // CodeSystems.
            log.info(
                    "Package {}:{} já está sendo carregado por outra instância (DataIntegrityViolationException interceptada). Abortando processamento nesta instância.",
                    source.getPackageId(), source.getVersion());
            return source;
        }

        // Carrega conteúdo do package
        byte[] packageBytes = source.load();

        // Processa CodeSystems de forma concorrente
        try {
            codeSystemLoaderService.loadCodeSystems(packageBytes, pkg);
        } catch (DataIntegrityViolationException e) {
            // Apenas como fallback extremamente improvável/impossível devido a atomicidade do banco
            log.info(
                    "CodeSystems do package {}:{} já foram/estão sendo carregados por outra instância (DataIntegrityViolationException)",
                    source.getPackageId(), source.getVersion());
        }

        log.info("Processamento do package {}:{} finalizado", source.getPackageId(), source.getVersion());
        return source;
    }

    private PackageSource createPackageSource(String packageRef) {
        if (packageRef.contains(":")) {
            // Formato: packageId:version
            String[] parts = packageRef.split(":");
            return new RegistryPackageSource(parts[0], parts[1], athenaProperties.getRegistryUrl(), executorService);
        } else {
            // Formato: path/to/file.tgz
            File file = new File(packageRef);
            if (file.isDirectory()) {
                throw new IllegalArgumentException("Diretórios não são suportados diretamente. Use arquivos .tgz");
            }
            return new LocalPackageSource(packageRef);
        }
    }
}
