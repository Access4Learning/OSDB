// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an invalid or expired authentication token is provided.
 * This exception maps to HTTP 401 Unauthorized status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Session token does not exist in the system</li>
 *   <li>Session token has expired</li>
 *   <li>HMAC signature validation fails</li>
 * </ul>
 * 
 * @see SifBrokerException
 */
public class InvalidTokenException extends SifBrokerException {
    
    /**
     * Constructs a new InvalidTokenException with the specified message.
     * The error code is set to "AUTH_001" and HTTP status to 401 Unauthorized.
     * 
     * @param message the detail message explaining why the token is invalid
     */
    public InvalidTokenException(String message) {
        super("AUTH_001", HttpStatus.UNAUTHORIZED, message);
    }
}
