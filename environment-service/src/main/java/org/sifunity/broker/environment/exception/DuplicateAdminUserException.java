// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to create an admin user with a username
 * that already exists within the same domain+solution scope.
 *
 * <p>Usernames must be unique within a domain+solution combination. This exception
 * is thrown during user creation when the provided username conflicts with
 * an existing user in the same scope.</p>
 *
 * <p>This exception results in HTTP 409 Conflict response.</p>
 *
 * @see org.sifunity.broker.environment.service.AdminUserService
 */
public class DuplicateAdminUserException extends SifBrokerException {

    private static final String ERROR_CODE = "ADMIN_USER_002";

    /**
     * Constructs a new DuplicateAdminUserException with the specified username.
     *
     * @param username the username that already exists in the domain+solution scope
     */
    public DuplicateAdminUserException(String username) {
        super(
            ERROR_CODE,
            HttpStatus.CONFLICT,
            "Username already exists in this domain+solution"
        );
    }
}
