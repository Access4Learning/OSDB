// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to register an environment with an application name
 * that already exists in the system.
 * 
 * <p>Application names must be unique across all environments. This exception is thrown
 * during environment registration when the provided application name conflicts with
 * an existing environment.</p>
 * 
 * <p>This exception results in HTTP 409 Conflict response.</p>
 * 
 * @see org.sifunity.broker.environment.service.EnvironmentService#registerEnvironment
 */
public class DuplicateApplicationNameException extends SifBrokerException {
    
    private static final String ERROR_CODE = "ENV_001";
    
    /**
     * Constructs a new DuplicateApplicationNameException with the specified application name.
     * 
     * @param applicationName the application name that already exists
     */
    public DuplicateApplicationNameException(String applicationName) {
        super(
            ERROR_CODE,
            HttpStatus.CONFLICT,
            "Application name '" + applicationName + "' already exists"
        );
    }
    
    /**
     * Constructs a new DuplicateApplicationNameException with the specified application name
     * and request context.
     * 
     * @param applicationName the application name that already exists
     * @param requestId the request ID for tracing
     */
    public DuplicateApplicationNameException(String applicationName, String requestId) {
        super(
            ERROR_CODE,
            HttpStatus.CONFLICT,
            "Application name '" + applicationName + "' already exists",
            requestId,
            null
        );
    }
}