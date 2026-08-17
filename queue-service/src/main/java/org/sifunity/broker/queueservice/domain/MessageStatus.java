// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.domain;

/**
 * Enum representing the status of a message in the queue system.
 */
public enum MessageStatus {
    /**
     * Message is pending delivery to a consumer
     */
    PENDING,
    
    /**
     * Message has been delivered to a consumer but not yet acknowledged
     */
    DELIVERED,
    
    /**
     * Message has been acknowledged by the consumer and successfully processed
     */
    ACKNOWLEDGED,
    
    /**
     * Message failed after maximum retry attempts and moved to dead-letter queue
     */
    FAILED
}
