// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when communication with the Queue Service fails.
 * <p>
 * This exception is thrown when the Events Connector is unable to enqueue events
 * to the Queue Service due to service unavailability, timeout, or other
 * communication errors.
 * </p>
 * <p>
 * Error Code: QUEUE_SERVICE_001<br>
 * HTTP Status: 503 Service Unavailable
 * </p>
 * <p>
 * Examples of Queue Service failures:
 * </p>
 * <ul>
 *   <li>Queue Service is unreachable</li>
 *   <li>Queue Service returns 5xx error</li>
 *   <li>Queue Service call times out</li>
 *   <li>Network connectivity issues</li>
 *   <li>All queue enqueue operations fail</li>
 * </ul>
 * <p>
 * Note: This exception is typically thrown when all queue operations fail.
 * Partial failures (some queues succeed, some fail) are handled differently
 * and result in a 207 Multi-Status response rather than this exception.
 * </p>
 *
 * @see EventsConnectorException
 * @see SubscriptionServiceException
 */
public class QueueServiceException extends EventsConnectorException {
    
    private static final String ERROR_CODE = "QUEUE_SERVICE_001";
    
    /**
     * Constructs a new QueueServiceException with the specified detail message.
     *
     * @param message the detail message explaining the Queue Service failure
     */
    public QueueServiceException(String message) {
        super(ERROR_CODE, HttpStatus.SERVICE_UNAVAILABLE, message);
    }
    
    /**
     * Constructs a new QueueServiceException with the specified detail message and cause.
     *
     * @param message the detail message explaining the Queue Service failure
     * @param cause   the cause of this exception (e.g., WebClientException, TimeoutException)
     */
    public QueueServiceException(String message, Throwable cause) {
        super(ERROR_CODE, HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
