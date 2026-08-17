// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a required HTTP header is missing from the request.
 * This exception maps to HTTP 400 Bad Request status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>X-Environment-Id header is missing</li>
 *   <li>X-Environment-Name header is missing</li>
 *   <li>queueId header is missing for DELAYED requests</li>
 * </ul>
 * 
 * <p>This exception indicates that the request is missing required context
 * information that should have been injected by the Gateway or provided by
 * the consumer.
 * 
 * <p>Requirements: 8.3</p>
 */
public class MissingHeaderException extends SifBrokerException {
    
    /**
     * Constructs a new MissingHeaderException with the specified message.
     * The error code is set to "REQ_001" and HTTP status to 400 Bad Request.
     * 
     * @param message the detail message explaining which header is missing
     */
    public MissingHeaderException(String message) {
        super("REQ_001", HttpStatus.BAD_REQUEST, message);
    }
}
