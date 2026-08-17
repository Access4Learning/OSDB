// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating a Named Query Template (D6).
 *
 * <p>Wrapped under the {@code xquery} key, matching {@link XQueryTemplateCreateRequest} and the SIF
 * 3.7 envelope convention:</p>
 *
 * <pre>{@code
 * {
 *   "xquery": {
 *     "status": "APPROVED"
 *   }
 * }
 * }</pre>
 *
 * <p>Partial by design — see {@link XQueryTemplateUpdateBody}. Approving a template is just this
 * request carrying nothing but {@code status}.</p>
 *
 * @param xquery the wrapped update body
 *
 * @see XQueryTemplateUpdateBody
 * @see XQueryTemplateCreateRequest
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record XQueryTemplateUpdateRequest(

    /**
     * The wrapped update body.
     *
     * <p>{@code @Valid} is required for the nested constraints to apply at all; without it the
     * {@code @Valid} on {@code parameters} is inert.</p>
     */
    @NotNull(message = "Request body must contain the \"xquery\" wrapper key per SIF 3.7 envelope convention")
    @Valid
    XQueryTemplateUpdateBody xquery
) {
}
