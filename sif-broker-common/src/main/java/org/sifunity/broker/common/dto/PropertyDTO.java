// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * A single name/value property, per the SIF 3.7 {@code property} definition (D1).
 *
 * <p>Schema: {@code required: ["name"]}. Used inside {@link PropertiesDTO} to describe protocol
 * settings on a provider's {@code endPoint}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PropertyDTO(

    /** The property name. Required by the schema. */
    @NotBlank(message = "property name is required")
    String name,

    /** The property value. Optional. */
    String value
) {
}
