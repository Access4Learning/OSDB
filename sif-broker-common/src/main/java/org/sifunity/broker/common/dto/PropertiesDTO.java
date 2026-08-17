// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The {@code properties} nesting from the SIF 3.7 schema (D1).
 *
 * <p>Schema: {@code required: ["property"]}, where {@code property} is an array. This is the same
 * singular-inside-plural idiom as {@code aliases.alias}, {@code operations.operation} and
 * {@code supportedRequestTypes.supportedRequestType} — SIF uses it consistently, so it is worth
 * modelling the same way each time rather than flattening one of them.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PropertiesDTO(

    /** The property list. Required to be present by the schema. */
    @NotNull(message = "property array is required")
    @Valid
    List<PropertyDTO> property
) {
}
