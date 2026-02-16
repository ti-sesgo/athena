package br.gov.go.saude.athena.loader;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Carrega package de arquivo local (.tgz).
 * Extrai metadados do arquivo package.json interno usando PackageJsonReader.
 */
@Slf4j
public class LocalPackageSource implements PackageSource {

    private final Path filePath;
    private String packageId;
    private String version;

    public LocalPackageSource(String filePath) {
        this.filePath = Path.of(filePath);
        extractMetadata();
    }

    /**
     * Extrai metadados do package.json dentro do .tgz.
     */
    private void extractMetadata() {
        try {
            byte[] packageBytes = Files.readAllBytes(filePath);
            PackageJsonReader.PackageBasicInfo info = PackageJsonReader.readBasicInfo(packageBytes);

            this.packageId = info.name();
            this.version = info.version();

            log.debug("Metadados extraídos: {} {}", packageId, version);

        } catch (IOException e) {
            log.warn("Erro ao extrair metadados, usando nome do arquivo: {}", e.getMessage());
        }
    }

    @Override
    public byte[] load() throws IOException {
        return Files.readAllBytes(filePath);
    }

    @Override
    public String getPackageId() {
        return packageId;
    }

    @Override
    public String getVersion() {
        return version;
    }
}
