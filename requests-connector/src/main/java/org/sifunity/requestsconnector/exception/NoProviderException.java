// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.exception;

/**
 * Exception thrown when no provider is registered for a requested service/zone/context combination.
 * 
 * <p>This exception indicates that the provider registry lookup did not find any provider
 * matching the requested serviceName, zoneId, and contextId. This typically results in
 * an HTTP 404 Not Found response to the consumer.</p>
 * 
 * <p><strong>Requirements:</strong> 1.5, 2.3</p>
 * 
 * @see org.sifunity.requestsconnector.service.RoutingService
 */
public class NoProviderException extends RuntimeException {
    
    /**
     * Constructs a NoProviderException with a formatted message.
     * 
     * @param serviceName the name of the requested service
     * @param zoneId the zone ID
     * @param contextId the context ID (may be null)
     */
    public NoProviderException(String serviceName, String zoneId, String contextId) {
        super(String.format("No provider registered for service=%s in zone=%s context=%s",
            serviceName, zoneId, contextId != null ? contextId : "default"));
    }
    
    /**
     * Constructs a NoProviderException with a custom message.
     * 
     * @param message the exception message
     */
    public NoProviderException(String message) {
        super(message);
    }
}
