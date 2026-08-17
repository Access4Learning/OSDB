// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

import java.util.UUID;

/**
 * Exception thrown when attempting to access a provider registration that does not exist.
 * 
 * <p>This exception is thrown when querying, updating, or deleting a provider registration
 * by ID and the provider registration cannot be found in the database.</p>
 * 
 * <p>This exception results in HTTP 404 Not Found response.</p>
 * 
 * @see org.sifunity.broker.environment.service.ProviderService
 */
public class ProviderRegistrationNotFoundException extends SifBrokerException {
    
    private static final String ERROR_CODE = "PROV_002";
    
    /**
     * Constructs a new ProviderRegistrationNotFoundException with the specified provider registration ID.
     * 
     * @param providerRegistrationId the ID of the provider registration that was not found
     */
    public ProviderRegistrationNotFoundException(UUID providerRegistrationId) {
        super(
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            "Provider registration with ID '" + providerRegistrationId + "' not found"
        );
    }
    
    /**
     * Constructs a new ProviderRegistrationNotFoundException with the specified provider registration ID
     * and request context.
     * 
     * @param providerRegistrationId the ID of the provider registration that was not found
     * @param requestId the request ID for tracing
     */
    public ProviderRegistrationNotFoundException(UUID providerRegistrationId, String requestId) {
        super(
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            "Provider registration with ID '" + providerRegistrationId + "' not found",
            requestId,
            providerRegistrationId.toString()
        );
    }
}