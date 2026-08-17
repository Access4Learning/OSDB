// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Internal representation of an alias code object stored in the JSONB column.
 *
 * <p>Maps to the SIF 3.7 alias {@code code} structure. Contains flags
 * indicating whether the alias is an old or official code, plus the
 * alias value string.</p>
 *
 * @param old      whether this alias represents an old/deprecated code
 * @param official whether this alias is the official code
 * @param value    the alias code value string
 * @see AliasData
 */
public record AliasCodeData(
    Boolean old,
    Boolean official,
    String value
) {}
