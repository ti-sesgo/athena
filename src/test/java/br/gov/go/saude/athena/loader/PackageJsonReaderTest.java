package br.gov.go.saude.athena.loader;

import br.gov.go.saude.athena.util.Util;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para PackageJsonReader e PackageMetadata.
 */
class PackageJsonReaderTest {

  @Test
  void parseMetadataValid() {
    String packageJson = """
        {
          "version" : "1.0.0",
          "tools-version" : 3,
          "type" : "IG",
          "date" : "20251124184833",
          "license" : "CC0-1.0",
          "canonical" : "http://www.saude.gov.br/fhir/r4",
          "url" : "https://fhir.saude.go.gov.br/r4/reds-go",
          "title" : "REDS GO",
          "description" : "NPM Package contendo artefatos de conformidade FHIR da Rede Nacional de Dados em Saúde (RNDS) do Brasil, ajustados para compatibilidade técnica com FHIR R4 sem alteração semântica. (built Mon, Nov 24, 2025 18:48+0000+00:00)",
          "fhirVersions" : ["4.0.1"],
          "dependencies" : {
            "hl7.fhir.r4.core" : "4.0.1",
            "hl7.terminology.r4" : "6.5.0",
            "hl7.fhir.uv.extensions.r4" : "5.2.0"
          },
          "author" : "SES-GO",
          "maintainers" : [
            {
              "name" : "SES-GO",
              "email" : "ti-ses.saude@goias.gov.br",
              "url" : "https://fhir.saude.go.gov.br"
            }
          ],
          "directories" : {
            "lib" : "package",
            "example" : "example"
          },
          "jurisdiction" : "urn:iso:std:iso:3166#BR",
          "name" : "br.go.ses.reds-go.r4"
        }

        """;

    var metadata = PackageJsonReader.parseMetadata(packageJson);

    assertEquals("br.go.ses.reds-go.r4", metadata.name());
    assertEquals("1.0.0", metadata.version());
    assertEquals(3, metadata.toolsVersion());
    assertEquals("IG", metadata.type());
    assertEquals("20251124184833", metadata.date());
    assertEquals("CC0-1.0", metadata.license());
    assertEquals("http://www.saude.gov.br/fhir/r4", metadata.canonical());
    assertEquals("https://fhir.saude.go.gov.br/r4/reds-go", metadata.url());
    assertEquals("REDS GO", metadata.title());
    assertTrue(metadata.description().startsWith("NPM Package contendo artefatos"));

    assertEquals(1, metadata.fhirVersions().size());
    assertEquals("4.0.1", metadata.fhirVersions().get(0));

    assertEquals(3, metadata.dependencies().size());
    assertEquals("4.0.1", metadata.dependencies().get("hl7.fhir.r4.core"));

    assertEquals("SES-GO", metadata.author());

    assertEquals(1, metadata.maintainers().size());
    assertEquals("SES-GO", metadata.maintainers().get(0).name());

    assertNotNull(metadata.directories());
    assertEquals("package", metadata.directories().lib());

    assertEquals("urn:iso:std:iso:3166#BR", metadata.jurisdiction());
  }

  @Test
  void parseBasicInfoValid() {
    String packageJson = """
        {
          "version" : "1.0.0",
          "canonical" : "http://www.saude.gov.br/fhir/r4",
          "title" : "REDS GO",
          "fhirVersions" : ["4.0.1"],
          "author" : "SES-GO",
          "name" : "br.go.ses.reds-go.r4"
        }
        """;

    var basicInfo = PackageJsonReader.parseBasicInfo(packageJson);

    assertEquals("br.go.ses.reds-go.r4", basicInfo.name());
    assertEquals("1.0.0", basicInfo.version());
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
