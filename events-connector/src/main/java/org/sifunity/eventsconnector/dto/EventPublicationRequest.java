// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for event publication from providers.
 * Represents an event to be distributed to all matching subscriber queues.
 * 
 * @param serviceName Name of the service/object type (e.g., StudentPersonal) - required
 * @param zoneId Zone identifier - required
 * @param contextId Context identifier - optional, defaults to DEFAULT
 * @param messageId Unique message identifier - optional, generated if not provided
 * @param timestamp ISO-8601 timestamp - optional, set to current time if not provided
 * @param payload Event payload as JSON
 * @param headers Passthrough HTTP headers (sanitized, non-blacklisted) to include in the envelope
 */
public record EventPublicationRequest(
    @NotBlank(message = "serviceName is required")
    String serviceName,
    
    @NotBlank(message = "zoneId is required")
    String zoneId,
    
    String contextId,
    
    String messageId,
    
    Instant timestamp,
    
    @NotNull(message = "payload is required")
    JsonNode payload,
    
    Map<String, String> headers,
    
    UUID environmentId
) {
    /**
     * Canonical constructor with default value handling.
     * Sets contextId to DEFAULT if not provided.
     * Generates messageId if not provided.
     * Sets timestamp to current time if not provided.
     * Sets headers to empty map if not provided.
     */
    public EventPublicationRequest {
        if (contextId == null || contextId.isBlank()) {
            contextId = "DEFAULT";
        }
        if (messageId == null || messageId.isBlank()) {
            messageId = UUID.randomUUID().toString();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (headers == null) {
            headers = Collections.emptyMap();
        }
    }
}
