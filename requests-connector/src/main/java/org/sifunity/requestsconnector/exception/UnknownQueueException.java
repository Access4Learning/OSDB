// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.exception;

/**
 * Thrown when a consumer-supplied queueId cannot be verified as owned by the caller.
 *
 * <p>Covers three cases: queue not found, queue owned by a different environment,
 * and ownership lookup failure. All three are surfaced as the same exception to
 * avoid leaking information about other tenants' queues.</p>
 *
 * <p>Mapped to HTTP 404 by {@link GlobalExceptionHandler} — never 403, to prevent
 * queue existence enumeration.</p>
 */
public class UnknownQueueException extends RuntimeException {

    public UnknownQueueException(String message) {
        super(message);
    }
}
