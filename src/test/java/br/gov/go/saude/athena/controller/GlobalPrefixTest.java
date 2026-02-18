package br.gov.go.saude.athena.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GlobalPrefixTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAccessCapabilityStatementWithFhirPrefix() throws Exception {
        mockMvc.perform(get("/fhir/metadata")
                .accept("application/fhir+json"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldNotAccessCapabilityStatementWithoutFhirPrefix() throws Exception {
        mockMvc.perform(get("/metadata")
                .accept("application/fhir+json"))
                .andExpect(status().isNotFound());
    }
}
