// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.sifunity.broker.common.constant.CodeItemAction;

/**
 * DTO for a single code item within a code set.
 * 
 * <p>Maps to the SIF 3.7 code item schema with required fields
 * ({@code code}, {@code value}, {@code action}, {@code timestamp})
 * and optional fields ({@code source}, {@code namespace}, {@code description},
 * {@code definition}, {@code aliases}).</p>
 * 
 * @param code        the code value identifier
 * @param source      optional source URI for this code item
 * @param namespace   optional namespace for this code item
 * @param value       the human-readable value (max 128 characters)
 * @param description optional description (max 1024 characters)
 * @param definition  optional definition (max 4096 characters)
 * @param aliases     optional aliases for this code item
 * @param action      the action classification (ADD, CHANGE, DEPRECATED, DELETE)
 * @param timestamp   the timestamp of the last action
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CodeItemDTO(
    String code,
    String source,
    String namespace,
    String value,
    String description,
    String definition,
    AliasesDTO aliases,
    CodeItemAction action,
    String timestamp
) {}
