// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * The error detail carried by {@link InternalErrorRequest} on {@code POST /internal/responses/error}.
 *
 * <p>Field names follow {@link org.sifunity.broker.common.model.SifErrorResponse}: {@code code} is
 * the <b>HTTP status integer</b> and {@code subCode} is the internal error identifier. Getting these
 * two the wrong way round is the mistake this type exists to prevent — the previous wire contract
 * bound the whole body to a single {@code String}, so a JSON object arrived double-encoded as the
 * message text and the sender's code and scope were discarded in favour of a hardcoded
 * {@code INTERNAL_ERROR} / {@code BROKER} / 500.
 *
 * <p>Every field except {@code message} is optional. Absent values fall back to the previous
 * hardcoded defaults, so a sender that supplies only a message still produces a valid envelope.
 * Unknown fields are tolerated deliberately: this is an internal contract between two services that
 * are versioned independently, and a strict binding here turns a additive sender-side change into a
 * rejected error report — the error would be lost precisely when something is already wrong.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InternalErrorDetail(

    /** HTTP status integer (e.g. 502). Falls back to 500 when absent. */
    Integer code,

    /** Internal error identifier (e.g. {@code PROVIDER_ERROR}). Falls back to {@code INTERNAL_ERROR}. */
    String subCode,

    /** Human-readable description of what went wrong. Required. */
    @NotBlank(message = "Error message is required")
    String message,

    /** Optional longer-form detail. */
    String description,

    /** Error scope (e.g. {@code PROVIDER}, {@code BROKER}). Falls back to {@code BROKER}. */
    String scope
) {

    private static final int DEFAULT_CODE = 500;
    private static final String DEFAULT_SUB_CODE = "INTERNAL_ERROR";
    private static final String DEFAULT_SCOPE = "BROKER";

    /**
     * Tolerant creator, so a report from a <b>pre-fix</b> {@code requests-connector} is still
     * accepted during a rolling deploy.
     *
     * <p>The previous sender put the identifier in {@code code} as a <em>string</em>
     * ({@code "code": "PROVIDER_ERROR"}) and sent no {@code subCode}. Binding {@code code} strictly
     * as {@code Integer} rejects that with 400 — and
     * {@code ResponsesConnectorClient.routeErrorResponse} catches and merely logs, so the report
     * would vanish silently and the consumer's delayed request would never be told its provider
     * failed. Losing an error report is worse than accepting an awkward one.
     *
     * <p>So a non-numeric {@code code} is treated as the identifier it actually was and moved to
     * {@code subCode} (unless the sender supplied one), recovering the information instead of
     * discarding it. A numeric {@code code} — string or JSON number — is taken as the HTTP status.
     *
     * @param code       HTTP status, or (legacy) the identifier as a string
     * @param subCode    internal error identifier
     * @param message    human-readable description
     * @param description optional longer-form detail
     * @param scope      error scope
     * @return a normalized detail
     */
    @com.fasterxml.jackson.annotation.JsonCreator
    static InternalErrorDetail fromJson(
            @com.fasterxml.jackson.annotation.JsonProperty("code") Object code,
            @com.fasterxml.jackson.annotation.JsonProperty("subCode") String subCode,
            @com.fasterxml.jackson.annotation.JsonProperty("message") String message,
            @com.fasterxml.jackson.annotation.JsonProperty("description") String description,
            @com.fasterxml.jackson.annotation.JsonProperty("scope") String scope) {

        Integer numericCode = null;
        String resolvedSubCode = subCode;

        if (code instanceof Number number) {
            numericCode = number.intValue();
        } else if (code instanceof String text && !text.isBlank()) {
            try {
                numericCode = Integer.valueOf(text.trim());
            } catch (NumberFormatException notAStatus) {
                // Legacy shape: this was the identifier, not a status.
                if (resolvedSubCode == null || resolvedSubCode.isBlank()) {
                    resolvedSubCode = text.trim();
                }
            }
        }

        return new InternalErrorDetail(numericCode, resolvedSubCode, message, description, scope);
    }

    /** @return the HTTP status integer, or 500 when the sender did not supply one. */
    public int codeOrDefault() {
        return code != null ? code : DEFAULT_CODE;
    }

    /** @return the internal error identifier, or {@code INTERNAL_ERROR} when absent or blank. */
    public String subCodeOrDefault() {
        return (subCode != null && !subCode.isBlank()) ? subCode : DEFAULT_SUB_CODE;
    }

    /** @return the error scope, or {@code BROKER} when absent or blank. */
    public String scopeOrDefault() {
        return (scope != null && !scope.isBlank()) ? scope : DEFAULT_SCOPE;
    }

    /**
     * Convenience factory for a message-only error, preserving the pre-existing default classification.
     *
     * @param message human-readable description of the failure
     * @return a detail carrying {@code message} with 500 / {@code INTERNAL_ERROR} / {@code BROKER}
     */
    public static InternalErrorDetail ofMessage(String message) {
        return new InternalErrorDetail(null, null, message, null, null);
    }
}
