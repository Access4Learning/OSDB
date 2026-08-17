// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to associate a user with an environment
 * that does not belong to the user's domain+solution scope.
 *
 * <p>Admin users are scoped to a specific domain+solution combination. Environment
 * access can only be granted for environments within the same domain+solution.
 * This exception is thrown when the target environment belongs to a different scope.</p>
 *
 * <p>This exception results in HTTP 400 Bad Request response.</p>
 *
 * @see org.sifunity.broker.environment.service.UserEnvironmentAccessService
 */
public class DomainSolutionMismatchException extends SifBrokerException {

    private static final String ERROR_CODE = "ADMIN_USER_004";

    /**
     * Constructs a new DomainSolutionMismatchException.
     *
     * @param environmentId the ID of the environment that does not match the user's scope
     */
    public DomainSolutionMismatchException(String environmentId) {
        super(
            ERROR_CODE,
            HttpStatus.BAD_REQUEST,
            "Environment does not belong to the user's domain+solution"
        );
    }
}
