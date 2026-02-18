package br.gov.go.saude.athena.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /**
     * Configura o contexto FHIR R4 como bean singleton.
     * 
     * <p>
     * FhirContext é thread-safe e deve ser reutilizado durante toda a vida da
     * aplicação.
     * É custoso criar múltiplas instâncias pois realiza scan de classes de
     * recursos.
     * 
     * @return contexto FHIR R4 configurado
     */
    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    /**
     * Cria um parser JSON para serialização/deserialização de recursos FHIR.
     * Necessário para CapabilityStatementController.
     *
     * @param fhirContext contexto FHIR configurado
     * @return parser JSON configurado para FHIR R4
     */
    @Bean
    public IParser jsonParser(FhirContext fhirContext) {
        return fhirContext.newJsonParser();
    }
}
