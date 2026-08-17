// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a broker internal service is temporarily unavailable.
 * This exception maps to HTTP 503 Service Unavailable status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Internal microservice (Queue Service, Environment Service, etc.) is down</li>
 *   <li>Internal service is undergoing maintenance or deployment</li>
 *   <li>Internal service is overloaded and rejecting requests</li>
 *   <li>Service discovery failed to locate the internal service</li>
 *   <li>Circuit breaker opened due to repeated failures</li>
 *   <li>Internal service health check failed</li>
 * </ul>
 * 
 * <p>This exception indicates a temporary unavailability of a broker internal service.
 * The client may retry the request after a delay, as the service may become
 * available again. This differs from {@link ProviderUnavailableException} which
 * specifically refers to external provider services.
 * 
 * @see SifBrokerException
 * @see ProviderUnavailableException
 * @see StorageUnavailableException
 */
public class ServiceUnavailableException extends SifBrokerException {
    
    /**
     * Constructs a new ServiceUnavailableException with the specified service name and message.
     * The error code is set to "SERVICE_002" and HTTP status to 503 Service Unavailable.
     * 
     * @param service the name of the unavailable service
     * @param message the detail message explaining why the service is unavailable
     */
    public ServiceUnavailableException(String service, String message) {
        super("SERVICE_002", HttpStatus.SERVICE_UNAVAILABLE, 
              "Service '" + service + "' unavailable: " + message);
    }
}
