package br.gov.go.saude.athena.loader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para LocalPackageSource usando package real.
 */
class LocalPackageEntitySourceTest {

    private static final String PACKAGE_PATH = "src/test/resources/package.tgz";

    @Test
    void deveExtrairMetadadosDoPackageReal() {
        Path packagePath = Paths.get(PACKAGE_PATH).toAbsolutePath();
        LocalPackageSource source = new LocalPackageSource(packagePath.toString());

        assertEquals("br.go.ses.seguranca", source.getPackageId());
        assertEquals("0.1.2", source.getVersion());
    }

    @Test
    void deveCarregarPackageReal() throws IOException {
        Path packagePath = Paths.get(PACKAGE_PATH).toAbsolutePath();
        LocalPackageSource source = new LocalPackageSource(packagePath.toString());

        byte[] loaded = source.load();

        assertNotNull(loaded);
        assertTrue(loaded.length > 70000);
    }

    @Test
    void deveLancarExcecaoSeArquivoNaoExiste() {
        LocalPackageSource source = new LocalPackageSource("/path/to/nonexistent.tgz");

        assertThrows(IOException.class, source::load);
    }
}
