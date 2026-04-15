package br.gov.go.saude.athena.service;

import br.gov.go.saude.athena.domain.CodeSystemEntity;
import br.gov.go.saude.athena.domain.ConceptEntity;
import br.gov.go.saude.athena.domain.PackageEntity;
import br.gov.go.saude.athena.loader.ExtractedResource;
import br.gov.go.saude.athena.loader.ResourceExtractor;
import br.gov.go.saude.athena.repository.CodeSystemRepository;
import br.gov.go.saude.athena.repository.ConceptRepository;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CodeSystemLoaderServiceTest {

    @Mock
    private ResourceExtractor resourceExtractor;
    @Mock
    private CodeSystemRepository codeSystemRepository;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ExecutorService executor;
    private CodeSystemLoaderService service;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        service = new CodeSystemLoaderService(
                resourceExtractor, codeSystemRepository, conceptRepository, executor, transactionTemplate);
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<Object> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void shouldAttachConceptsToExistingEntityWhenFragmentAlreadyPersistedForSameUrlAndVersion() throws Exception {
        String url = "http://hl7.org/fhir/sid/icd-9-cm";
        String version = "3.0.0";
        PackageEntity pkg = PackageEntity.builder().packageId("hl7.terminology.r4").version("6.5.0").build();
        CodeSystemEntity existing = CodeSystemEntity.builder()
                .resourceId("icd-9-cm")
                .url(url)
                .version(version)
                .status(PublicationStatus.ACTIVE)
                .content("{}".getBytes())
                .packageEntityRef(pkg)
                .isLatest(true)
                .active(true)
                .build();

        CodeSystem fragment = newFragment(url, version, "E800", "Acidente em aeronave de passageiros");

        when(resourceExtractor.extractCodeSystems(any())).thenReturn(List.of(new ExtractedResource("{}".getBytes(), fragment)));
        when(codeSystemRepository.findByUrlAndVersionAndActiveTrue(url, version)).thenReturn(Optional.of(existing));

        List<ConceptEntity> savedSnapshot = new java.util.ArrayList<>();
        when(conceptRepository.saveAll(any())).thenAnswer(inv -> {
            Iterable<ConceptEntity> arg = inv.getArgument(0);
            arg.forEach(savedSnapshot::add);
            return savedSnapshot;
        });

        service.loadCodeSystems("pkg-bytes".getBytes(), pkg);

        verify(codeSystemRepository, never()).saveAndFlush(any());
        verify(conceptRepository, times(1)).saveAll(any());
        assertEquals(1, savedSnapshot.size());
        assertEquals("E800", savedSnapshot.get(0).getCode());
        assertEquals(existing, savedSnapshot.get(0).getCodeSystem());
    }

    @Test
    void shouldPersistNewEntityWhenFragmentHasUnseenUrlAndVersion() throws Exception {
        String url = "http://hl7.org/fhir/sid/icd-9-cm";
        String version = "3.0.0";
        PackageEntity pkg = PackageEntity.builder().packageId("hl7.terminology.r4").version("6.5.0").build();
        CodeSystem fragment = newFragment(url, version, "E800", "Acidente em aeronave de passageiros");

        when(resourceExtractor.extractCodeSystems(any())).thenReturn(List.of(new ExtractedResource("{}".getBytes(), fragment)));
        when(codeSystemRepository.findByUrlAndVersionAndActiveTrue(url, version)).thenReturn(Optional.empty());
        when(codeSystemRepository.findByUrlAndIsLatestTrueAndActiveTrue(url)).thenReturn(Optional.empty());
        when(codeSystemRepository.saveAndFlush(any(CodeSystemEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.loadCodeSystems("pkg-bytes".getBytes(), pkg);

        verify(codeSystemRepository, times(1)).saveAndFlush(any(CodeSystemEntity.class));
        verify(conceptRepository, times(1)).saveAll(any());
    }

    private CodeSystem newFragment(String url, String version, String code, String display) {
        CodeSystem codeSystem = new CodeSystem();
        codeSystem.setId("icd-9-cm");
        codeSystem.setUrl(url);
        codeSystem.setVersion(version);
        codeSystem.setName("ICD-9-CM");
        codeSystem.setTitle("International Classification of Diseases, 9th Revision, Clinical Modification");
        codeSystem.setStatus(PublicationStatus.ACTIVE);
        codeSystem.setContent(CodeSystem.CodeSystemContentMode.FRAGMENT);
        codeSystem.addConcept().setCode(code).setDisplay(display);
        return codeSystem;
    }
}
