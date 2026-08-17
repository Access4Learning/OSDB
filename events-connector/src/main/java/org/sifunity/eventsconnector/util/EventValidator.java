// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.util;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.eventsconnector.exception.EventValidationException;
import org.springframework.stereotype.Component;

/**
 * Utility class for validating event publication requests.
 * Validates required headers and payload format.
 * 
 * Requirements: 6 (Event Validation)
 */
@Component
@Slf4j
public class EventValidator {
    
    private static final String DEFAULT_CONTEXT_ID = "DEFAULT";
    
    /**
     * Validates that serviceName is not blank.
     * 
     * @param serviceName the service name to validate
     * @throws EventValidationException if serviceName is null or blank
     */
    public void validateServiceName(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            log.error("Validation failed: serviceName is required");
            throw new EventValidationException("serviceName header is required and cannot be blank");
        }
    }
    
    /**
     * Validates that zoneId is not blank.
     * 
     * @param zoneId the zone ID to validate
     * @throws EventValidationException if zoneId is null or blank
     */
    public void validateZoneId(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            log.error("Validation failed: zoneId is required");
            throw new EventValidationException("zoneId header is required and cannot be blank");
        }
    }
    
    /**
     * Validates and normalizes contextId.
     * If contextId is null or blank, returns DEFAULT.
     * 
     * @param contextId the context ID to validate
     * @return the validated context ID or DEFAULT if not provided
     */
    public String validateAndNormalizeContextId(String contextId) {
        if (contextId == null || contextId.isBlank()) {
            log.debug("contextId not provided, using DEFAULT");
            return DEFAULT_CONTEXT_ID;
        }
        return contextId;
    }
    
    /**
     * Validates that the event payload is valid JSON.
     * 
     * @param payload the JSON payload to validate
     * @throws EventValidationException if payload is null or not valid JSON
     */
    public void validatePayload(JsonNode payload) {
        if (payload == null) {
            log.error("Validation failed: event payload is required");
            throw new EventValidationException("Event payload is required and cannot be null");
        }
        
        if (payload.isMissingNode() || payload.isNull()) {
            log.error("Validation failed: event payload is missing or null");
            throw new EventValidationException("Event payload must be valid JSON");
        }
    }
    
    /**
     * Validates all required event fields.
     * 
     * @param serviceName the service name
     * @param zoneId the zone ID
     * @param contextId the context ID (will be normalized to DEFAULT if blank)
     * @param payload the event payload
     * @return the normalized context ID
     * @throws EventValidationException if any validation fails
     */
    public String validateEvent(String serviceName, String zoneId, String contextId, JsonNode payload) {
        validateServiceName(serviceName);
        validateZoneId(zoneId);
        validatePayload(payload);
        return validateAndNormalizeContextId(contextId);
    }
}
