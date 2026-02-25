package br.gov.go.saude.athena.dto;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;

/**
 * DTO de saída da operação FHIR CodeSystem $validate-code.
 * <p>
 * Conforme FHIR R4: result (1..1 boolean), message (0..1), display (0..1).
 * </p>
 *
 * @see <a href="https://hl7.org/fhir/R4/operation-codesystem-validate-code.json">OperationDefinition</a>
 */
public record ValidateCodeResult(
        boolean result,
        String message,
        String display
) {

    /**
     * Converte este resultado para o recurso FHIR Parameters (resposta da operação).
     */
    public Parameters toParameters() {
        Parameters parameters = new Parameters();
        parameters.addParameter("result", new BooleanType(result));
        if (message != null) {
            parameters.addParameter("message", new StringType(message));
        }
        if (display != null) {
            parameters.addParameter("display", new StringType(display));
        }
        return parameters;
    }
}
