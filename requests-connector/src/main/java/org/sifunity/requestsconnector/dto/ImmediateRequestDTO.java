// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.dto;

import lombok.Data;

/**
 * DTO for IMMEDIATE (synchronous) request pattern.
 * 
 * This DTO represents a consumer request that expects an immediate synchronous response.
 * The request is forwarded to a provider endpoint and the response is returned in the same HTTP connection.
 * 
 * The serviceName comes from the path variable, and serviceType/messageType can be set by the controller
 * based on headers or defaults. The request body is just the payload.
 * 
 * Validates Requirements 7.1, 15.3
 */
@Data
public class ImmediateRequestDTO {
    
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
