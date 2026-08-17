// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import org.sifunity.broker.common.constant.Operation;

import java.util.List;

/**
 * The {@code operations} nesting from the SIF 3.7 schema (D1) — the operations a request may target.
 *
 * <p>Schema: {@code required: ["operation"]}, where {@code operation} is an array of strings. The
 * broker previously emitted a bare {@code Set<Operation>} in this position.</p>
 *
 * <p>A {@link List} rather than a {@code Set} because the schema types this as an array, and JSON
 * arrays are ordered. Preserving order makes the wire form stable across serializations, which
 * matters for the conformance tests and for anything diffing responses.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationsDTO(

    /** The operation list. Required to be present by the schema. */
    @NotNull(message = "operation array is required")
    List<Operation> operation
) {

    /**
     * Builds an {@code operations} wrapper from a plain collection, preserving iteration order.
     *
     * @param operations the operations, may be {@code null}
     * @return the wrapper, or {@code null} when {@code operations} is null
     */
    public static OperationsDTO of(java.util.Collection<Operation> operations) {
        return operations == null ? null : new OperationsDTO(List.copyOf(operations));
    }
}
