// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.exception;

/**
 * Exception thrown when a provider call times out.
 * 
 * <p>This exception is thrown when a provider endpoint does not respond within
 * the configured timeout period. The timeout can be configured separately for
 * connection establishment and read operations.</p>
 * 
 * <p>This exception is handled by the GlobalExceptionHandler and results in
 * HTTP 504 Gateway Timeout being returned to the consumer.</p>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>11.3: Return HTTP 504 Gateway Timeout when provider call times out</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class ProviderTimeoutException extends RuntimeException {
    
    /**
     * Constructs a new ProviderTimeoutException with the specified detail message.
     * 
     * @param message the detail message
     */
    public ProviderTimeoutException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new ProviderTimeoutException with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the timeout
     */
    public ProviderTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
