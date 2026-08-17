// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.exception.SifBrokerException;

import java.util.UUID;

/**
 * Exception thrown when attempting to register a provider with an environment
 * that is not of type BROKERED.
 * 
 * <p>This exception enforces the business rule that only environments with
 * type BROKERED can register provider services. DIRECT environments communicate
 * directly with providers and do not register through the broker.</p>
 * 
 * <p>This exception results in HTTP 400 Bad Request response.</p>
 * 
 * @see org.sifunity.broker.environment.service.ProviderService
 * @see org.sifunity.broker.common.constant.EnvironmentType#BROKERED
 */
public class InvalidProviderEnvironmentException extends SifBrokerException {
    
    private static final String ERROR_CODE = "PROV_001";
    
    /**
     * Constructs a new InvalidProviderEnvironmentException with the specified environment details.
     * 
     * @param environmentId the ID of the environment that is not of type BROKERED
     * @param actualType the actual type of the environment
     */
    public InvalidProviderEnvironmentException(UUID environmentId, EnvironmentType actualType) {
        super(
            ERROR_CODE,
            HttpStatus.BAD_REQUEST,
            "Environment with ID '" + environmentId + "' has type '" + actualType + 
            "' but must be type 'BROKERED' to register provider services"
        );
    }
    
    /**
     * Constructs a new InvalidProviderEnvironmentException with the specified environment details
     * and request context.
     * 
     * @param environmentId the ID of the environment that is not of type BROKERED
     * @param actualType the actual type of the environment
     * @param requestId the request ID for tracing
     */
    public InvalidProviderEnvironmentException(UUID environmentId, EnvironmentType actualType, String requestId) {
        super(
            ERROR_CODE,
            HttpStatus.BAD_REQUEST,
            "Environment with ID '" + environmentId + "' has type '" + actualType + 
            "' but must be type 'BROKERED' to register provider services",
            requestId,
            environmentId.toString()
        );
    }
}