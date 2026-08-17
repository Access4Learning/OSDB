// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new Named Query Template (xQueryTemplate).
 *
 * <p>D6 / requirement 2.12: the body is <b>wrapped under the {@code xquery} key</b>, per the SIF 3.7
 * envelope convention used by every other singular resource ({@code environment}, {@code provider},
 * {@code subscription}). The broker previously accepted an unwrapped body.</p>
 *
 * <pre>{@code
 * {
 *   "xquery": {
 *     "id": "studentsByGrade",
 *     "type": "SINGULAR",
 *     "status": "APPROVED",
 *     "script": "...",
 *     "returnType": "http://...",
 *     "parameters": { "parameter": [ { "name": "grade", "default": "9" } ] }
 *   }
 * }
 * }</pre>
 *
 * <p>The combination of id (queryToken) and solution must be unique; a duplicate yields 409.</p>
 *
 * @param xquery the template body — see {@link XQueryTemplateBody}
 *
 * @see XQueryTemplateBody
 * @see XQueryParametersDTO
 * @see XQueryTemplateDTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record XQueryTemplateCreateRequest(

    /**
     * The wrapped template body.
     *
     * <p>{@code @Valid} is required here for the nested constraints on {@link XQueryTemplateBody} to
     * be applied at all — the controller's {@code @RequestBody} previously carried no {@code @Valid},
     * so nothing in this contract was enforced.</p>
     */
    @NotNull(message = "Request body must contain the \"xquery\" wrapper key per SIF 3.7 envelope convention")
    @Valid
    XQueryTemplateBody xquery
) {
}
