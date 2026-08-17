// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a subscription create names a service/zone/context/accessType combination for which
 * the caller holds <strong>no approved ACL grant</strong>.
 *
 * <h2>Why this is separate from {@link AclEntryNotFoundException}</h2>
 *
 * <p>D7 (requirement 2.16) splits what used to be one 404 into the two outcomes
 * {@code POST /subscriptions/subscription} actually declares:</p>
 *
 * <ul>
 *   <li><b>This exception → 403.</b> The caller does not hold a matching grant. That is an
 *       authorization outcome, and 403 is declared for the operation.</li>
 *   <li><b>{@link AclEntryNotFoundException} → 400.</b> The caller supplied an explicit
 *       {@code aclEntryId} that does not resolve. That is a malformed reference in the request, so it
 *       is a bad request rather than an authorization failure.</li>
 * </ul>
 *
 * <p>Neither is 404, which the operation does not declare. The 404s on
 * {@code GET}/{@code DELETE /subscriptions/{id}} are declared and conformant and are deliberately
 * left untouched — see {@code SubscriptionConformancePreservationPropertyTest}.</p>
 *
 * <p>Keeping the two distinct also avoids leaking existence information: a caller probing for ACL
 * ids learns "you don't hold this" rather than "this does not exist".</p>
 */
public class AclEntryNotHeldException extends SifBrokerException {

    /** Error code for ACL-not-held (authorization) errors. */
    private static final String ERROR_CODE = "ACL_403";

    /** Authorization failure, not a missing resource. */
    private static final HttpStatus HTTP_STATUS = HttpStatus.FORBIDDEN;

    /**
     * Constructs a new AclEntryNotHeldException.
     *
     * @param message the detail message explaining which grant was required
     */
    public AclEntryNotHeldException(String message) {
        super(ERROR_CODE, HTTP_STATUS, message);
    }
}
