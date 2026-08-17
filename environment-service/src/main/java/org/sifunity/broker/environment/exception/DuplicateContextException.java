// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to create a context with a zone ID and context ID
 * combination that already exists in the system.
 * 
 * <p>The combination of zone ID and context ID must be unique across all contexts.
 * This exception is thrown during context creation when the provided zone ID and
 * context ID combination conflicts with an existing context.</p>
 * 
 * <p>This exception results in HTTP 409 Conflict response.</p>
 * 
 * @see org.sifunity.broker.environment.service.ZoneService#createContext
 */
public class DuplicateContextException extends SifBrokerException {
    
    private static final String ERROR_CODE = "ZONE_003";
    
    /**
     * Constructs a new DuplicateContextException with the specified zone ID and context ID.
     * 
     * @param zoneId the zone ID of the duplicate context
     * @param contextId the context ID that already exists within the zone
     */
    public DuplicateContextException(String zoneId, String contextId) {
        super(
            ERROR_CODE,
            HttpStatus.CONFLICT,
            "Context with ID '" + contextId + "' already exists in zone '" + zoneId + "'"
        );
    }
    
    /**
     * Constructs a new DuplicateContextException with the specified zone ID, context ID,
     * and request context.
     * 
     * @param zoneId the zone ID of the duplicate context
     * @param contextId the context ID that already exists within the zone
     * @param requestId the request ID for tracing
     */
    public DuplicateContextException(String zoneId, String contextId, String requestId) {
        super(
            ERROR_CODE,
            HttpStatus.CONFLICT,
            "Context with ID '" + contextId + "' already exists in zone '" + zoneId + "'",
            requestId,
            zoneId + ":" + contextId
        );
    }
}