package br.gov.go.saude.athena.loader;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Carrega package do FHIR Registry remoto.
 * Valida metadados do package.json após download usando PackageJsonReader.
 */
@Slf4j
public class RegistryPackageSource implements PackageSource {

    private static final String DEFAULT_REGISTRY = "https://packages.fhir.org";

    private final String packageId;
    private final String version;
    private final String registryUrl;
    private final HttpClient httpClient;
    private final CompletableFuture<byte[]> loadingFuture;

    public RegistryPackageSource(String packageId, String version, ExecutorService executor) {
        this(packageId, version, DEFAULT_REGISTRY, executor);
    }

    public RegistryPackageSource(String packageId, String version, String registryUrl, ExecutorService executor) {
        this.packageId = packageId;
        this.version = version;
        this.registryUrl = registryUrl;
        this.httpClient = HttpClient.newHttpClient();

        // Inicia o carregamento imediatamente
        this.loadingFuture = CompletableFuture.supplyAsync(this::doLoad, executor);
    }

    private byte[] doLoad() {
        try {
            String url = String.format("%s/%s/%s", registryUrl, packageId, version);
            log.info("Baixando package do registry (async): {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new IOException("Erro ao baixar package: HTTP " + response.statusCode());
            }

            byte[] packageBytes = response.body().readAllBytes();

            // TODO: talvez desnecessário
            // Valida metadados usando PackageJsonReader
            validatePackageMetadata(packageBytes);

            return packageBytes;
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    @Override
    public byte[] load() throws Exception {
        try {
            log.info("Carregando package do registry: {}:{}", packageId, version);
            return loadingFuture.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                // Se a causa for IOException, relança como está
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                throw (Exception) cause;
            }
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    /**
     * Valida se o package baixado corresponde ao packageId e version solicitados.
     * Usa PackageJsonReader para evitar código duplicado.
     */
    private void validatePackageMetadata(byte[] packageBytes) throws IOException {
        PackageJsonReader.PackageBasicInfo info = PackageJsonReader.readBasicInfo(packageBytes);

        if (!info.name().equals(packageId)) {
            throw new IOException(String.format(
                    "Package name mismatch: esperado '%s', encontrado '%s'",
                    packageId, info.name()));
        }

        if (!info.version().equals(version)) {
            throw new IOException(String.format(
                    "Package version mismatch: esperado '%s', encontrado '%s'",
                    version, info.version()));
        }

        log.debug("Package validado: {} {}", info.name(), info.version());
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
