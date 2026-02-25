package br.gov.go.saude.athena.service;

import br.gov.go.saude.athena.domain.CodeSystemEntity;
import br.gov.go.saude.athena.domain.ConceptEntity;
import br.gov.go.saude.athena.dto.ValidateCodeResult;
import br.gov.go.saude.athena.exception.ConceptNotFoundException;
import br.gov.go.saude.athena.repository.CodeSystemRepository;
import br.gov.go.saude.athena.repository.CodeSystemRepository.UrlVersionLatestProjection;
import br.gov.go.saude.athena.repository.ConceptRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.*;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeSystemService {

    private final CodeSystemRepository codeSystemRepository;
    private final ConceptRepository conceptRepository;
    private final FhirContext fhirContext;

    /**
     * Busca um CodeSystem pelo ID lógico ou ID interno e retorna como recurso HAPI.
     */
    public Optional<CodeSystem> findResourceById(String id) {
        return findById(id).map(this::parseEntity);
    }

    /**
     * Busca um CodeSystem pela URL canônica e retorna como recurso HAPI.
     */
    public Optional<CodeSystem> findResourceByUrl(String url) {
        return findByUrl(url).map(this::parseEntity);
    }

    private CodeSystem parseEntity(CodeSystemEntity entity) {
        return fhirContext.newJsonParser().parseResource(CodeSystem.class, new String(entity.getContent()));
    }

    /**
     * Busca um CodeSystem pelo ID lógico ou ID interno.
     */
    public Optional<CodeSystemEntity> findById(String id) {
        return codeSystemRepository.findByResourceIdAndIsLatestTrueAndActiveTrue(id)
                .or(() -> codeSystemRepository.findById(tryParseId(id)));
    }

    /**
     * Busca um CodeSystem pela URL canônica.
     */
    public Optional<CodeSystemEntity> findByUrl(String url) {
        return codeSystemRepository.findByUrlAndIsLatestTrueAndActiveTrue(url);
    }

    /**
     * Lista URLs distintas dos CodeSystems ativos.
     * Resultado em cache heap (carregado uma vez, reutilizado em respostas futuras).
     */
    @Cacheable(value = "codeSystem", key = "'urls'")
    public List<String> findDistinctUrlsByActiveTrue() {
        return codeSystemRepository.findDistinctUrlByActiveTrueOrderByUrl();
    }

    /**
     * Lista CodeSystems ativos com suas versões suportadas.
     * Cada entrada contém uri, versão e se é a versão padrão (isLatest).
     * Resultado em cache heap.
     *
     * @see <a href="https://hl7.org/fhir/R4/terminologycapabilities.html">TerminologyCapabilities.codeSystem</a>
     */
    @Cacheable(value = "codeSystem", key = "'withVersions'")
    public List<CodeSystemWithVersions> findCodeSystemsWithVersionsByActiveTrue() {
        List<UrlVersionLatestProjection> rows = codeSystemRepository.findByActiveTrueOrderByUrlAscVersionAsc();
        return groupByUrlWithVersions(rows);
    }

    private List<CodeSystemWithVersions> groupByUrlWithVersions(List<UrlVersionLatestProjection> rows) {
        Map<String, List<VersionInfo>> byUrl = new LinkedHashMap<>();
        for (var row : rows) {
            String url = row.getUrl();
            String version = row.getVersion();
            boolean isDefault = Boolean.TRUE.equals(row.getIsLatest()); // getiIsLatest pode ser null

            byUrl.computeIfAbsent(url, k -> new ArrayList<>())
                    .add(new VersionInfo(version, isDefault));
        }
        return byUrl.entrySet().stream()
                .map(e -> new CodeSystemWithVersions(e.getKey(), e.getValue()))
                .toList();
    }

    public record CodeSystemWithVersions(String uri, List<VersionInfo> versions) {}

    public record VersionInfo(String code, boolean isDefault) {}

    /**
     * Busca conceito por sistema e código (versão mais recente/aleatória ativa).
     */
    public Optional<ConceptEntity> findConcept(String system, String code) {
        return conceptRepository.findByCodeSystemUrlAndCodeAndCodeSystemIsLatestTrueAndActiveTrue(system, code);
    }

    /**
     * Busca conceito por sistema, código e versão.
     */
    public Optional<ConceptEntity> findConcept(String system, String code, String version) {
        return conceptRepository.findByCodeSystemUrlAndCodeAndCodeSystemVersionAndActiveTrue(system, code,
                version);
    }

    private Long tryParseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * Operação $lookup.
     * <p>
     * Busca os detalhes de um conceito e do CodeSystem.
     * </p>
     */
    @Cacheable(value = "concepts", key = "#system + '-' + #code + '-' + (#version ?: 'latest')")
    public Parameters lookup(String system, String code, String version) {
        Optional<ConceptEntity> concept;
        if (version != null && !version.isEmpty()) {
            concept = findConcept(system, code, version);
        } else {
            concept = findConcept(system, code);
        }

        if (concept.isEmpty()) {
            String diagnostic = "Unable to find code[" + code + "] in system[" + system + "]";
            if (version != null && !version.isEmpty()) {
                diagnostic += " version[" + version + "]";
            }
            throw new ConceptNotFoundException(diagnostic);
        }

        ConceptEntity p = concept.get();

        Parameters parameters = new Parameters();
        parameters.addParameter("name", new StringType(p.getCodeSystemName()));

        if (p.getCodeSystemVersion() != null) {
            parameters.addParameter("version", new StringType(p.getCodeSystemVersion()));
        }

        parameters.addParameter("display", new StringType(p.getDisplay()));

        if (p.getDefinition() != null) {
            parameters.addParameter("definition", new StringType(p.getDefinition()));
        }

        if (p.getProperty() != null) {
            for (var property : p.getProperty()) {
                parameters.addParameter()
                        .setName("property")
                        .setPart(toParametersParameter(property));
            }
        }

        if (p.getDesignation() != null) {
            for (var designation : p.getDesignation()) {
                parameters.addParameter()
                        .setName("designation")
                        .setPart(toParametersParameter(designation));
            }
        }

        return parameters;
    }

    /**
     * Operação $validate-code.
     * Valida se um código pertence ao CodeSystem (banco de dados).
     */
    public ValidateCodeResult validateCode(String system, String code, String version, String requestDisplay) {
        Optional<ConceptEntity> concept;
        if (StringUtils.hasText(version)) {
            concept = findConcept(system, code, version);
        } else {
            concept = findConcept(system, code);
        }

        if (concept.isEmpty()) {
            String diagnostic = "Unable to find code[" + code + "] in system[" + system + "]";
            if (StringUtils.hasText(version)) diagnostic += " version[" + version + "]";
            return new ValidateCodeResult(false, diagnostic, null);
        }

        String recommendedDisplay = concept.get().getDisplay();
        if (StringUtils.hasText(requestDisplay) && !requestDisplay.equals(recommendedDisplay)) {
            return new ValidateCodeResult(false,
                    "The display \"" + requestDisplay + "\" is incorrect.",
                    recommendedDisplay);
        }
        return new ValidateCodeResult(true, null, recommendedDisplay);
    }

    private List<Parameters.ParametersParameterComponent> toParametersParameter(
            CodeSystem.ConceptPropertyComponent property) {

        Parameters.ParametersParameterComponent parameterCode = new Parameters.ParametersParameterComponent();
        parameterCode.setName("code");
        parameterCode.setValue(new CodeType(property.getCode()));

        Parameters.ParametersParameterComponent parameterValue = new Parameters.ParametersParameterComponent();
        parameterValue.setName("value");
        parameterValue.setValue(property.getValue());

        List<Parameters.ParametersParameterComponent> part = new ArrayList<>();
        part.add(parameterCode);
        part.add(parameterValue);

        return part;
    }

    private List<Parameters.ParametersParameterComponent> toParametersParameter(
            CodeSystem.ConceptDefinitionDesignationComponent designation) {

        List<Parameters.ParametersParameterComponent> part = new ArrayList<>();

        if (designation.hasLanguage()) {
            Parameters.ParametersParameterComponent parameterLanguage = new Parameters.ParametersParameterComponent();
            parameterLanguage.setName("language");
            parameterLanguage.setValue(new CodeType(designation.getLanguage()));
            part.add(parameterLanguage);
        }

        if (designation.hasUse()) {
            Parameters.ParametersParameterComponent parameterUse = new Parameters.ParametersParameterComponent();
            parameterUse.setName("use");
            parameterUse.setValue(designation.getUse());
            part.add(parameterUse);
        }

        if (designation.hasValue()) {
            Parameters.ParametersParameterComponent parameterValue = new Parameters.ParametersParameterComponent();
            parameterValue.setName("value");
            parameterValue.setValue(new StringType(designation.getValue()));
            part.add(parameterValue);
        }

        return part;
    }
}
