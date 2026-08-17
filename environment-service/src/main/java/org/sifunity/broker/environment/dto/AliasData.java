// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Internal representation of a code item alias stored in the JSONB column.
 *
 * <p>Maps to the SIF 3.7 alias structure within a code item.
 * Each alias provides an alternate code representation with optional
 * source and namespace qualifiers.</p>
 *
 * @param code      the alias code object containing old/official flags and value
 * @param source    optional URI indicating the alias source
 * @param namespace optional namespace qualifier for the alias
 * @see AliasCodeData
 * @see CodeItemData
 */
public record AliasData(
    AliasCodeData code,
    String source,
    String namespace
) {}
