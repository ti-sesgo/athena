package br.gov.go.saude.athena.config;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FhirContext fhirContext;

    public WebConfig(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, new FhirHttpMessageConverter(fhirContext));
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/fhir",
                c -> c.isAnnotationPresent(RestController.class));
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.favorParameter(true)
                .parameterName("_format")
                .ignoreAcceptHeader(false)
                .defaultContentType(MediaType.parseMediaType("application/fhir+json"))
                .mediaType("json", MediaType.parseMediaType("application/fhir+json"))
                .mediaType("application/json", MediaType.parseMediaType("application/fhir+json"))
                .mediaType("application/fhir+json", MediaType.parseMediaType("application/fhir+json"))
                .mediaType("xml", MediaType.parseMediaType("application/fhir+xml"))
                .mediaType("text/xml", MediaType.parseMediaType("application/fhir+xml"))
                .mediaType("application/xml", MediaType.parseMediaType("application/fhir+xml"))
                .mediaType("application/fhir+xml", MediaType.parseMediaType("application/fhir+xml"));
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/fhir");
    }
}
