// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.SupportedRequestType;

/**
 * Request DTO for registering a provider service (D1 / requirements 2.1-2.6, 3.1-3.3).
 *
 * <p>The body is <b>wrapped under the {@code provider} key</b>, per the SIF 3.7 envelope convention
 * already used by {@code environment}, {@code subscription} and {@code xquery}. Jackson deserializes
 * records through the <b>canonical</b> constructor, so the single component below is the whole wire
 * contract — clients must send the wrapper.</p>
 *
 * <p>Everything about the registration itself, including which of the two service-identification forms
 * is in play and the known {@code providerName} gap, is documented on
 * {@link ProviderRegistrationBody}.</p>
 *
 * @param provider the wrapped registration body
 *
 * @see ProviderRegistrationBody
 * @see ServiceType
 * @see SupportedRequestType
 * @see org.sifunity.broker.common.dto.ProviderRegistrationDTO
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderRegistrationRequest(

    /**
     * The wrapped registration body.
     *
     * <p>{@code @Valid} is required for {@link ProviderRegistrationBody}'s constraints to apply at all;
     * without it the nested {@code @NotNull} and {@code @AssertTrue} checks are inert.</p>
     */
    @NotNull(message = "Request body must contain the \"provider\" wrapper key per SIF 3.7 envelope convention")
    @Valid
    ProviderRegistrationBody provider
) {
}
