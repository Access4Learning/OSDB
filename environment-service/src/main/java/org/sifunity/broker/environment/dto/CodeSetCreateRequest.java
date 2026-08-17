// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Request wrapper for code set creation.
 * 
 * <p>Wraps the code set creation body under a {@code codeSet} key,
 * matching the SIF 3.7 request envelope pattern.</p>
 * 
 * @param codeSet the code set creation body
 * @see CodeSetCreateBody
 */
public record CodeSetCreateRequest(
    CodeSetCreateBody codeSet
) {}
