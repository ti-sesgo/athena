package br.gov.go.saude.athena.service;

import br.gov.go.saude.athena.domain.ConceptEntity;
import br.gov.go.saude.athena.repository.ConceptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.mockito.Mockito.*;

@SpringBootTest
class CacheIntegrationTest {

    @Autowired
    private CodeSystemService codeSystemService;

    @MockitoBean
    private ConceptRepository conceptRepository;

    @Test
    void shouldCacheFindConceptResult() {
        // Arrange
        String system = "http://test.com";
        String code = "123";
        ConceptEntity mockEntity = new ConceptEntity();
        mockEntity.setCode(code);

        when(conceptRepository.findByCodeSystemUrlAndCodeAndCodeSystemIsLatestTrueAndActiveTrue(system, code))
                .thenReturn(Optional.of(mockEntity));

        // Act 1 - Primeira chamada (deve bater no mock do repository)
        codeSystemService.lookup(system, code, null);

        // Act 2 - Segunda chamada (deve retornar do cache e não bater no repository)
        codeSystemService.lookup(system, code, null);

        // Assert - Verifica se o repositório foi chamado exatas 'UMA' vez
        verify(conceptRepository, times(1))
                .findByCodeSystemUrlAndCodeAndCodeSystemIsLatestTrueAndActiveTrue(system, code);
    }
}
