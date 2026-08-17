// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Response wrapper for a single alert.
 * 
 * <p>Wraps the alert DTO under an {@code alert} key per SIF 3.7 convention.</p>
 * 
 * @param alert the alert data
 * @see AlertDTO
 */
public record AlertResponse(
    AlertDTO alert
) {}
