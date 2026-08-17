// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when attempting to delete a job that is currently in progress.
 * <p>
 * This exception is thrown when a delete operation is attempted on a job that is
 * in the INPROGRESS state. Jobs must be in a terminal state (COMPLETED or FAILED)
 * or in NOTSTARTED state before they can be deleted.
 * </p>
 * <p>
 * Error Code: JOB_004<br>
 * HTTP Status: 409 Conflict
 * </p>
 */
public class JobInProgressException extends SifBrokerException {
    
    /**
     * Constructs a new JobInProgressException with the specified detail message.
     *
     * @param message the detail message explaining why the job cannot be deleted
     */
    public JobInProgressException(String message) {
        super("JOB_004", HttpStatus.CONFLICT, message);
    }
    
    /**
     * Constructs a new JobInProgressException with the specified detail message and cause.
     *
     * @param message the detail message explaining why the job cannot be deleted
     * @param cause   the cause of this exception
     */
    public JobInProgressException(String message, Throwable cause) {
        super("JOB_004", HttpStatus.CONFLICT, message, cause);
    }
}
