// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Response wrapper for a collection of alerts.
 * 
 * <p>Wraps the alert collection under an {@code alerts} key per SIF 3.7 convention.</p>
 * 
 * @param alerts the alert collection
 * @see AlertCollection
 */
public record AlertCollectionResponse(
    AlertCollection alerts
) {}
