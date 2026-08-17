// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

/**
 * The mutable part of a Named Query Template, wrapped under {@code xquery}.
 *
 * <p><b>Every field is optional</b>, which is deliberate and follows the specification: the
 * authoritative {@code jsonSchema_OpenAPI3.1_Update_IN.json} defines {@code xquery} with the same
 * properties as the create schema but with <em>no</em> {@code required} list, whereas the create
 * variant requires {@code type}, {@code status}, {@code script}, {@code returnType} and
 * {@code parameters}. An update sends only what changes; an omitted field is left alone.</p>
 *
 * <p>{@code id} is absent on purpose. The query token identifies the template and comes from the
 * path — renaming it would silently break every {@code context_services} row, provider registration
 * and consumer request bound to that name, since those are coupled to the template by name and not
 * by a foreign key. Deleting and recreating is the honest way to change a token.</p>
 *
 * <p>Distinguishing "omitted" from "set to empty" matters here, so the fields are compared against
 * {@code null} in {@code XQueryTemplateService.update} rather than being blank-checked: sending
 * {@code "qualifier": ""} clears the qualifier, while omitting it preserves the current value.
 * {@code returnType} is the exception — it is required on create, so clearing it is rejected rather
 * than honoured. See {@link #isReturnTypeAbsoluteUri()}.</p>
 *
 * @param type        the query type (SINGULAR, FORMULA, EXTENDED), or null to leave unchanged
 * @param status      the approval status (PENDING, APPROVED, DISALLOWED), or null to leave unchanged
 * @param qualifier   the reason when status is DISALLOWED, or null to leave unchanged
 * @param description free-text description, or null to leave unchanged
 * @param script      the query body, or null to leave unchanged
 * @param parameters  the nested parameter wrapper, or null to leave unchanged
 * @param returnType  the declared return type, or null to leave unchanged
 *
 * @see XQueryTemplateBody
 * @see XQueryTemplateUpdateRequest
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record XQueryTemplateUpdateBody(

    /** One of SINGULAR, FORMULA, EXTENDED. Validated by the service, which maps an unknown value to 400. */
    String type,

    /** One of PENDING, APPROVED, DISALLOWED. This is what an "approve" action changes. */
    String status,

    /** The reason, when status is DISALLOWED. */
    String qualifier,

    /** Free-text description shown to consumers browsing available queries. */
    String description,

    /** The query body. */
    String script,

    /** The parameters, nested per the schema as {@code {"parameter": [ ... ]}}. */
    @Valid
    XQueryParametersDTO parameters,

    /**
     * The fully qualified name of the root element returned.
     *
     * <p>When supplied it must be an absolute URI, on the same grounds as on create — an update is
     * just as capable of storing a value the broker cannot emit conformantly.</p>
     */
    String returnType
) {

    /**
     * Checks that {@code returnType} is an absolute URI when supplied.
     *
     * <p>Absence means "leave unchanged" here, so a null value passes. Shares the URI test with
     * {@link XQueryTemplateBody#isAbsoluteUri(String)} so create and update cannot drift.</p>
     *
     * <p>Blank is rejected, which is where this differs from create. On create a blank value is left
     * to {@code @NotBlank}; on update there is no {@code @NotBlank} to fall back on, and blank is not
     * the same as absent — {@code XQueryTemplateService.update} writes any non-null value, so
     * {@code "returnType": ""} would clear a field that is required on create and leave the template
     * emitting a value the broker cannot emit conformantly. That is reachable from the admin
     * console's edit panel, which sends the trimmed contents of the field whether or not it is
     * empty. Clearing is legitimate for {@code qualifier} and {@code description}; it is not for
     * {@code returnType}.</p>
     *
     * @return true when returnType is omitted, or is present and an absolute URI
     */
    @AssertTrue(message = "returnType must be an absolute URI, for example "
            + "http://www.sifassociation.org/datamodel/na/3.3:students")
    @JsonIgnore
    public boolean isReturnTypeAbsoluteUri() {
        return returnType == null
                || (!returnType.isBlank() && XQueryTemplateBody.isAbsoluteUri(returnType));
    }
}
