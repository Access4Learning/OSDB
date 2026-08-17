// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Request wrapper for alert creation.
 * 
 * <p>Wraps the alert creation body under an {@code alert} key,
 * matching the SIF 3.7 request envelope pattern.</p>
 * 
 * @param alert the alert creation body
 * @see AlertCreateBody
 */
public record AlertCreateRequest(
    AlertCreateBody alert
) {}
