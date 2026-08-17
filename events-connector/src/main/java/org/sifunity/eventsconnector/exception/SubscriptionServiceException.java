// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when communication with the Subscription Service fails.
 * <p>
 * This exception is thrown when the Events Connector is unable to query subscriptions
 * from the Subscription Service due to service unavailability, timeout, or other
 * communication errors.
 * </p>
 * <p>
 * Error Code: SUBSCRIPTION_SERVICE_001<br>
 * HTTP Status: 503 Service Unavailable
 * </p>
 * <p>
 * Examples of Subscription Service failures:
 * </p>
 * <ul>
 *   <li>Subscription Service is unreachable</li>
 *   <li>Subscription Service returns 5xx error</li>
 *   <li>Subscription Service call times out</li>
 *   <li>Network connectivity issues</li>
 * </ul>
 *
 * @see EventsConnectorException
 * @see QueueServiceException
 */
public class SubscriptionServiceException extends EventsConnectorException {
    
    private static final String ERROR_CODE = "SUBSCRIPTION_SERVICE_001";
    
    /**
     * Constructs a new SubscriptionServiceException with the specified detail message.
     *
     * @param message the detail message explaining the Subscription Service failure
     */
    public SubscriptionServiceException(String message) {
        super(ERROR_CODE, HttpStatus.SERVICE_UNAVAILABLE, message);
    }
    
    /**
     * Constructs a new SubscriptionServiceException with the specified detail message and cause.
     *
     * @param message the detail message explaining the Subscription Service failure
     * @param cause   the cause of this exception (e.g., WebClientException, TimeoutException)
     */
    public SubscriptionServiceException(String message, Throwable cause) {
        super(ERROR_CODE, HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
