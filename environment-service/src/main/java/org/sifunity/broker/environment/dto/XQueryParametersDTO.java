// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;

import java.util.List;

/**
 * The {@code parameters} nesting required by the SIF 3.7 XQuery template schema (D6 / requirement
 * 2.13).
 *
 * <p>The schema models this as an <em>object</em> containing a {@code parameter} array, not a bare
 * array:</p>
 *
 * <pre>{@code
 * "parameters": { "parameter": [ { "name": "...", "default": "..." } ] }
 * }</pre>
 *
 * <p>The broker previously emitted {@code "parameters": [ ... ]} directly. This is the same
 * singular-inside-plural idiom SIF uses throughout ({@code aliases.alias},
 * {@code supportedRequests.supportedRequestType}, {@code operations.operation}), so it is worth
 * matching rather than special-casing.</p>
 *
 * <p>An empty parameter list is represented as {@code new XQueryParametersDTO(List.of())} — an
 * <em>empty array</em>, not an omitted key. "This template takes no parameters" is a different
 * statement from "the parameters are unknown", and the preservation properties assert the
 * distinction.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record XQueryParametersDTO(

    /** The parameter list. Empty means "no parameters", not "unknown". */
    @Valid
    List<XQueryParameterDTO> parameter
) {
}
