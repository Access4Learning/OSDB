// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested subscription cannot be found.
 * This exception maps to HTTP 404 Not Found status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Subscription ID does not exist in the system</li>
 *   <li>Subscription has been deleted or expired</li>
 *   <li>Environment attempts to access a subscription for a queue they don't own</li>
 *   <li>Subscription lookup by ID or criteria fails</li>
 *   <li>No subscription exists for the specified service/zone/context combination</li>
 * </ul>
 * 
 * <p>This exception indicates that the request was properly authenticated and authorized,
 * but the specified subscription does not exist or is not accessible to the requesting
 * environment. The client should verify the subscription ID is correct and that the
 * subscription has been created.
 * 
 * @see SifBrokerException
 * @see ProviderNotFoundException
 * @see QueueNotFoundException
 */
public class SubscriptionNotFoundException extends SifBrokerException {
    
    /**
     * Constructs a new SubscriptionNotFoundException with the specified message.
     * The error code is set to "ROUTE_003" and HTTP status to 404 Not Found.
     * 
     * @param message the detail message explaining which subscription was not found
     */
    public SubscriptionNotFoundException(String message) {
        super("ROUTE_003", HttpStatus.NOT_FOUND, message);
    }
}
