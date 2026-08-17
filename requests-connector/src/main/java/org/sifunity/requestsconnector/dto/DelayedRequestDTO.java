// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.dto;

import lombok.Data;

/**
 * DTO for DELAYED (asynchronous) request pattern.
 * 
 * This DTO represents a consumer request that will be processed asynchronously.
 * The request is either enqueued to a provider's request queue (DELAYED_to_DELAYED)
 * or stored for background proxy processing (DELAYED_to_IMMEDIATE).
 * The consumer receives an HTTP 202 Accepted response immediately with a messageId,
 * and the actual response is delivered to the consumer's queue later.
 * 
 * The serviceName comes from the path variable, and serviceType/messageType can be set by the controller
 * based on headers or defaults. The request body is just the payload.
 * 
 * Validates Requirements 7.1, 7.3, 15.3
 */
@Data
public class DelayedRequestDTO {
    
    /**
     * The name of the service being requested (e.g., "StudentPersonal").
     * Set from the path variable by the controller.
     */
    private String serviceName;
    
    /**
     * The type of service (e.g., "OBJECT", "FUNCTION", "UTILITY").
     * Set by the controller based on headers or defaults.
     */
    private String serviceType;
    
    /**
     * The type of message (e.g., "REQUEST", "RESPONSE", "EVENT").
     * Set by the controller based on headers or defaults.
     */
    private String messageType;
    
    /**
     * The request payload containing the actual data being requested.
     * This is the actual request body sent by the consumer.
     * This is preserved unchanged when forwarding to the provider.
     */
    private Object payload;
}
