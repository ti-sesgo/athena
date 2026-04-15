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
     * Falha com IllegalStateException quando o arquivo é inacessível ou não contém package.json válido,
     * evitando que packageId/version nulos se propaguem para a camada de persistência.
     */
    private void extractMetadata() {
        try {
            byte[] packageBytes = Files.readAllBytes(filePath);
            PackageJsonReader.PackageBasicInfo info = PackageJsonReader.readBasicInfo(packageBytes);

            this.packageId = info.name();
            this.version = info.version();

            if (this.packageId == null || this.version == null) {
                throw new IllegalStateException(
                        "package.json de " + filePath + " não contém name/version obrigatórios");
            }

            log.debug("Metadados extraídos: {} {}", packageId, version);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Falha ao ler package local: " + filePath + " (" + e.getMessage() + ")", e);
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
