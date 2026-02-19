package br.gov.go.saude.athena.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ContentNegotiationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnJsonByDefault() throws Exception {
        mockMvc.perform(get("/fhir/metadata"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json;charset=UTF-8"));
    }

    @Test
    void shouldReturnJsonWhenRequestedViaAcceptHeader() throws Exception {
        mockMvc.perform(get("/fhir/metadata")
                .accept("application/fhir+json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json;charset=UTF-8"));
    }

    @Test
    void shouldReturnXmlWhenRequestedViaAcceptHeader() throws Exception {
        mockMvc.perform(get("/fhir/metadata")
                .accept("application/fhir+xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+xml;charset=UTF-8"));
    }

    @Test
    void shouldReturnJsonWhenRequestedViaFormatParameter() throws Exception {
        mockMvc.perform(get("/fhir/metadata?_format=json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json;charset=UTF-8"));
    }

    @Test
    void shouldReturnXmlWhenRequestedViaFormatParameter() throws Exception {
        mockMvc.perform(get("/fhir/metadata?_format=xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+xml;charset=UTF-8"));
    }

    @Test
    void shouldOverrideAcceptHeaderWithFormatParameter() throws Exception {
        mockMvc.perform(get("/fhir/metadata?_format=json")
                .accept("application/fhir+xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json;charset=UTF-8"));
    }

    @Test
    void shouldReturnXmlWhenRequestedViaFormatParameterRelateXml() throws Exception {
        mockMvc.perform(get("/fhir/metadata?_format=application/xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+xml;charset=UTF-8"));
    }

    @Test
    void shouldReturnXmlWhenRequestedViaFormatParameterTextXml() throws Exception {
        mockMvc.perform(get("/fhir/metadata?_format=text/xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+xml;charset=UTF-8"));
    }

    @Test
    void shouldReturnXmlWhenRequestedViaFormatParameterApplicationFhirXml() throws Exception {
        mockMvc.perform(get("/fhir/metadata?_format=application/fhir+xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+xml;charset=UTF-8"));
    }

    @Test
    void shouldReturnJsonWhenRequestedViaFormatParameterApplicationJson() throws Exception {
        mockMvc.perform(get("/fhir/metadata?_format=application/json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json;charset=UTF-8"));
    }

    @Test
    void shouldReturnJsonWhenRequestedViaFormatParameterApplicationFhirJson() throws Exception {
        mockMvc.perform(get("/fhir/metadata?_format=application/fhir+json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/fhir+json;charset=UTF-8"));
    }

    @Test
    void shouldReturnJsonWhenRequestedViaTextJson() throws Exception {
        // Spec: If a client provides a generic mime type ... server SHOULD respond with
        // the requested mime type ... using the XML or JSON formats
        // We will return application/fhir+json content, but with the requested
        // Content-Type header if possible, OR application/fhir+json.
        // Spring's behavior usually sets the Content-Type to what was requested if it's
        // in the supported media types.
        mockMvc.perform(get("/fhir/metadata")
                .accept("text/json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/json;charset=UTF-8"));
    }

    @Test
    void shouldReturnXmlWhenRequestedViaTextXml() throws Exception {
        mockMvc.perform(get("/fhir/metadata")
                .accept("text/xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/xml;charset=UTF-8"));
    }

    // Since we are strictly supporting FHIR formats, other formats might not be
    // supported directly by our converter
    // but standard Spring handling might apply. The requirement implies strict
    // negotiation.
    // However, if no handler supports text/plain, Spring usually returns 406.
    @Test
    void shouldReturn406ForUnsupportedAcceptHeader() throws Exception {
        mockMvc.perform(get("/fhir/metadata")
                .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void shouldReturn404JsonWhenRequestingNonExistentResourceWithHtmlAccept() throws Exception {
        // This simulates a browser request (Accept: text/html) to a non-existent
        // endpoint.
        // It should return 404 (not 406) and the OperationOutcome in
        // application/fhir+json.
        mockMvc.perform(get("/fhir/non-existent-resource")
                .accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/fhir+json;charset=UTF-8"));
    }
}
