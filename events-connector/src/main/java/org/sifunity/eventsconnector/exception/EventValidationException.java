// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when event validation fails.
 * <p>
 * This exception is thrown when an event publication request fails validation,
 * such as missing required headers (serviceName, zoneId), invalid contextId,
 * or malformed event payload.
 * </p>
 * <p>
 * Error Code: VALIDATION_001<br>
 * HTTP Status: 400 Bad Request
 * </p>
 * <p>
 * Examples of validation failures:
 * </p>
 * <ul>
 *   <li>Missing serviceName header</li>
 *   <li>Missing zoneId header</li>
 *   <li>Invalid JSON payload</li>
 *   <li>Missing environment context headers</li>
 * </ul>
 *
 * @see EventsConnectorException
 */
public class EventValidationException extends EventsConnectorException {
    
    private static final String ERROR_CODE = "VALIDATION_001";
    
    /**
     * Constructs a new EventValidationException with the specified detail message.
     *
     * @param message the detail message explaining the validation failure
     */
    public EventValidationException(String message) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, message);
    }
    
    /**
     * Constructs a new EventValidationException with the specified detail message and cause.
     *
     * @param message the detail message explaining the validation failure
     * @param cause   the cause of this exception
     */
    public EventValidationException(String message, Throwable cause) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST, message, cause);
    }
}
