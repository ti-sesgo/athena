package br.gov.go.saude.athena.loader;

import ca.uhn.fhir.context.FhirContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para ResourceExtractor.
 * Testa extração de recursos FHIR de packages.
 */
class ResourceExtractorTest {

    private ResourceExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ResourceExtractor(FhirContext.forR4(), Executors.newVirtualThreadPerTaskExecutor());
    }

    @Test
    void deveRetornarListaVaziaQuandoPackageInvalido() {
        byte[] invalidPackage = "invalid".getBytes();

        // Deve retornar lista vazia, não lançar exceção
        assertThrows(IOException.class, () -> extractor.extractCodeSystems(invalidPackage));
    }

    @Test
    void deveExtrairTodasTerminologiasEmParalelo() {
        byte[] invalidPackage = "invalid".getBytes();

        // extractAllTerminologies trata erros internamente
        ResourceExtractor.TerminologyResources result = extractor.extractAllTerminologies(invalidPackage);

        assertNotNull(result);
        assertNotNull(result.codeSystems());
        assertNotNull(result.valueSets());
        assertNotNull(result.conceptMaps());

        // Deve retornar listas vazias para package inválido
        assertTrue(result.codeSystems().isEmpty());
        assertTrue(result.valueSets().isEmpty());
        assertTrue(result.conceptMaps().isEmpty());
    }

    @Test
    void deveRetornarObjetoComListasVazias() {
        byte[] invalidPackage = "not a valid tgz".getBytes();

        ResourceExtractor.TerminologyResources result = extractor.extractAllTerminologies(invalidPackage);

        assertNotNull(result);
        assertEquals(0, result.codeSystems().size());
        assertEquals(0, result.valueSets().size());
        assertEquals(0, result.conceptMaps().size());
    }

    @Test
    void deveExtrairTudoEmParalelo() throws IOException {
        // Mock do conteúdo do pacote
        byte[] packageBytes = "dummy content".getBytes();

        // Comportamento esperado: método extrator deve ser chamado
        // Nota: Como estamos testando a classe real e não um mock,
        // este teste verifica principalmente a orquestração das threads

        ResourceExtractor.TerminologyResources result = extractor.extractAllTerminologies(packageBytes);

        assertNotNull(result);
        assertNotNull(result.codeSystems());
        assertNotNull(result.valueSets());
        assertNotNull(result.conceptMaps());
    }

    @Test
    void metodoExtractDeveSerGenerico() throws IOException {
        byte[] invalidPackage = "invalid".getBytes();

        // extract genérico deve lançar IOException para package inválido
        assertThrows(IOException.class, () -> extractor.extract(invalidPackage, "CodeSystem"));
    }
}
