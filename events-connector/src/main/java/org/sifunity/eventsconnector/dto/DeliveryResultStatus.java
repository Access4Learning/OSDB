// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.dto;

/**
 * Enum representing the delivery status for an individual queue.
 * Used in QueueDeliveryResult to indicate whether a specific queue
 * successfully received the event or failed.
 */
public enum DeliveryResultStatus {
    /**
     * The event was successfully enqueued to this queue.
     */
    SUCCESS,
    
    /**
     * The event failed to be enqueued to this queue.
     */
    FAILED
}
