// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a request is malformed or contains invalid data.
 * This exception maps to HTTP 400 Bad Request status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Request body cannot be parsed as valid JSON</li>
 *   <li>Request contains semantically invalid data</li>
 *   <li>Request violates business rules or constraints</li>
 *   <li>Request structure does not conform to expected format</li>
 * </ul>
 * 
 * <p>This exception indicates that the request was properly authenticated and authorized,
 * but the request itself is invalid and cannot be processed. The client should review
 * the request format and data before retrying.
 * 
 * @see SifBrokerException
 * @see MissingRequiredFieldException
 * @see InvalidParameterException
 */
public class InvalidRequestException extends SifBrokerException {
    
    /**
     * Constructs a new InvalidRequestException with the specified message.
     * The error code is set to "VAL_001" and HTTP status to 400 Bad Request.
     * 
     * @param message the detail message explaining why the request is invalid
     */
    public InvalidRequestException(String message) {
        super("VAL_001", HttpStatus.BAD_REQUEST, message);
    }
}
