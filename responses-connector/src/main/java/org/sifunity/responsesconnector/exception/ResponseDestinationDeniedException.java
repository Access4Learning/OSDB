// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.exception;

/**
 * Thrown when a response submitted to a public endpoint may not be delivered to the
 * caller-supplied destination queue.
 *
 * <p>Raised by the destination-ACL gate in either of two situations:</p>
 * <ol>
 *   <li>the owning environment of the named queue could not be resolved, or</li>
 *   <li>that owner holds no approved ACL grant for the object being delivered.</li>
 * </ol>
 *
 * <p><strong>Both cases deliberately produce the same error code and message.</strong>
 * Distinguishing them to the caller would disclose whether a given queue exists and who owns it,
 * handing an attacker an enumeration oracle over other tenants' queue UUIDs (requirement 2.4).
 * The real reason is recorded at WARN for operators instead.</p>
 *
 * <p>Note this is <em>not</em> an ownership failure. A provider legitimately names the
 * <em>consumer's</em> queue when returning a delayed response, so "the caller does not own this
 * queue" is the normal, correct case. The question this exception answers negatively is whether
 * the queue's <em>owner</em> is entitled to the object being delivered.</p>
 *
 * <p>Maps to HTTP 404 in {@code GlobalExceptionHandler}, matching the request-side ownership gate
 * rather than 403, for the same non-disclosure reason.</p>
 */
public class ResponseDestinationDeniedException extends RuntimeException {

    /**
     * Single error code for every denial reason, so the response body cannot be used to
     * distinguish "queue does not exist" from "queue exists but its owner is not entitled".
     */
    private static final String ERROR_CODE = "RESPONSE_DESTINATION_DENIED";

    /** Generic, owner-free message returned to the caller. */
    private static final String GENERIC_MESSAGE = "The specified queue is not a valid destination for this response";

    public ResponseDestinationDeniedException() {
        super(GENERIC_MESSAGE);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
