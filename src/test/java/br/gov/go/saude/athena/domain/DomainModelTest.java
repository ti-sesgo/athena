package br.gov.go.saude.athena.domain;

import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes funcionais do modelo de domínio.
 * Demonstra o fluxo completo de criação de entidades.
 */
class DomainModelTest {

        @Test
        void deveCriarPackageCompleto() {
                PackageEntity pkg = PackageEntity.builder()
                                .packageId("hl7.fhir.r4.core")
                                .version("4.0.1")
                                .registryUrl("https://packages.fhir.org")
                                .loadedAt(LocalDateTime.now())
                                .active(true)
                                .build();

                assertNotNull(pkg);
                assertEquals("hl7.fhir.r4.core", pkg.getPackageId());
                assertEquals("4.0.1", pkg.getVersion());
                assertTrue(pkg.getActive());
        }

        @Test
        void deveCriarCodeSystemComStatus() {
                PackageEntity pkg = PackageEntity.builder()
                                .packageId("test")
                                .version("1.0")
                                .loadedAt(LocalDateTime.now())
                                .active(true)
                                .build();

                CodeSystemEntity cs = CodeSystemEntity.builder()
                                .resourceId("loinc")
                                .url("http://loinc.org")
                                .version("2.75")
                                .name("LOINC")
                                .title("Logical Observation Identifiers Names and Codes")
                                .status(PublicationStatus.ACTIVE)
                                // .description("LOINC is a universal code system")
                                .content("{}".getBytes())
                                .packageEntityRef(pkg)
                                .isLatest(true)
                                .build();

                assertNotNull(cs);
                assertEquals("loinc", cs.getResourceId());
                assertEquals("http://loinc.org", cs.getUrl());
                assertEquals(PublicationStatus.ACTIVE, cs.getStatus());
                assertTrue(cs.getIsLatest());
        }

        @Test
        void deveCriarConceitoCompleto() {
                PackageEntity pkg = PackageEntity.builder()
                                .packageId("test")
                                .version("1.0")
                                .loadedAt(LocalDateTime.now())
                                .active(true)
                                .build();

                CodeSystemEntity cs = CodeSystemEntity.builder()
                                .resourceId("loinc")
                                .url("http://loinc.org")
                                .version("2.75")
                                .name("LOINC")
                                .status(PublicationStatus.ACTIVE)
                                .content("{}".getBytes())
                                .packageEntityRef(pkg)
                                .isLatest(true)
                                .build();

                ConceptEntity concept = ConceptEntity.builder()
                                .codeSystemUrl("http://loinc.org")
                                .code("1963-8")
                                .display("Bicarbonate [Moles/volume] in Serum")
                                // .definition("Bicarbonate measurement in serum")
                                // .designations("{\"designations\": []}")
                                // .properties("{\"COMPONENT\": \"Bicarbonate\"}")
                                .codeSystem(cs)
                                .active(true)
                                .build();

                assertNotNull(concept);
                assertEquals("http://loinc.org", concept.getCodeSystemUrl());
                assertEquals("1963-8", concept.getCode());
                assertEquals("Bicarbonate [Moles/volume] in Serum", concept.getDisplay());
                assertTrue(concept.getActive());
        }

        @Test
        void deveSimularFluxoCompletoDeLookup() {
                // 1. Package carregado
                PackageEntity pkg = PackageEntity.builder()
                                .packageId("hl7.fhir.r4.core")
                                .version("4.0.1")
                                .loadedAt(LocalDateTime.now())
                                .active(true)
                                .build();

                // 2. CodeSystem extraído
                CodeSystemEntity cs = CodeSystemEntity.builder()
                                .resourceId("loinc")
                                .url("http://loinc.org")
                                .version("2.75")
                                .name("LOINC")
                                .status(PublicationStatus.ACTIVE)
                                .content("{}".getBytes())
                                .packageEntityRef(pkg)
                                .isLatest(true)
                                .build();

                // 3. Conceito indexado
                ConceptEntity concept = ConceptEntity.builder()
                                .codeSystemUrl("http://loinc.org")
                                .code("1963-8")
                                .display("Bicarbonate [Moles/volume] in Serum")
                                .codeSystem(cs)
                                .active(true)
                                .build();

                // 4. Simula lookup: system + code → display
                String lookupSystem = "http://loinc.org";
                String lookupCode = "1963-8";

                if (concept.getCodeSystemUrl().equals(lookupSystem) &&
                                concept.getCode().equals(lookupCode) &&
                                concept.getActive()) {

                        String result = concept.getDisplay();
                        assertEquals("Bicarbonate [Moles/volume] in Serum", result);
                }
        }

        @Test
        void deveValidarStatusTransitions() {
                // DRAFT → ACTIVE
                CodeSystemEntity draft = CodeSystemEntity.builder()
                                .resourceId("test-cs")
                                .url("http://test.org")
                                .version("0.1")
                                .name("TestCS")
                                .status(PublicationStatus.DRAFT)
                                .content("{}".getBytes())
                                .isLatest(false)
                                .build();

                assertEquals(PublicationStatus.DRAFT, draft.getStatus());

                // Simula mudança para ACTIVE
                draft.setStatus(PublicationStatus.ACTIVE);
                draft.setIsLatest(true);

                assertEquals(PublicationStatus.ACTIVE, draft.getStatus());
                assertTrue(draft.getIsLatest());
        }
}
