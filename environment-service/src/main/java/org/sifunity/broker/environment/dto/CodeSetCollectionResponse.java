// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Response wrapper for a collection of code sets.
 * 
 * <p>Wraps the code set collection under a {@code codeSets} key per SIF 3.7 convention.</p>
 * 
 * @param codeSets the code set collection
 * @see CodeSetCollection
 */
public record CodeSetCollectionResponse(
    CodeSetCollection codeSets
) {}
