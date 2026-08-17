// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of message exchange patterns supported by the SIF Unity Broker.
 * 
 * <p>Message exchange patterns define how messages are delivered between
 * consumers and providers in the broker system.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for message delivery patterns.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum MessageExchangePattern {
    /**
     * Synchronous request/response pattern.
     * The consumer sends a request and waits for an immediate response.
     */
    IMMEDIATE,
    
    /**
     * Asynchronous request/response pattern via queues.
     * The consumer sends a request and receives the response later via a queue.
     */
    DELAYED,
    
    /**
     * Publish/subscribe event pattern.
     * Providers publish events that are delivered to all subscribed consumers.
     */
    EVENT
}
