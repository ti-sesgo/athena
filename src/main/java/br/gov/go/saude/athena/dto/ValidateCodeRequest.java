package br.gov.go.saude.athena.dto;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * DTO de entrada da operação FHIR CodeSystem $validate-code.
 * <p>
 * Spec: servidor não aceita parâmetro codeSystem ("Servers may choose not to accept").
 * </p>
 *
 * @see <a href="https://hl7.org/fhir/R4/operation-codesystem-validate-code.json">OperationDefinition</a>
 */
public record ValidateCodeRequest(
        String system,
        String code,
        String version,
        String display
) {

    /**
     * Verifica se o cliente enviou o parâmetro codeSystem.
     * Spec: "Servers may choose not to accept code systems in this fashion."
     */
    public static boolean hasCodeSystemParam(Parameters parameters) {
        if (parameters == null) return false;
        Parameters.ParametersParameterComponent p = parameters.getParameter("codeSystem");
        return p != null && p.getResource() != null;
    }

    /**
     * Extrai ValidateCodeRequest a partir de Parameters (POST).
     * Spec: um (e apenas um) de code+url, coding ou codeableConcept.
     * Ordem de extração: code+url → coding → codeableConcept.
     *
     * @param systemUrlFromInstance quando não nulo (nível instância), usa como url do CodeSystem em vez de extrair dos parâmetros
     */
    public static List<ValidateCodeRequest> fromParameters(Parameters parameters, String systemUrlFromInstance) {
        if (parameters == null) {
            return List.of();
        }

        String url;
        if (StringUtils.hasText(systemUrlFromInstance)) {
            url = systemUrlFromInstance;
        } else {
            url = paramValue(parameters, "url");
            if (!StringUtils.hasText(url)) {
                return List.of();
            }
        }

        int count = countValidOptions(parameters, url);
        if (count != 1) {
            return List.of();
        }

        var fromCodeAndUrl = extractFromCodeAndUrl(parameters, url);
        if (fromCodeAndUrl.isPresent()) return List.of(fromCodeAndUrl.get());

        var fromCoding = extractFromCoding(parameters, url);
        if (fromCoding.isPresent()) return List.of(fromCoding.get());

        return extractFromCodeableConcept(parameters, url);
    }

    /** Sobrecarga para chamada sem nível instância (url obrigatória nos parâmetros). */
    public static List<ValidateCodeRequest> fromParameters(Parameters parameters) {
        return fromParameters(parameters, null);
    }

    private static int countValidOptions(Parameters parameters, String url) {
        int count = 0;
        if (isValidCodeAndUrl(parameters)) count++;
        if (isValidCoding(parameters, url)) count++;
        if (isValidCodeableConcept(parameters, url)) count++;
        return count;
    }

    private static boolean isValidCodeAndUrl(Parameters parameters) {
        Parameters.ParametersParameterComponent codeParam = parameters.getParameter("code");

        return codeParam != null && codeParam.hasValue() && StringUtils.hasText(codeParam.getValue().primitiveValue());
    }

    private static boolean isValidCoding(Parameters parameters, String url) {
        Parameters.ParametersParameterComponent p = parameters.getParameter("coding");

        if (p == null || !(p.getValue() instanceof Coding c)) {
            return false;
        }

        return isValidCoding(c, url);
    }

    private static boolean isValidCoding(Coding coding, String url) {
        return coding != null && StringUtils.hasText(coding.getCode()) &&
                StringUtils.hasText(coding.getSystem()) &&
                coding.getSystem().equals(url);
    }

    private static ValidateCodeRequest fromCoding(Coding c) {
        return new ValidateCodeRequest(c.getSystem(), c.getCode(), c.getVersion(), c.getDisplay());
    }

    private static boolean isValidCodeableConcept(Parameters parameters, String url) {
        Parameters.ParametersParameterComponent p = parameters.getParameter("codeableConcept");
        if (p == null || !(p.getValue() instanceof CodeableConcept cc) || !cc.hasCoding()) {
            return false;
        }
        return cc.getCoding().stream().anyMatch(c -> isValidCoding(c, url));
    }

    private static Optional<ValidateCodeRequest> extractFromCoding(Parameters parameters, String url) {
        Parameters.ParametersParameterComponent p = parameters.getParameter("coding");
        if (p == null || !(p.getValue() instanceof Coding c)) {
            return Optional.empty();
        }
        return isValidCoding(c, url) ? Optional.of(fromCoding(c)) : Optional.empty();
    }

    private static List<ValidateCodeRequest> extractFromCodeableConcept(Parameters parameters, String url) {
        Parameters.ParametersParameterComponent p = parameters.getParameter("codeableConcept");
        if (p == null || !(p.getValue() instanceof CodeableConcept cc) || !cc.hasCoding()) {
            return List.of();
        }
        return cc.getCoding().stream()
                .filter(c -> isValidCoding(c, url))
                .map(ValidateCodeRequest::fromCoding)
                .toList();
    }

    private static Optional<ValidateCodeRequest> extractFromCodeAndUrl(Parameters parameters, String url) {
        Parameters.ParametersParameterComponent codeParam = parameters.getParameter("code");
        if (codeParam == null || !codeParam.hasValue()) {
            return Optional.empty();
        }
        String code = codeParam.getValue().primitiveValue();
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        String version = paramValue(parameters, "version");
        String display = paramValue(parameters, "display");
        return Optional.of(new ValidateCodeRequest(url, code, version, display));
    }

    private static String paramValue(Parameters parameters, String name) {
        Parameters.ParametersParameterComponent p = parameters.getParameter(name);
        if (p == null || !p.hasValue()) return null;
        String val = p.getValue().primitiveValue();
        return StringUtils.hasText(val) ? val : null;
    }
}
