// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to grant environment access that already exists.
 *
 * <p>A user-environment association must be unique. This exception is thrown when
 * attempting to grant a user access to an environment they already have access to.</p>
 *
 * <p>This exception results in HTTP 409 Conflict response.</p>
 *
 * @see org.sifunity.broker.environment.service.UserEnvironmentAccessService
 */
public class DuplicateAccessException extends SifBrokerException {

    private static final String ERROR_CODE = "ADMIN_USER_003";

    /**
     * Constructs a new DuplicateAccessException for the specified user and environment.
     *
     * @param userId        the ID of the user
     * @param environmentId the ID of the environment
     */
    public DuplicateAccessException(String userId, String environmentId) {
        super(
            ERROR_CODE,
            HttpStatus.CONFLICT,
            "User already has access to this environment"
        );
    }
}
