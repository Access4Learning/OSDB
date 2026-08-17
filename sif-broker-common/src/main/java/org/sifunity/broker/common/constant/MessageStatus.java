// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of message statuses in the SIF Unity Broker queue system.
 * 
 * <p>Message statuses track the delivery lifecycle of queued messages.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for message queue management.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum MessageStatus {
    /**
     * Waiting for delivery.
     * The message is in the queue and has not yet been delivered to the consumer.
     */
    PENDING,
    
    /**
     * Delivered to consumer.
     * The message has been delivered to the consumer but not yet acknowledged.
     */
    DELIVERED,
    
    /**
     * Acknowledged by consumer.
     * The consumer has acknowledged receipt and processing of the message.
     */
    ACKNOWLEDGED,
    
    /**
     * Failed after max retries.
     * The message delivery failed after exhausting all retry attempts.
     */
    FAILED
}
