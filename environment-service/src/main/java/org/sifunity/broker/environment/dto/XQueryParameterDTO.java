// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object for a Named Query parameter definition.
 *
 * <p>Represents a single parameter that a consumer may (or must) supply
 * when invoking a Named Query (xQueryTemplate). Used in both create requests
 * and response DTOs.</p>
 *
 * @param name         the parameter name (used as the query-string key)
 * @param type         the SIF data type of the parameter (e.g., "xs:string", "xs:int")
 * @param defaultValue the default value if the consumer does not supply one (nullable)
 * @param description  human-readable description of the parameter (nullable)
 * @param required     whether the parameter is mandatory for invocation (nullable, defaults false)
 *
 * @see XQueryTemplateCreateRequest
 * @see XQueryTemplateDTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record XQueryParameterDTO(
    /**
     * The parameter name, used as the query-string key when invoking the query.
     *
     * <p>Required by the SIF schema (D6 / requirement 2.13).</p>
     */
    @NotBlank(message = "parameter name is required")
    String name,

    /**
     * The SIF data type of the parameter (e.g., "xs:string", "xs:int").
     */
    String type,

    /**
     * The default value if the consumer does not supply one. May be null.
     *
     * <p>Serialized as {@code default} per the SIF schema (D6 / requirement 2.13). {@code default} is
     * a Java reserved word and so cannot be the component name; the wire mapping is expressed with
     * {@code @JsonProperty} instead. Read it as {@code default} off the wire and
     * {@code defaultValue} in Java.</p>
     */
    @JsonProperty("default")
    String defaultValue,

    /**
     * Human-readable description of the parameter. May be null.
     */
    String description,

    /**
     * Whether the parameter is mandatory for invocation. May be null (defaults false).
     */
    Boolean required
) {
}
