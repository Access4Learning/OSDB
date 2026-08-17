// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.dto;

/**
 * Enum representing the overall delivery status of an event distribution.
 * Used in EventDistributionResponse to indicate the outcome of distributing
 * an event to all matching subscriber queues.
 */
public enum DeliveryStatus {
    /**
     * All subscriber queues successfully received the event.
     */
    SUCCESS,
    
    /**
     * Some subscriber queues received the event, but others failed.
     */
    PARTIAL,
    
    /**
     * All subscriber queues failed to receive the event.
     */
    FAILED,
    
    /**
     * No subscriptions were found matching the event criteria.
     */
    NO_SUBSCRIBERS
}
