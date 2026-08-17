// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating a provider registration (D1).
 *
 * <p>The body is <b>wrapped under the {@code provider} key</b>, matching
 * {@link ProviderRegistrationRequest} and the SIF 3.7 envelope convention:</p>
 *
 * <pre>{@code
 * {
 *   "provider": {
 *     "endPoint": { "location": "https://sis.example.org/StudentPersonals" },
 *     "supportedRequestTypes": {
 *       "supportedRequestType": [
 *         { "requestType": "IMMEDIATE", "operations": { "operation": ["QUERY"] } }
 *       ]
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>This previously took a flat {@code endpoint} string and a bare
 * {@code List<SupportedRequestType>} while registration took the wrapped, nested form. Aligning them
 * matters more than either shape individually: a client should not need one mental model to create a
 * provider and a different one to amend it.</p>
 *
 * <p>Only the endpoint and supported request types are updatable — see {@link ProviderUpdateBody} for
 * why a provider's identity is fixed at creation.</p>
 *
 * @param provider the wrapped update body
 *
 * @see ProviderUpdateBody
 * @see ProviderRegistrationRequest
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderUpdateRequest(

    /**
     * The wrapped update body.
     *
     * <p>{@code @Valid} is required for {@link ProviderUpdateBody}'s constraints to apply at all;
     * without it the nested {@code @NotNull} and {@code @AssertTrue} checks are inert.</p>
     */
    @NotNull(message = "Request body must contain the \"provider\" wrapper key per SIF 3.7 envelope convention")
    @Valid
    ProviderUpdateBody provider
) {
}
