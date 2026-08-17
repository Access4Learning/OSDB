// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a referenced ACL entry cannot be found.
 * <p>
 * This exception is thrown when:
 * <ul>
 *   <li>The provided {@code aclEntryId} does not exist in the {@code acl_entries} table</li>
 *   <li>No matching ACL entry is found for the given {@code serviceName}, {@code zoneId},
 *       {@code contextId}, and {@code accessType} combination</li>
 * </ul>
 * <p>
 * Maps to HTTP <b>400 Bad Request</b>, not 404 (D7 / requirement 2.16).
 * {@code POST /subscriptions/subscription} declares no 404, and a caller-supplied {@code aclEntryId}
 * that does not resolve is a malformed request rather than a missing resource. The declared 404s on
 * {@code GET} / {@code DELETE /subscriptions/{id}} are unaffected.
 * </p>
 * <p>
 * The status has to be right <b>here</b> and not only in the handler that catches this. It previously
 * carried {@code NOT_FOUND} while {@code GlobalExceptionHandler} returned 400, which made the response
 * self-contradictory: {@code SifErrorResponse.fromException} builds the body's {@code code} from
 * {@code getHttpStatus()}, so the HTTP status said 400 while the body said 404 -- still advertising the
 * undeclared status D7 exists to remove.
 * </p>
 *
 * @see org.sifunity.broker.subscriptionservice.domain.Subscription
 */
public class AclEntryNotFoundException extends SifBrokerException {

    /**
     * Error code for ACL entry not found errors.
     */
    private static final String ERROR_CODE = "ACL_001";

    /**
     * HTTP status for an unresolvable ACL entry reference -- see the class javadoc for why this is 400.
     */
    private static final HttpStatus HTTP_STATUS = HttpStatus.BAD_REQUEST;

    /**
     * Constructs a new AclEntryNotFoundException with the specified message.
     *
     * @param message the detail message explaining which ACL entry was not found
     */
    public AclEntryNotFoundException(String message) {
        super(ERROR_CODE, HTTP_STATUS, message);
    }
}
