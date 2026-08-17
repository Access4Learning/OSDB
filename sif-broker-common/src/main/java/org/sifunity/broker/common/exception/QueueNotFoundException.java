// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested queue cannot be found.
 * This exception maps to HTTP 404 Not Found status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Queue ID does not exist in the system</li>
 *   <li>Queue has been deleted</li>
 *   <li>Environment attempts to access a queue owned by another environment</li>
 *   <li>Queue lookup by ID or name fails</li>
 * </ul>
 * 
 * <p>This exception indicates that the request was properly authenticated and authorized,
 * but the specified queue does not exist or is not accessible to the requesting
 * environment. The client should verify the queue ID is correct and that the queue
 * has been created.
 * 
 * @see SifBrokerException
 * @see ProviderNotFoundException
 * @see SubscriptionNotFoundException
 */
public class QueueNotFoundException extends SifBrokerException {
    
    /**
     * Constructs a new QueueNotFoundException with the specified message.
     * The error code is set to "ROUTE_002" and HTTP status to 404 Not Found.
     * 
     * @param message the detail message explaining which queue was not found
     */
    public QueueNotFoundException(String message) {
        super("ROUTE_002", HttpStatus.NOT_FOUND, message);
    }
}
