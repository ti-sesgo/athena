package br.gov.go.saude.athena.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

/**
 * Utilitário para ler package.json de packages FHIR (.tgz).
 * Elimina duplicação de código entre LocalPackageSource e RegistryPackageSource.
 */
@Slf4j
public class PackageJsonReader {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    /**
     * Lê metadados completos do package.json.
     * 
     * @param packageBytes conteúdo do package .tgz
     * @return metadados completos do package
     * @throws IOException se package.json não for encontrado
     */
    public static PackageMetadata readMetadata(byte[] packageBytes) throws IOException {
        return readFromPackageJson(packageBytes, jsonContent -> {
            try {
                PackageMetadata metadata = OBJECT_MAPPER.readValue(jsonContent, PackageMetadata.class);
                log.debug("Metadados completos lidos: {} {}", metadata.name(), metadata.version());
                return metadata;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Lê apenas name e version do package.json.
     * Método otimizado para casos onde metadados completos não são necessários.
     */
    public static PackageBasicInfo readBasicInfo(byte[] packageBytes) throws IOException {
        return readFromPackageJson(packageBytes, jsonContent -> {
            try {
                var json = OBJECT_MAPPER.readTree(jsonContent);
                String name = json.get("name").asText();
                String version = json.get("version").asText();
                return new PackageBasicInfo(name, version);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Template method para ler e processar package.json.
     * 
     * @param packageBytes conteúdo do package .tgz
     * @param processor função que processa o conteúdo JSON
     * @return resultado do processamento
     */
    private static <T> T readFromPackageJson(byte[] packageBytes, Function<String, T> processor) throws IOException {
        try (InputStream bis = new ByteArrayInputStream(packageBytes);
             GZIPInputStream gzis = new GZIPInputStream(bis);
             TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {
            
            TarArchiveEntry entry;
            while ((entry = tais.getNextEntry()) != null) {
                if (isPackageJson(entry)) {
                    byte[] content = tais.readNBytes((int) entry.getSize());
                    String jsonContent = new String(content, StandardCharsets.UTF_8);
                    return processor.apply(jsonContent);
                }
            }
            
            throw new IOException("package.json não encontrado no package");
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioe) {
                throw ioe;
            }
            throw e;
        }
    }

    /**
     * Verifica se a entry é o arquivo package.json.
     */
    private static boolean isPackageJson(TarArchiveEntry entry) {
        return entry.isFile() && 
               (entry.getName().endsWith("package/package.json") || 
                entry.getName().endsWith("package.json"));
    }

    /**
     * Informações básicas do package (name e version).
     */
    public record PackageBasicInfo(String name, String version) {}
}
