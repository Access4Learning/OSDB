// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The inner body of an XQuery template create request (D6 / requirement 2.12).
 *
 * <p>Carried under the {@code xquery} key by {@link XQueryTemplateCreateRequest}. The broker
 * previously accepted an <em>unwrapped</em> body and applied no Bean Validation to it at all (the
 * controller's {@code @RequestBody} had no {@code @Valid}), so every field was effectively optional.</p>
 *
 * <p>Requirement 2.12 requires {@code type}, {@code status}, {@code script}, {@code returnType} and
 * {@code parameters}. {@code id}, {@code qualifier} and {@code description} stay optional.</p>
 *
 * <p>Note {@code parameters} is {@code @NotNull} rather than "non-empty": a template legitimately
 * takes no parameters, and that is expressed as {@code {"parameter": []}} — an empty array is a
 * meaningful answer, whereas an absent key is not. See {@link XQueryParametersDTO}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record XQueryTemplateBody(

    /** Optional; assigned by the broker when absent. */
    String id,

    /** The query type. One of: SINGULAR, FORMULA, EXTENDED. Required. */
    @NotBlank(message = "type is required")
    String type,

    /** The approval status. One of: PENDING, APPROVED, DISALLOWED. Required. */
    @NotBlank(message = "status is required")
    String status,

    /** Optional reason or qualifier (e.g. the reason when status is DISALLOWED). */
    String qualifier,

    /** Optional human-readable description. */
    String description,

    /** The query script. Required. */
    @NotBlank(message = "script is required")
    String script,

    /**
     * The template's parameters, nested per the schema. Required to be present; an empty
     * {@code parameter} array is valid and means "this template takes no parameters".
     */
    @NotNull(message = "parameters is required")
    @Valid
    XQueryParametersDTO parameters,

    /**
     * The declared return type — the fully qualified name of the root element returned. Required.
     *
     * <p>Must be an absolute URI; see {@link #isReturnTypeAbsoluteUri()}.</p>
     */
    @NotBlank(message = "returnType is required")
    String returnType
) {

    /**
     * Checks that {@code returnType} is an absolute URI.
     *
     * <p>The schema types it as {@code type: string, format: uri}, and its description is explicit
     * about intent: "The fully qualified name of the root element returned by this service. Example:
     * http://www.sifassociation.org/datamodel/na/3.3:students". A bare word such as {@code "void"}
     * parses only as a relative reference and is not a URI, so the broker must not store it — a
     * stored value that cannot be emitted conformantly turns one bad create into a permanently
     * nonconformant response on every subsequent read.</p>
     *
     * <p>Deliberately <em>not</em> restricted to http/https, unlike
     * {@link ProviderRegistrationBody#isEndPointLocationValid()}. That endpoint is a callback the
     * broker actually invokes, so a usable protocol matters. This is a namespace identifier the
     * broker never dereferences, and {@code urn:} forms are legitimate. Requiring only absoluteness
     * is what {@code format: uri} means, and matches how the conformance harness judges it.</p>
     *
     * <p>Expressed as {@code @AssertTrue} rather than {@code @Pattern} because URI syntax is not
     * usefully expressible as a regex, following the cross-field pattern already used in
     * {@link ProviderRegistrationBody}.</p>
     *
     * @return true when returnType is absent (left to {@code @NotBlank}) or is an absolute URI
     */
    @AssertTrue(message = "returnType must be an absolute URI, for example "
            + "http://www.sifassociation.org/datamodel/na/3.3:students")
    @JsonIgnore
    public boolean isReturnTypeAbsoluteUri() {
        return isAbsoluteUri(returnType);
    }

    /**
     * Shared absolute-URI check, also used by {@link XQueryTemplateUpdateBody}.
     *
     * @param value the candidate URI; null or blank returns true so that presence is enforced
     *     separately by {@code @NotBlank} on create and treated as "unchanged" on update
     * @return true when the value is absent or is an absolute URI
     */
    static boolean isAbsoluteUri(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            return new java.net.URI(value).isAbsolute();
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }
}
