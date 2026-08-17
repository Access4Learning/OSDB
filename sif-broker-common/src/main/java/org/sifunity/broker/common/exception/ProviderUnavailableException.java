// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a provider service is temporarily unavailable.
 * This exception maps to HTTP 503 Service Unavailable status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Provider service is down or not responding</li>
 *   <li>Provider service is undergoing maintenance</li>
 *   <li>Provider service is overloaded and rejecting requests</li>
 *   <li>Network connectivity issues preventing access to provider</li>
 *   <li>Provider service health check failed</li>
 *   <li>Provider endpoint returned 503 or connection timeout</li>
 * </ul>
 * 
 * <p>This exception indicates a temporary unavailability of a specific provider.
 * The client may retry the request after a delay, as the provider may become
 * available again. This differs from {@link ProviderNotFoundException} which
 * indicates no provider is registered for the service.
 * 
 * @see SifBrokerException
 * @see ProviderNotFoundException
 * @see ServiceUnavailableException
 */
public class ProviderUnavailableException extends SifBrokerException {
    
    /**
     * Constructs a new ProviderUnavailableException with the specified message.
     * The error code is set to "SERVICE_001" and HTTP status to 503 Service Unavailable.
     * 
     * @param message the detail message explaining why the provider is unavailable
     */
    public ProviderUnavailableException(String message) {
        super("SERVICE_001", HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
