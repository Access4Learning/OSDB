// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

import java.util.UUID;

/**
 * Exception thrown when a non-admin environment attempts an admin-only operation
 * such as resolving a provision request.
 *
 * <p>Returns HTTP 403 Forbidden status code.</p>
 */
public class AdminRequiredException extends SifBrokerException {

    private static final String ERROR_CODE = "PROV_004";

    /**
     * Creates a new AdminRequiredException.
     *
     * @param requesterId the environment ID of the non-admin caller
     */
    public AdminRequiredException(UUID requesterId) {
        super(ERROR_CODE, HttpStatus.FORBIDDEN,
              "Admin privileges required. Environment '" + requesterId + "' is not an admin.");
    }
}
