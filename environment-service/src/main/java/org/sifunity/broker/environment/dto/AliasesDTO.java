// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.List;

/**
 * DTO wrapper for a list of aliases within a code item.
 * 
 * <p>Matches the SIF 3.7 schema structure: {@code { "alias": [...] }}.</p>
 * 
 * @param alias list of alias DTOs
 * @see AliasDTO
 */
public record AliasesDTO(
    List<AliasDTO> alias
) {}
