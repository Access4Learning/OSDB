// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.exception;

/**
 * Exception thrown when a provider does not support the required message exchange pattern.
 * 
 * <p>This exception indicates that the provider was found in the registry but does not
 * support the message exchange pattern required for the request (IMMEDIATE or DELAYED).
 * This typically results in an HTTP 400 Bad Request response to the consumer.</p>
 * 
 * <p><strong>Requirements:</strong> 3.4</p>
 * 
 * @see org.sifunity.requestsconnector.service.RoutingService
 */
public class UnsupportedPatternException extends RuntimeException {
    
    /**
     * Constructs an UnsupportedPatternException with the specified message.
     * 
     * @param message the exception message
     */
    public UnsupportedPatternException(String message) {
        super(message);
    }
}
