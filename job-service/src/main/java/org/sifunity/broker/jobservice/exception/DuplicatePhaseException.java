// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when attempting to create a phase with a duplicate name within the same job.
 * <p>
 * This exception is thrown when a phase creation request specifies a phaseName that already
 * exists for the given job. Phase names must be unique within a job to ensure proper
 * phase identification and tracking.
 * </p>
 * <p>
 * Error Code: JOB_005<br>
 * HTTP Status: 409 Conflict
 * </p>
 */
public class DuplicatePhaseException extends SifBrokerException {
    
    /**
     * Constructs a new DuplicatePhaseException with the specified detail message.
     *
     * @param message the detail message explaining which phase name is duplicated
     */
    public DuplicatePhaseException(String message) {
        super("JOB_005", HttpStatus.CONFLICT, message);
    }
    
    /**
     * Constructs a new DuplicatePhaseException with the specified detail message and cause.
     *
     * @param message the detail message explaining which phase name is duplicated
     * @param cause   the cause of this exception
     */
    public DuplicatePhaseException(String message, Throwable cause) {
        super("JOB_005", HttpStatus.CONFLICT, message, cause);
    }
}
