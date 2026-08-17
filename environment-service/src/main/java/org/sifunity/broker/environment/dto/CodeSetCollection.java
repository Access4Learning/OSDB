// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.List;

/**
 * Collection of code set DTOs for list responses.
 * 
 * <p>Uses {@code codeSet} as the array key, consistent with the SifCollection
 * pattern used throughout the broker.</p>
 * 
 * @param codeSet list of code set DTOs
 * @see CodeSetDTO
 */
public record CodeSetCollection(
    List<CodeSetDTO> codeSet
) {}
