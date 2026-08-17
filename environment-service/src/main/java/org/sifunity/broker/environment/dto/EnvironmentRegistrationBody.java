// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.sifunity.broker.common.constant.EnvironmentType;

/**
 * The inner body of an environment registration request (D2 / requirements 2.7, 2.8).
 *
 * <p>Carried under the {@code environment} key by {@link EnvironmentRegistrationRequest}.</p>
 *
 * <h2>What changed and why</h2>
 *
 * <p>Two schema mismatches are corrected here:</p>
 * <ul>
 *   <li>{@code applicationName} → <b>{@code consumerName}</b>. Note the <em>response</em>
 *       ({@link SifEnvironmentResponse}) has always used {@code consumerName}, so the request was the
 *       odd one out — this aligns the two rather than introducing a new name.</li>
 *   <li>{@code defaultZoneId} (a flat string) → <b>{@code defaultZone}</b> (an object carrying
 *       {@code id}), matching the response shape and the schema.</li>
 * </ul>
 *
 * <p>{@code solutionId} is accepted in the body but the <b>path value always wins</b> — see
 * {@code EnvironmentController.registerEnvironment}, which rebuilds the request with the path value.
 * That precedence is asserted by the preservation tests (requirement 3.5) and must not be
 * "simplified" away, because a body-supplied solutionId would otherwise let a caller register into a
 * solution its URL did not name.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnvironmentRegistrationBody(

    /**
     * The consumer/application name. Required.
     *
     * <p>Named {@code consumerName} per the schema; this was {@code applicationName} on the request
     * only, while the response already used {@code consumerName}.</p>
     */
    @NotBlank(message = "Consumer name is required")
    String consumerName,

    /** The environment type. Required. */
    @NotNull(message = "Environment type is required")
    EnvironmentType type,

    /**
     * The default zone, as an object carrying {@code id} — not a bare {@code defaultZoneId} string.
     *
     * <p>Optional: an environment may be registered without a default zone, in which case the key is
     * absent on the way back out too (D3 / requirement 2.9).</p>
     */
    @Valid
    DefaultZoneDTO defaultZone,

    /**
     * Accepted for compatibility, but the path {@code solutionId} takes precedence — see the class
     * javadoc.
     */
    String solutionId,

    /** Optional application metadata. */
    @Valid
    ApplicationInfoDTO applicationInfo
) {

    /**
     * Convenience accessor for the default zone's id, or {@code null} when no default zone was
     * supplied. Keeps call sites from having to null-check the wrapper themselves.
     *
     * @return the default zone id, or {@code null}
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String defaultZoneId() {
        return defaultZone == null ? null : defaultZone.id();
    }
}
