// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when the Authorization header is malformed or does not conform
 * to the expected SIF HMAC authentication format.
 * This exception maps to HTTP 401 Unauthorized status.
 * 
 * <p>The expected format is: {@code SIF_HMACSHA256 Base64(sessionToken:hash)}
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Authorization header is missing</li>
 *   <li>Authorization header does not start with "SIF_HMACSHA256"</li>
 *   <li>Authorization header is not properly Base64-encoded</li>
 *   <li>Decoded authorization header does not contain sessionToken:hash</li>
 *   <li>Authorization header contains invalid characters or encoding</li>
 * </ul>
 * 
 * @see SifBrokerException
 */
public class MalformedAuthHeaderException extends SifBrokerException {
    
    /**
     * Constructs a new MalformedAuthHeaderException with the specified message.
     * The error code is set to "AUTH_003" and HTTP status to 401 Unauthorized.
     * 
     * @param message the detail message explaining why the authorization header is malformed
     */
    public MalformedAuthHeaderException(String message) {
        super("AUTH_003", HttpStatus.UNAUTHORIZED, message);
    }
}
