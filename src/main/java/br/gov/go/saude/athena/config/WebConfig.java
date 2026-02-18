package br.gov.go.saude.athena.config;

import ca.uhn.fhir.parser.IParser;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final IParser jsonParser;
    private final IParser xmlParser;

    public WebConfig(IParser jsonParser, IParser xmlParser) {
        this.jsonParser = jsonParser;
        this.xmlParser = xmlParser;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, new FhirHttpMessageConverter(jsonParser, xmlParser));
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/fhir",
                c -> c.isAnnotationPresent(RestController.class));
    }
}
