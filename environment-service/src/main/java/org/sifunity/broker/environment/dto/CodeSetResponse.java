// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Response wrapper for a single code set.
 * 
 * <p>Wraps the code set DTO under a {@code codeSet} key per SIF 3.7 convention.</p>
 * 
 * @param codeSet the code set data
 * @see CodeSetDTO
 */
public record CodeSetResponse(
    CodeSetDTO codeSet
) {}
