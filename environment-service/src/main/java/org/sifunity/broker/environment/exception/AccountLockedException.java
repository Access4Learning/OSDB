// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when a login attempt is made on a locked account.
 *
 * <p>An account is locked after 5 consecutive failed login attempts within
 * a 15-minute window. The error message intentionally does not disclose
 * the lockout state to prevent information leakage.</p>
 *
 * <p>This exception results in HTTP 401 Unauthorized response.</p>
 *
 * @see org.sifunity.broker.environment.service.UserAuthenticationService
 */
public class AccountLockedException extends SifBrokerException {

    private static final String ERROR_CODE = "ADMIN_USER_005";

    /**
     * Constructs a new AccountLockedException.
     * The message intentionally matches the generic invalid credentials message
     * to avoid disclosing lockout status.
     */
    public AccountLockedException() {
        super(
            ERROR_CODE,
            HttpStatus.UNAUTHORIZED,
            "Invalid credentials"
        );
    }
}
