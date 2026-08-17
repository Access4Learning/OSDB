// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested job is not found.
 * <p>
 * This exception is thrown when attempting to retrieve, update, or delete a job
 * that does not exist in the database.
 * </p>
 * <p>
 * Error Code: JOB_001<br>
 * HTTP Status: 404 Not Found
 * </p>
 */
public class JobNotFoundException extends SifBrokerException {
    
    /**
     * Constructs a new JobNotFoundException with the specified detail message.
     *
     * @param message the detail message explaining which job was not found
     */
    public JobNotFoundException(String message) {
        super("JOB_001", HttpStatus.NOT_FOUND, message);
    }
    
    /**
     * Constructs a new JobNotFoundException with the specified detail message and cause.
     *
     * @param message the detail message explaining which job was not found
     * @param cause   the cause of this exception
     */
    public JobNotFoundException(String message, Throwable cause) {
        super("JOB_001", HttpStatus.NOT_FOUND, message, cause);
    }
}
