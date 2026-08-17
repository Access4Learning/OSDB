// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response DTO for Named Query Template (xQueryTemplate) data.
 *
 * <p>This record represents the Named Query Template data returned by the
 * Environment Service. It is used for all template retrieval operations
 * including individual queries, listing by solution, and wrapping in
 * SIF envelope responses.</p>
 *
 * @param id          the query token (URL-addressable identifier for the query)
 * @param type        the query type: SINGULAR, FORMULA, or EXTENDED
 * @param status      the approval status: PENDING, APPROVED, or DISALLOWED
 * @param qualifier   optional reason/qualifier (e.g., reason if DISALLOWED)
 * @param description human-readable description of the query
 * @param script      the query script definition
 * @param parameters  list of parameter definitions for the query
 * @param returnType  URI string describing the return type
 *
 * @see XQueryParameterDTO
 * @see XQueryTemplateCreateRequest
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record XQueryTemplateDTO(
    /**
     * The query token — the URL-addressable identifier for this Named Query.
     */
    String id,

    /**
     * The query type. One of: SINGULAR, FORMULA, EXTENDED.
     */
    String type,

    /**
     * The approval status. One of: PENDING, APPROVED, DISALLOWED.
     */
    String status,

    /**
     * Optional reason or qualifier (e.g., reason if status is DISALLOWED).
     */
    String qualifier,

    /**
     * Human-readable description of the Named Query.
     */
    String description,

    /**
     * The query script definition.
     */
    String script,

    /**
     * List of parameter definitions that consumers supply when invoking the query.
     */
    /**
     * The template's parameters.
     *
     * <p>D6 / requirement 2.13: the schema nests these as an object with a {@code parameter} array,
     * not a bare array — see {@link XQueryParametersDTO}.</p>
     */
    XQueryParametersDTO parameters,

    /**
     * URI string describing the return type of the query.
     */
    String returnType
) {
}
