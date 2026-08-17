// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when attempting to create a subscription that already exists.
 * <p>
 * This exception is thrown when a subscription with the same combination of
 * queueId, serviceName, zoneId, contextId, and subscriptionType already exists
 * in the system. The uniqueness constraint ensures that messages are not delivered
 * multiple times to the same queue.
 * </p>
 * <p>
 * This exception maps to HTTP 409 Conflict status code.
 * </p>
 *
 * @see org.sifunity.broker.subscriptionservice.domain.Subscription
 */
public class DuplicateSubscriptionException extends SifBrokerException {
    
    /**
     * Error code for duplicate subscription errors.
     */
    private static final String ERROR_CODE = "SUBSCRIPTION_001";
    
    /**
     * HTTP status code for duplicate subscription errors.
     */
    private static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;
    
    /**
     * Constructs a new DuplicateSubscriptionException with the specified message.
     *
     * @param message the detail message explaining which subscription already exists
     */
    public DuplicateSubscriptionException(String message) {
        super(ERROR_CODE, HTTP_STATUS, message);
    }
}
