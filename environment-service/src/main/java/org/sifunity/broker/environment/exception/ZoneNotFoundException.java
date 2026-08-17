// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to access a zone that does not exist.
 * 
 * <p>This exception is thrown when querying, updating, or deleting a zone
 * by zone ID and the zone cannot be found in the database. It is also thrown
 * when attempting to create a context in a non-existent zone.</p>
 * 
 * <p>This exception results in HTTP 404 Not Found response.</p>
 * 
 * @see org.sifunity.broker.environment.service.ZoneService
 */
public class ZoneNotFoundException extends SifBrokerException {
    
    private static final String ERROR_CODE = "ZONE_001";
    
    /**
     * Constructs a new ZoneNotFoundException with the specified zone ID.
     * 
     * @param zoneId the ID of the zone that was not found
     */
    public ZoneNotFoundException(String zoneId) {
        super(
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            "Zone with ID '" + zoneId + "' not found"
        );
    }
    
    /**
     * Constructs a new ZoneNotFoundException with the specified zone ID
     * and request context.
     * 
     * @param zoneId the ID of the zone that was not found
     * @param requestId the request ID for tracing
     */
    public ZoneNotFoundException(String zoneId, String requestId) {
        super(
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            "Zone with ID '" + zoneId + "' not found",
            requestId,
            zoneId
        );
    }
}