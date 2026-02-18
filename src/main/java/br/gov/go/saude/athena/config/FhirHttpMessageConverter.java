package br.gov.go.saude.athena.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

public class FhirHttpMessageConverter extends AbstractHttpMessageConverter<IBaseResource> {

    private static final List<MediaType> SUPPORTED_JSON_MEDIA_TYPES = List.of(
            MediaType.parseMediaType("application/fhir+json"),
            MediaType.parseMediaType("application/json"),
            MediaType.parseMediaType("text/json"),
            MediaType.parseMediaType("application/json+fhir"));

    private static final List<MediaType> SUPPORTED_XML_MEDIA_TYPES = List.of(
            MediaType.parseMediaType("application/fhir+xml"),
            MediaType.parseMediaType("application/xml"),
            MediaType.parseMediaType("text/xml"),
            MediaType.parseMediaType("application/xml+fhir"));

    private static final List<MediaType> SUPPORTED_MEDIA_TYPES = Stream
            .concat(SUPPORTED_JSON_MEDIA_TYPES.stream(), SUPPORTED_XML_MEDIA_TYPES.stream()).toList();

    private final FhirContext fhirContext;

    public FhirHttpMessageConverter(FhirContext fhirContext) {
        super(SUPPORTED_MEDIA_TYPES.toArray(new MediaType[0]));
        this.fhirContext = fhirContext;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return IBaseResource.class.isAssignableFrom(clazz);
    }

    @Override
    protected IBaseResource readInternal(Class<? extends IBaseResource> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        MediaType contentType = inputMessage.getHeaders().getContentType();
        IParser parser = getParser(contentType);

        try (InputStreamReader reader = new InputStreamReader(inputMessage.getBody(), StandardCharsets.UTF_8)) {
            return parser.parseResource(clazz, reader);
        }
    }

    @Override
    protected void writeInternal(IBaseResource resource, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        MediaType contentType = outputMessage.getHeaders().getContentType();
        IParser parser = getParser(contentType);

        if (isPrettyPrint()) {
            parser.setPrettyPrint(true);
        }

        try (OutputStreamWriter writer = new OutputStreamWriter(outputMessage.getBody(), StandardCharsets.UTF_8)) {
            parser.encodeResourceToWriter(resource, writer);
        }
    }

    /**
     * Retornar o parser correto de acordo com o MediaType correspondente
     *
     *
     * @param contentType O MediaType. Um subconjunto do {@code SUPPORTED_MEDIA_TYPES}
     * @return parser correspondente ao MediaType
     */
    private IParser getParser(MediaType contentType) {
        if (contentType.getSubtype().contains("xml")) {
            return fhirContext.newXmlParser();
        }

        return fhirContext.newJsonParser();
    }

    private boolean isPrettyPrint() {
        try {
            var requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
                var request = servletRequestAttributes.getRequest();
                String pretty = request.getParameter("_pretty");
                return "true".equalsIgnoreCase(pretty);
            }
        } catch (NoClassDefFoundError | Exception e) {
            // Ignore if not in a web context or RequestContextHolder is unavailable
        }
        return false;
    }
}
