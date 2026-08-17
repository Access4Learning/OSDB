// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a request timestamp is outside the acceptable tolerance window.
 * This exception maps to HTTP 401 Unauthorized status.
 * 
 * <p>According to SIF 3.7 specification, timestamps must be within a configurable
 * tolerance window (default 5 minutes) to prevent replay attacks.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Timestamp is too far in the past</li>
 *   <li>Timestamp is too far in the future</li>
 *   <li>Timestamp format is invalid or unparseable</li>
 * </ul>
 * 
 * @see SifBrokerException
 */
public class InvalidTimestampException extends SifBrokerException {
    
    /**
     * Constructs a new InvalidTimestampException with the specified message.
     * The error code is set to "AUTH_002" and HTTP status to 401 Unauthorized.
     * 
     * @param message the detail message explaining why the timestamp is invalid
     */
    public InvalidTimestampException(String message) {
        super("AUTH_002", HttpStatus.UNAUTHORIZED, message);
    }
}
