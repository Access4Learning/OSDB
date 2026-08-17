// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;

/**
 * Represents one entry in a provider's {@code supportedRequestTypes} array.
 *
 * <p>Maps to the SIF 3.7 {@code supportedRequest} schema object, which pairs a {@link RequestType}
 * (IMMEDIATE or DELAYED) with the operations the provider supports for that request type.</p>
 *
 * <h2>D1 reshape</h2>
 *
 * <p>Schema: {@code required: ["operations"]}, and {@code operations} is an <b>object</b> carrying an
 * {@code operation} array — not a bare array. The broker previously emitted
 * {@code "operations": ["QUERY"]} where the schema requires
 * {@code "operations": { "operation": ["QUERY"] }}.</p>
 *
 * <p>The nested form is the only way to express this type — there is no flat {@code Set}-based
 * alternative, so a caller cannot accidentally reconstruct the pre-D1 wire shape.</p>
 *
 * @param requestType the request delivery mode (IMMEDIATE or DELAYED)
 * @param operations  the nested operations wrapper
 * @see RequestType
 * @see Operation
 * @see OperationsDTO
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SupportedRequestType(

    /** The request delivery mode. Optional per the schema. */
    RequestType requestType,

    /** The supported operations, nested per the schema. Required to be present. */
    @NotNull(message = "operations is required")
    @Valid
    OperationsDTO operations
) {

}
