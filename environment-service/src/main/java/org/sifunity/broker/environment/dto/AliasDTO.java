// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for a single alias within a code item.
 * 
 * <p>Maps to the SIF 3.7 alias structure containing a {@code code} object
 * and optional {@code source} and {@code namespace} fields.</p>
 * 
 * @param code      the alias code object
 * @param source    optional source URI for this alias
 * @param namespace optional namespace for this alias
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AliasDTO(
    AliasCodeDTO code,
    String source,
    String namespace
) {}
