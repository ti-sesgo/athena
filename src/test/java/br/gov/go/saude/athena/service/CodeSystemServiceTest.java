package br.gov.go.saude.athena.service;

import br.gov.go.saude.athena.domain.ConceptEntity;
import br.gov.go.saude.athena.dto.ValidateCodeResult;
import br.gov.go.saude.athena.repository.CodeSystemRepository;
import br.gov.go.saude.athena.repository.ConceptRepository;
import ca.uhn.fhir.context.FhirContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeSystemServiceTest {

    @Mock
    private CodeSystemRepository codeSystemRepository;
    @Mock
    private ConceptRepository conceptRepository;

    private CodeSystemService service;

    @BeforeEach
    void setUp() {
        service = new CodeSystemService(codeSystemRepository, conceptRepository, FhirContext.forR4());
    }

    @Test
    void shouldValidateDisplayCaseInsensitively() {
        ConceptEntity stored = ConceptEntity.builder().code("A90").display("Dengue Fever").build();
        when(conceptRepository.findByCodeSystemUrlAndCodeAndCodeSystemIsLatestTrueAndActiveTrue("sys", "A90"))
                .thenReturn(Optional.of(stored));

        ValidateCodeResult result = service.validateCode("sys", "A90", null, "dengue fever");

        assertTrue(result.result());
        assertEquals("Dengue Fever", result.display());
    }

    @Test
    void shouldValidateDisplayAfterTrimmingWhitespace() {
        ConceptEntity stored = ConceptEntity.builder().code("A90").display("Dengue Fever").build();
        when(conceptRepository.findByCodeSystemUrlAndCodeAndCodeSystemIsLatestTrueAndActiveTrue("sys", "A90"))
                .thenReturn(Optional.of(stored));

        ValidateCodeResult result = service.validateCode("sys", "A90", null, "   Dengue Fever   ");

        assertTrue(result.result());
    }

    @Test
    void shouldRejectDisplayWhenTextIsDifferent() {
        ConceptEntity stored = ConceptEntity.builder().code("A90").display("Dengue Fever").build();
        when(conceptRepository.findByCodeSystemUrlAndCodeAndCodeSystemIsLatestTrueAndActiveTrue("sys", "A90"))
                .thenReturn(Optional.of(stored));

        ValidateCodeResult result = service.validateCode("sys", "A90", null, "Something else");

        assertFalse(result.result());
        assertEquals("Dengue Fever", result.display());
    }
}
