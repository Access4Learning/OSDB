// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to create a zone with a zone ID
 * that already exists within a solution.
 * 
 * <p>Zone IDs must be unique within a solution. This exception is thrown
 * during zone creation when the provided zone ID conflicts with
 * an existing zone in the same solution.</p>
 * 
 * <p>This exception results in HTTP 409 Conflict response.</p>
 * 
 * @see org.sifunity.broker.environment.service.ZoneService#createZone
 */
public class DuplicateZoneException extends SifBrokerException {
    
    private static final String ERROR_CODE = "ZONE_002";
    
    /**
     * Constructs a new DuplicateZoneException with the specified zone ID.
     * 
     * @param zoneId the zone ID that already exists
     * @deprecated Use {@link #DuplicateZoneException(String, String)} instead to include solution context
     */
    @Deprecated
    public DuplicateZoneException(String zoneId) {
        super(
            ERROR_CODE,
            HttpStatus.CONFLICT,
            "Zone with ID '" + zoneId + "' already exists"
        );
    }
    
    /**
     * Constructs a new DuplicateZoneException with the specified solution ID and zone ID.
     * 
     * @param solutionId the solution ID where the zone already exists
     * @param zoneId the zone ID that already exists
     */
    public DuplicateZoneException(String solutionId, String zoneId) {
        super(
            ERROR_CODE,
            HttpStatus.CONFLICT,
            "Zone with ID '" + zoneId + "' already exists in solution '" + solutionId + "'"
        );
    }
    

}