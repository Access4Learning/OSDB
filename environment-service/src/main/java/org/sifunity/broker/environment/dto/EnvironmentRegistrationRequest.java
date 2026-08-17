// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.sifunity.broker.common.constant.EnvironmentType;

/**
 * Request DTO for environment registration (D2 / requirements 2.7, 2.8).
 *
 * <p>The body is <b>wrapped under the {@code environment} key</b>, per the SIF 3.7 envelope
 * convention already used by {@code provider}, {@code subscription} and {@code xquery}:</p>
 *
 * <pre>{@code
 * {
 *   "environment": {
 *     "consumerName": "Example Consumer",
 *     "type": "DIRECT",
 *     "defaultZone": { "id": "district-zone" },
 *     "applicationInfo": { ... }
 *   }
 * }
 * }</pre>
 *
 * <p>Jackson deserializes records through the <b>canonical</b> constructor, so the single-component
 * constructor below is the whole contract — clients must send the wrapper, and the wrapper is equally
 * the only way to build this type from Java.</p>
 *
 * @param environment the wrapped registration body
 *
 * @see EnvironmentRegistrationBody
 * @see EnvironmentType
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnvironmentRegistrationRequest(

    /**
     * The wrapped registration body.
     *
     * <p>{@code @Valid} is required for {@link EnvironmentRegistrationBody}'s constraints to apply at
     * all; without it the nested {@code @NotBlank}/{@code @NotNull} are inert.</p>
     */
    @NotNull(message = "Request body must contain the \"environment\" wrapper key per SIF 3.7 envelope convention")
    @Valid
    EnvironmentRegistrationBody environment
) {

}
