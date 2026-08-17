// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

import java.util.UUID;

/**
 * Exception thrown when attempting to access an environment that does not exist.
 * 
 * <p>This exception is thrown when querying, updating, or deleting an environment
 * by ID and the environment cannot be found in the database.</p>
 * 
 * <p>This exception results in HTTP 404 Not Found response.</p>
 * 
 * @see org.sifunity.broker.environment.service.EnvironmentService
 */
public class EnvironmentNotFoundException extends SifBrokerException {
    
    private static final String ERROR_CODE = "ENV_002";
    
    /**
     * Constructs a new EnvironmentNotFoundException with the specified environment ID.
     * 
     * @param environmentId the ID of the environment that was not found
     */
    public EnvironmentNotFoundException(UUID environmentId) {
        super(
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            "Environment with ID '" + environmentId + "' not found"
        );
    }
    
    /**
     * Constructs a new EnvironmentNotFoundException with the specified environment ID
     * and request context.
     * 
     * @param environmentId the ID of the environment that was not found
     * @param requestId the request ID for tracing
     */
    public EnvironmentNotFoundException(UUID environmentId, String requestId) {
        super(
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            "Environment with ID '" + environmentId + "' not found",
            requestId,
            environmentId.toString()
        );
    }
}