package br.gov.go.saude.athena.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "athena.packages")
@Getter
@Setter
public class AthenaProperties {
    /**
     * Habilita o carregamento automático de packages.
     */
    private boolean autoLoad = true;

    /**
     * Lista de packages a serem carregados.
     */
    private List<String> items = new ArrayList<>();
}
