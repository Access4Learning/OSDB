// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an ACL entry's access type does not match the expected type
 * for the subscription being created.
 * <p>
 * This exception is thrown when:
 * <ul>
 *   <li>An EVENT subscription references an ACL entry that does not have
 *       {@code access_type = SUBSCRIBE}</li>
 *   <li>A DELAYED_REQUEST subscription references an ACL entry that does not have
 *       {@code access_type = PROVIDE}</li>
 * </ul>
 * <p>
 * This exception maps to HTTP 403 Forbidden status code, as an access type mismatch
 * is an authorization failure (insufficient permissions) rather than a validation error.
 * </p>
 *
 * @see org.sifunity.broker.subscriptionservice.domain.Subscription
 */
public class AccessTypeMismatchException extends SifBrokerException {

    /**
     * Error code for access type mismatch errors.
     */
    private static final String ERROR_CODE = "ACL_002";

    /**
     * HTTP status code for access type mismatch errors.
     */
    private static final HttpStatus HTTP_STATUS = HttpStatus.FORBIDDEN;

    /**
     * Constructs a new AccessTypeMismatchException with the specified message.
     *
     * @param message the detail message explaining the access type mismatch
     */
    public AccessTypeMismatchException(String message) {
        super(ERROR_CODE, HTTP_STATUS, message);
    }
}
