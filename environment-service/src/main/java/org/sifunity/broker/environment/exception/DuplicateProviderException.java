// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to register a provider for a service/zone/context
 * combination that already has a provider registered.
 * 
 * <p>This exception enforces the business rule that only one provider can be registered
 * for each unique combination of service name, zone ID, and context ID.</p>
 * 
 * <p>This exception results in HTTP 409 Conflict response.</p>
 * 
 * @see org.sifunity.broker.environment.service.ProviderService
 */
public class DuplicateProviderException extends SifBrokerException {
    
    private static final String ERROR_CODE = "PROV_003";
    
    /**
     * Constructs a new DuplicateProviderException with the specified service/zone/context details.
     * 
     * @param serviceName the service name that already has a provider
     * @param zoneId the zone ID that already has a provider for this service
     * @param contextId the context ID that already has a provider for this service (may be null)
     */
    public DuplicateProviderException(String serviceName, String zoneId, String contextId) {
        super(
            ERROR_CODE,
            HttpStatus.CONFLICT,
            "Provider already registered for service '" + serviceName + 
            "' in zone '" + zoneId + 
            (contextId != null ? "' and context '" + contextId + "'" : "'")
        );
    }
    
    /**
     * Constructs a new DuplicateProviderException with the specified service/zone/context details
     * and request context.
     * 
     * @param serviceName the service name that already has a provider
     * @param zoneId the zone ID that already has a provider for this service
     * @param contextId the context ID that already has a provider for this service (may be null)
     * @param requestId the request ID for tracing
     */
    public DuplicateProviderException(String serviceName, String zoneId, String contextId, String requestId) {
        super(
            ERROR_CODE,
            HttpStatus.CONFLICT,
            "Provider already registered for service '" + serviceName + 
            "' in zone '" + zoneId + 
            (contextId != null ? "' and context '" + contextId + "'" : "'"),
            requestId,
            serviceName + ":" + zoneId + ":" + contextId
        );
    }
}