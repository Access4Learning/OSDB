// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a queue with the specified queueId does not exist.
 * This exception maps to HTTP 404 Not Found status.
 * 
 * <p>This exception is thrown when attempting to enqueue a response to a consumer's
 * queue that does not exist in the Queue Service. This typically indicates that:
 * <ul>
 *   <li>The queueId provided in the response headers is invalid</li>
 *   <li>The consumer's queue has been deleted</li>
 *   <li>The queueId was incorrectly specified by the provider</li>
 * </ul>
 * 
 * <p>When this exception is thrown, the response cannot be delivered to the consumer,
 * and the provider should be notified with an HTTP 404 Not Found error.
 * 
 * <p><b>Requirements:</b> 3.7, 9.3, 10.4</p>
 * 
 * @see org.sifunity.responsesconnector.client.QueueServiceClient
 */
public class QueueNotFoundException extends SifBrokerException {
    
    /**
     * Constructs a new QueueNotFoundException with the specified message.
     * The error code is set to "QUEUE_NOT_FOUND" and HTTP status to 404 Not Found.
     * 
     * @param message the detail message explaining which queue was not found
     */
    public QueueNotFoundException(String message) {
        super("QUEUE_NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }
    
    /**
     * Constructs a new QueueNotFoundException with the specified message and cause.
     * The error code is set to "QUEUE_NOT_FOUND" and HTTP status to 404 Not Found.
     * 
     * @param message the detail message explaining which queue was not found
     * @param cause   the underlying cause of this exception
     */
    public QueueNotFoundException(String message, Throwable cause) {
        super("QUEUE_NOT_FOUND", HttpStatus.NOT_FOUND, message, cause);
    }
}
