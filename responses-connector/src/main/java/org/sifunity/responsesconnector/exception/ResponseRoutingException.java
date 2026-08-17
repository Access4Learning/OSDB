// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an error occurs during response routing operations.
 * This exception maps to HTTP 500 Internal Server Error status.
 * 
 * <p>This exception is thrown when an unexpected error occurs during the response
 * routing process that is not covered by more specific exceptions. Common scenarios include:
 * <ul>
 *   <li>Unexpected errors during message envelope construction</li>
 *   <li>Serialization/deserialization failures</li>
 *   <li>Unexpected runtime errors in routing logic</li>
 *   <li>Internal service errors that prevent response delivery</li>
 * </ul>
 * 
 * <p>This is a general-purpose exception for routing failures that don't fit into
 * more specific categories like {@link QueueNotFoundException} or
 * {@link QueueServiceUnavailableException}.
 * 
 * <p>When this exception is thrown, it indicates an internal error in the Responses
 * Connector that should be investigated. The provider receives an HTTP 500 Internal
 * Server Error response.
 * 
 * <p><b>Requirements:</b> 10.3, 10.5</p>
 * 
 * @see org.sifunity.responsesconnector.service.ResponseRoutingService
 * @see QueueNotFoundException
 * @see QueueServiceUnavailableException
 */
public class ResponseRoutingException extends SifBrokerException {
    
    /**
     * Constructs a new ResponseRoutingException with the specified message.
     * The error code is set to "ROUTING_ERROR" and HTTP status to 500 Internal Server Error.
     * 
     * @param message the detail message explaining the routing error
     */
    public ResponseRoutingException(String message) {
        super("ROUTING_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
    
    /**
     * Constructs a new ResponseRoutingException with the specified message and cause.
     * The error code is set to "ROUTING_ERROR" and HTTP status to 500 Internal Server Error.
     * 
     * @param message the detail message explaining the routing error
     * @param cause   the underlying cause of this exception
     */
    public ResponseRoutingException(String message, Throwable cause) {
        super("ROUTING_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}
