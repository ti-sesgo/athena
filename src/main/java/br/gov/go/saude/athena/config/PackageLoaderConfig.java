package br.gov.go.saude.athena.config;

import br.gov.go.saude.athena.service.PackageLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import java.util.List;

/**
 * Configuração para carregamento automático de packages na inicialização.
 * 
 * <p>
 * Configuração via application.properties:
 * 
 * <pre>
 * # Carregamento local
 * athena.packages=/path/to/package1.tgz,/path/to/package2.tgz
 * 
 * # Carregamento remoto
 * athena.packages=hl7.fhir.r4.core:4.0.1,hl7.fhir.uv.extensions:5.2.0
 * 
 * # Misto
 * athena.packages=/path/local.tgz,hl7.fhir.r4.core:4.0.1
 * </pre>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AthenaProperties.class)
@RequiredArgsConstructor
public class PackageLoaderConfig {

    private final PackageLoaderService packageLoaderService;
    private final AthenaProperties athenaProperties;

    /**
     * Carrega packages automaticamente após a aplicação estar pronta.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadPackagesOnStartup() {
        if (!athenaProperties.isAutoLoad()) {
            log.info("Carregamento automático de packages desabilitado");
            return;
        }

        List<String> items = athenaProperties.getItems();
        if (items == null || items.isEmpty()) {
            log.warn("Nenhum package configurado para carregamento (athena.packages.items)");
            return;
        }

        String[] packages = items.toArray(new String[0]);
        log.info("Packages configurados: {}", packages.length);

        try {
            packageLoaderService.loadPackages(packages);
        } catch (Exception e) {
            log.error("Erro no carregamento automático de packages", e);
        }
    }
}
