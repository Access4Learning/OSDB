// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when an admin session token is expired or invalid.
 *
 * <p>This exception is thrown when a request includes an admin session token
 * that cannot be found in Redis (expired or never existed) or has been
 * explicitly invalidated via logout.</p>
 *
 * <p>This exception results in HTTP 401 Unauthorized response.</p>
 *
 * @see org.sifunity.broker.environment.service.UserAuthenticationService
 */
public class InvalidAdminSessionException extends SifBrokerException {

    private static final String ERROR_CODE = "ADMIN_USER_006";

    /**
     * Constructs a new InvalidAdminSessionException.
     */
    public InvalidAdminSessionException() {
        super(
            ERROR_CODE,
            HttpStatus.UNAUTHORIZED,
            "Session expired or invalid"
        );
    }
}
