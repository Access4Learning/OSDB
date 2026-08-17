// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.List;

/**
 * DTO wrapper for a list of code items within a code set.
 * 
 * <p>Matches the SIF 3.7 schema structure: {@code { "codeItem": [...] }}.</p>
 * 
 * @param codeItem list of code item DTOs
 * @see CodeItemDTO
 */
public record CodeItemsDTO(
    List<CodeItemDTO> codeItem
) {}
