package br.gov.go.saude.athena.service;

import br.gov.go.saude.athena.domain.PackageEntity;
import br.gov.go.saude.athena.loader.LocalPackageSource;
import br.gov.go.saude.athena.loader.PackageSource;
import br.gov.go.saude.athena.loader.RegistryPackageSource;
import br.gov.go.saude.athena.repository.PackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Carrega packages a partir de configuração.
     * Formato: "path/to/file.tgz" ou "packageId:version"
     */
    public void loadPackages(String[] packagesRef) {
        log.info("Iniciando carregamento de {} packages", packagesRef.length);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String packageRef : packagesRef) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    loadPackage(packageRef);
                } catch (Exception e) {
                    log.error("Erro ao carregar package {}: {}", packageRef, e.getMessage(), e);
                }
            }, executorService);

            futures.add(future);
        }

        // Aguarda todos os packages serem carregados
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("Carregamento de packages concluído");
    }

    public void loadPackage(String packageRef) throws Exception {
        PackageSource source = createPackageSource(packageRef);

        // Verifica se já está carregado
        if (packageRepository.findByPackageIdAndVersion(source.getPackageId(), source.getVersion()).isPresent()) {
            log.info("Package {}:{} já carregado, pulando", source.getPackageId(), source.getVersion());
            return;
        }

        log.info("Carregando package: {}:{}", source.getPackageId(), source.getVersion());

        // Salva metadados do package
        PackageEntity pkg = PackageEntity.builder()
                .packageId(source.getPackageId())
                .version(source.getVersion())
                .registryUrl(source instanceof RegistryPackageSource ? "https://packages.fhir.org" : null)
                .loadedAt(LocalDateTime.now())
                .active(true)
                .build();

        pkg = packageRepository.save(pkg);

        // Carrega conteúdo do package
        byte[] packageBytes = source.load();

        // Processa CodeSystems de forma concorrente
        codeSystemLoaderService.loadCodeSystems(packageBytes, pkg);

        log.info("Package {}:{} carregado com sucesso", source.getPackageId(), source.getVersion());
    }

    private PackageSource createPackageSource(String packageRef) {
        if (packageRef.contains(":")) {
            // Formato: packageId:version
            String[] parts = packageRef.split(":");
            return new RegistryPackageSource(parts[0], parts[1], executorService);
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
