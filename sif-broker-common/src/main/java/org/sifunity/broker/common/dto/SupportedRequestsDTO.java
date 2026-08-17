// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The {@code supportedRequestTypes} nesting from the SIF 3.7 schema (D1).
 *
 * <p>Schema: {@code required: ["supportedRequestType"]}, where {@code supportedRequestType} is an
 * array of {@code supportedRequest}. The broker previously emitted a bare array in this position.</p>
 *
 * <p>Note the naming asymmetry, which is the schema's and not a mistake here: the <em>property</em> on
 * {@code provider} is plural ({@code supportedRequestTypes}) while the array <em>inside</em> it is
 * singular ({@code supportedRequestType}).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SupportedRequestsDTO(

    /** The supported-request list. Required to be present by the schema. */
    @NotNull(message = "supportedRequestType array is required")
    @Valid
    List<SupportedRequestType> supportedRequestType
) {

    /**
     * Builds the wrapper from a plain list.
     *
     * @param types the supported request types, may be {@code null}
     * @return the wrapper, or {@code null} when {@code types} is null
     */
    public static SupportedRequestsDTO of(List<SupportedRequestType> types) {
        return types == null ? null : new SupportedRequestsDTO(types);
    }
}
