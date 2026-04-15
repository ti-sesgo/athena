package br.gov.go.saude.athena.service;

import br.gov.go.saude.athena.domain.PackageEntity;
import br.gov.go.saude.athena.domain.ValueSetEntity;
import br.gov.go.saude.athena.loader.ExtractedResource;
import br.gov.go.saude.athena.loader.ResourceExtractor;
import br.gov.go.saude.athena.repository.ValueSetRepository;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r4.model.ValueSet;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ValueSetLoaderServiceTest {

    @Mock
    private ResourceExtractor resourceExtractor;
    @Mock
    private ValueSetRepository valueSetRepository;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ExecutorService executor;
    private ValueSetLoaderService service;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        service = new ValueSetLoaderService(resourceExtractor, valueSetRepository, executor, transactionTemplate);
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<Object> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void shouldPersistValueSetWhenNotYetKnown() throws Exception {
        PackageEntity pkg = PackageEntity.builder().packageId("p").version("1").build();
        ValueSet vs = newValueSet("http://example.org/vs", "1.0.0");

        when(resourceExtractor.extractValueSets(any())).thenReturn(List.of(new ExtractedResource("{}".getBytes(), vs)));
        when(valueSetRepository.findByUrlAndVersionAndActiveTrue(vs.getUrl(), vs.getVersion())).thenReturn(Optional.empty());
        when(valueSetRepository.findByUrlAndIsLatestTrueAndActiveTrue(vs.getUrl())).thenReturn(Optional.empty());
        when(valueSetRepository.saveAndFlush(any(ValueSetEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.loadValueSets("pkg".getBytes(), pkg);

        verify(valueSetRepository, times(1)).saveAndFlush(any(ValueSetEntity.class));
    }

    @Test
    void shouldSkipValueSetWhenUrlAndVersionAlreadyPersisted() throws Exception {
        PackageEntity pkg = PackageEntity.builder().packageId("p").version("1").build();
        ValueSet vs = newValueSet("http://example.org/vs", "1.0.0");
        ValueSetEntity existing = ValueSetEntity.builder()
                .resourceId("vs")
                .url(vs.getUrl())
                .version(vs.getVersion())
                .status(PublicationStatus.ACTIVE)
                .content("{}".getBytes())
                .build();

        when(resourceExtractor.extractValueSets(any())).thenReturn(List.of(new ExtractedResource("{}".getBytes(), vs)));
        when(valueSetRepository.findByUrlAndVersionAndActiveTrue(vs.getUrl(), vs.getVersion())).thenReturn(Optional.of(existing));

        service.loadValueSets("pkg".getBytes(), pkg);

        verify(valueSetRepository, never()).saveAndFlush(any(ValueSetEntity.class));
    }

    private ValueSet newValueSet(String url, String version) {
        ValueSet vs = new ValueSet();
        vs.setId("vs");
        vs.setUrl(url);
        vs.setVersion(version);
        vs.setName("Example");
        vs.setTitle("Example ValueSet");
        vs.setStatus(PublicationStatus.ACTIVE);
        return vs;
    }
}
