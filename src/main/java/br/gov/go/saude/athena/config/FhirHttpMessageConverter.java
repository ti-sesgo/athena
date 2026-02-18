package br.gov.go.saude.athena.config;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class FhirHttpMessageConverter extends AbstractHttpMessageConverter<IBaseResource> {

    private final IParser jsonParser;
    private final IParser xmlParser;

    public FhirHttpMessageConverter(IParser jsonParser, IParser xmlParser) {
        super(MediaType.parseMediaType("application/fhir+json"),
                MediaType.parseMediaType("application/json"),
                MediaType.parseMediaType("application/fhir+xml"),
                MediaType.parseMediaType("application/xml"));
        this.jsonParser = jsonParser;
        this.xmlParser = xmlParser;
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

        try (OutputStreamWriter writer = new OutputStreamWriter(outputMessage.getBody(), StandardCharsets.UTF_8)) {
            parser.encodeResourceToWriter(resource, writer);
        }
    }

    private IParser getParser(MediaType contentType) {
        if (contentType != null && (contentType.includes(MediaType.APPLICATION_XML) ||
                contentType.getSubtype().contains("xml"))) {
            return xmlParser;
        }
        return jsonParser;
    }
}
