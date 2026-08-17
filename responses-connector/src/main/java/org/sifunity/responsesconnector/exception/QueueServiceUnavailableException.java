// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when the Queue Service is unavailable or unreachable.
 * This exception maps to HTTP 503 Service Unavailable status.
 * 
 * <p>This exception is thrown when the Responses Connector cannot communicate with
 * the Queue Service due to:
 * <ul>
 *   <li>Queue Service is down or not responding</li>
 *   <li>Network connectivity issues</li>
 *   <li>Queue Service returning 5xx server errors</li>
 *   <li>All retry attempts have been exhausted</li>
 * </ul>
 * 
 * <p>When this exception is thrown, the response cannot be delivered to the consumer's
 * queue, and the provider should be notified with an HTTP 503 Service Unavailable error.
 * The provider may retry the request later when the Queue Service is available.
 * 
 * <p>The Responses Connector implements retry logic with exponential backoff for
 * transient failures. This exception is only thrown after all retry attempts have
 * been exhausted.
 * 
 * <p><b>Requirements:</b> 3.6, 9.4, 10.1, 12.4</p>
 * 
 * @see org.sifunity.responsesconnector.client.QueueServiceClient
 */
public class QueueServiceUnavailableException extends SifBrokerException {
    
    /**
     * Constructs a new QueueServiceUnavailableException with the specified message.
     * The error code is set to "SERVICE_UNAVAILABLE" and HTTP status to 503 Service Unavailable.
     * 
     * @param message the detail message explaining the service unavailability
     */
    public QueueServiceUnavailableException(String message) {
        super("SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, message);
    }
    
    /**
     * Constructs a new QueueServiceUnavailableException with the specified message and cause.
     * The error code is set to "SERVICE_UNAVAILABLE" and HTTP status to 503 Service Unavailable.
     * 
     * @param message the detail message explaining the service unavailability
     * @param cause   the underlying cause of this exception (e.g., WebClientException)
     */
    public QueueServiceUnavailableException(String message, Throwable cause) {
        super("SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }
}
