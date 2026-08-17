// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.exception;

/**
 * Thrown when a consumer-supplied queueId cannot be verified as owned by the caller.
 *
 * <p>Mapped to HTTP 404 by {@link GlobalExceptionHandler}.</p>
 */
public class UnknownQueueException extends RuntimeException {

    public UnknownQueueException(String message) {
        super(message);
    }
}
