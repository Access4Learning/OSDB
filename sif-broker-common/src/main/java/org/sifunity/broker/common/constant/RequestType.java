// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of request types for provider registrations.
 * 
 * <p>Models the SIF 3.7 {@code requestTypeValue} which defines how requests
 * are delivered to a provider. This is distinct from {@link MessageExchangePattern}
 * which also includes {@code EVENT} for the publish/subscribe pattern.
 * {@code RequestType} covers only the two request delivery modes.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for request type classification.</p>
 * 
 * @see MessageExchangePattern
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum RequestType {
    /**
     * Synchronous request/response pattern.
     * The consumer sends a request and waits for an immediate response.
     */
    IMMEDIATE,
    
    /**
     * Asynchronous request/response pattern via queues.
     * The consumer sends a request and receives the response later via a queue.
     */
    DELAYED
}
