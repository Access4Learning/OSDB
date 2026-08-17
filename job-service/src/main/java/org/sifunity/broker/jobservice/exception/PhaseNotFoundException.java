// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested job phase is not found.
 * <p>
 * This exception is thrown when attempting to retrieve or update a phase
 * that does not exist in the database.
 * </p>
 * <p>
 * Error Code: JOB_002<br>
 * HTTP Status: 404 Not Found
 * </p>
 */
public class PhaseNotFoundException extends SifBrokerException {
    
    /**
     * Constructs a new PhaseNotFoundException with the specified detail message.
     *
     * @param message the detail message explaining which phase was not found
     */
    public PhaseNotFoundException(String message) {
        super("JOB_002", HttpStatus.NOT_FOUND, message);
    }
    
    /**
     * Constructs a new PhaseNotFoundException with the specified detail message and cause.
     *
     * @param message the detail message explaining which phase was not found
     * @param cause   the cause of this exception
     */
    public PhaseNotFoundException(String message, Throwable cause) {
        super("JOB_002", HttpStatus.NOT_FOUND, message, cause);
    }
}
