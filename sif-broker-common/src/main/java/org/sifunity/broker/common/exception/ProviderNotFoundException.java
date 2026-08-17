// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a provider for a requested service cannot be found.
 * This exception maps to HTTP 404 Not Found status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>No provider is registered for the requested service in the specified zone/context</li>
 *   <li>Provider registration has expired or been removed</li>
 *   <li>Service name is valid but no provider is available in the target zone</li>
 *   <li>Routing lookup fails to find a matching provider registration</li>
 * </ul>
 * 
 * <p>This exception indicates that the request was properly authenticated and authorized,
 * but the broker cannot route the request because no provider is available for the
 * requested service. The client may need to wait for a provider to register or
 * verify the service name and zone/context are correct.
 * 
 * @see SifBrokerException
 * @see QueueNotFoundException
 * @see SubscriptionNotFoundException
 */
public class ProviderNotFoundException extends SifBrokerException {
    
    /**
     * Constructs a new ProviderNotFoundException with the specified message.
     * The error code is set to "ROUTE_001" and HTTP status to 404 Not Found.
     * 
     * @param message the detail message explaining which provider was not found
     */
    public ProviderNotFoundException(String message) {
        super("ROUTE_001", HttpStatus.NOT_FOUND, message);
    }
}
