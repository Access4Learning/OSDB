// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to access an admin user that does not exist.
 *
 * <p>This exception is thrown when querying, updating, or deleting an admin user
 * by ID and the user cannot be found in the database. It is also thrown when
 * attempting to grant environment access to a non-existent user.</p>
 *
 * <p>This exception results in HTTP 404 Not Found response.</p>
 *
 * @see org.sifunity.broker.environment.service.AdminUserService
 */
public class AdminUserNotFoundException extends SifBrokerException {

    private static final String ERROR_CODE = "ADMIN_USER_001";

    /**
     * Constructs a new AdminUserNotFoundException with the specified user ID.
     *
     * @param userId the ID of the admin user that was not found
     */
    public AdminUserNotFoundException(String userId) {
        super(
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            "Admin user not found"
        );
    }
}
