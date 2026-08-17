// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for an alias code object within a code item alias.
 * 
 * <p>Maps to the SIF 3.7 alias {@code code} structure containing
 * an optional {@code old} flag, {@code official} flag, and {@code value}.</p>
 * 
 * @param old      whether this is an old/legacy code value
 * @param official whether this is the official code value
 * @param value    the alias code value
 */
public record AliasCodeDTO(
    @JsonProperty("old") Boolean old,
    Boolean official,
    String value
) {}
