// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

import java.util.UUID;

/**
 * Exception thrown when a non-admin environment attempts to view ACL entries
 * belonging to another environment.
 *
 * <p>Returns HTTP 403 Forbidden status code.</p>
 *
 * @see org.sifunity.broker.environment.controller.AclController#listAclEntries
 */
public class InsufficientAclPermissionsException extends SifBrokerException {

    private static final String ERROR_CODE = "ACL_003";

    /**
     * Creates a new InsufficientAclPermissionsException.
     *
     * @param requesterId         the environment ID of the caller
     * @param targetEnvironmentId the environment ID the caller attempted to query
     */
    public InsufficientAclPermissionsException(UUID requesterId, UUID targetEnvironmentId) {
        super(ERROR_CODE, HttpStatus.FORBIDDEN,
              "Environment '" + requesterId + "' is not permitted to view ACL entries for environment '" + targetEnvironmentId + "'");
    }
}
