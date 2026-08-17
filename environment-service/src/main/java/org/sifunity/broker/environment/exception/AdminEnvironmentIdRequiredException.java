// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when an admin caller invokes the ACL listing endpoint
 * without providing the required {@code environmentId} query parameter.
 *
 * <p>Returns HTTP 400 Bad Request status code.</p>
 *
 * @see org.sifunity.broker.environment.controller.AclController#listAclEntries
 */
public class AdminEnvironmentIdRequiredException extends SifBrokerException {

    private static final String ERROR_CODE = "ACL_004";

    /**
     * Creates a new AdminEnvironmentIdRequiredException.
     */
    public AdminEnvironmentIdRequiredException() {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST,
              "Admin callers must provide an explicit 'environmentId' query parameter");
    }
}
