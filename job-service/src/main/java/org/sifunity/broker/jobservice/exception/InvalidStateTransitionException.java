// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an invalid state transition is attempted.
 * <p>
 * This exception is thrown when attempting to transition a job or phase from one state
 * to another state that is not allowed by the state machine rules. For example:
 * </p>
 * <ul>
 *   <li>NOTSTARTED → COMPLETED (must go through INPROGRESS)</li>
 *   <li>NOTSTARTED → FAILED (must go through INPROGRESS)</li>
 *   <li>COMPLETED → any state (terminal state)</li>
 *   <li>FAILED → any state (terminal state)</li>
 *   <li>INPROGRESS → NOTSTARTED (cannot go backwards)</li>
 * </ul>
 * <p>
 * Error Code: JOB_003<br>
 * HTTP Status: 400 Bad Request
 * </p>
 */
public class InvalidStateTransitionException extends SifBrokerException {
    
    /**
     * Constructs a new InvalidStateTransitionException with the specified detail message.
     *
     * @param message the detail message explaining the invalid transition
     */
    public InvalidStateTransitionException(String message) {
        super("JOB_003", HttpStatus.BAD_REQUEST, message);
    }
    
    /**
     * Constructs a new InvalidStateTransitionException with the specified detail message and cause.
     *
     * @param message the detail message explaining the invalid transition
     * @param cause   the cause of this exception
     */
    public InvalidStateTransitionException(String message, Throwable cause) {
        super("JOB_003", HttpStatus.BAD_REQUEST, message, cause);
    }
}
