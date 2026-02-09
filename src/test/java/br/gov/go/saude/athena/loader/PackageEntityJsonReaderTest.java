package br.gov.go.saude.athena.loader;

import br.gov.go.saude.athena.util.Util;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para PackageJsonReader e PackageMetadata.
 */
class PackageEntityJsonReaderTest {

  @Test
  void deveParsearPackageJsonCompleto() throws Exception {
    byte[] packageBytes = Util.loadResource("package.tgz");

    PackageMetadata metadata = PackageJsonReader.readMetadata(packageBytes);

    assertNotNull(metadata);
    assertEquals("br.go.ses.seguranca", metadata.name());
    assertEquals("0.1.2", metadata.version());
    assertEquals("SES-GO", metadata.author());
    assertEquals("4.0.1", metadata.dependencies().get("hl7.fhir.r4.core"));
  }

  @Test
  void deveParsearPackageJsonBasicInfo() throws Exception {
    byte[] packageBytes = Util.loadResource("package.tgz");

    var basicInfo = PackageJsonReader.readBasicInfo(packageBytes);

    assertNotNull(basicInfo);
    assertEquals("br.go.ses.seguranca", basicInfo.name());
    assertEquals("0.1.2", basicInfo.version());
  }


  @Test
  void deveLancarExcecaoParaPackageInvalido() {
    byte[] invalidPackage = "invalid".getBytes();

    assertThrows(IOException.class, () -> PackageJsonReader.readBasicInfo(invalidPackage));
    assertThrows(IOException.class, () -> PackageJsonReader.readMetadata(invalidPackage));
  }
}
