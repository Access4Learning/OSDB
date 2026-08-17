// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.List;

/**
 * Internal representation of a single code item stored in the JSONB column.
 *
 * <p>Maps to the SIF 3.7 {@code codeItem} schema object within a code set.
 * Field names use lowercase to match the SIF JSON serialization format.</p>
 *
 * @param code        the code identifier (required)
 * @param source      optional URI indicating the external source
 * @param namespace   optional namespace qualifier
 * @param value       the human-readable code value (required, max 128 chars)
 * @param description optional description (max 1024 chars)
 * @param definition  optional full definition text (max 4096 chars)
 * @param aliases     optional list of alias entries for this code item
 * @param action      the last action taken on this code item (ADD, CHANGE, DEPRECATED, DELETE)
 * @param timestamp   the timestamp of the last action (required)
 * @see CodeItemsWrapper
 * @see AliasData
 */
public record CodeItemData(
    String code,
    String source,
    String namespace,
    String value,
    String description,
    String definition,
    List<AliasData> aliases,
    String action,
    String timestamp
) {}
