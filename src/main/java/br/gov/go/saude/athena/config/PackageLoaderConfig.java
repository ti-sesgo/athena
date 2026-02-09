package br.gov.go.saude.athena.config;

import br.gov.go.saude.athena.service.PackageLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Configuração para carregamento automático de packages na inicialização.
 * 
 * <p>Configuração via application.properties:
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
@RequiredArgsConstructor
public class PackageLoaderConfig {
    
    private final PackageLoaderService packageLoaderService;
    
    @Value("${athena.packages:}")
    private String packagesConfig;
    
    @Value("${athena.packages.auto-load:true}")
    private boolean autoLoad;
    
    /**
     * Carrega packages automaticamente após a aplicação estar pronta.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadPackagesOnStartup() {
        if (!autoLoad) {
            log.info("Carregamento automático de packages desabilitado");
            return;
        }
        
        if (packagesConfig == null || packagesConfig.trim().isEmpty()) {
            log.warn("Nenhum package configurado para carregamento (athena.packages)");
            return;
        }
        
        String[] packages = packagesConfig.split(",");
        log.info("Iniciando carregamento automático de {} packages", packages.length);
        
        try {
            packageLoaderService.loadPackages(packages);
            log.info("Carregamento automático concluído com sucesso");
        } catch (Exception e) {
            log.error("Erro no carregamento automático de packages", e);
        }
    }
}
