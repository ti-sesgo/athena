package br.gov.go.saude.athena.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.gov.go.saude.athena.service.PackageLoaderService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CapabilityStatementControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private PackageLoaderService packageLoaderService;

        @Test
        void shouldReturnCapabilityStatementInternal() throws Exception {
                mockMvc.perform(get("/fhir/metadata")
                                .accept("application/fhir+json"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType("application/fhir+json"))
                                .andExpect(jsonPath("$.resourceType").value("CapabilityStatement"));
        }

        @Test
        void shouldReturn405WhenMethodNotAllowed() throws Exception {
                mockMvc.perform(post("/fhir/metadata")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isMethodNotAllowed())
                                .andExpect(header().exists("Allow"))
                                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                                .andExpect(jsonPath("$.issue[0].severity").value("error"))
                                .andExpect(jsonPath("$.issue[0].code").value("not-supported")) // or "processing"
                                                                                               // depending on HAPI
                                                                                               // default enum mapping,
                                                                                               // let's allow
                                                                                               // flexibility if needed
                                                                                               // or stick to our code
                                                                                               // set
                                .andExpect(jsonPath("$.issue[0].details.text").value("Method not allowed."));
        }
}
