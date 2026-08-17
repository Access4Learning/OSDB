// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

import java.util.UUID;

/**
 * Exception thrown when a non-admin environment attempts to access a provision request
 * belonging to another environment.
 *
 * <p>Returns HTTP 403 Forbidden status code.</p>
 */
public class ProvisionRequestAccessDeniedException extends SifBrokerException {

    private static final String ERROR_CODE = "PROV_003";

    /**
     * Creates a new ProvisionRequestAccessDeniedException.
     *
     * @param requesterId        the environment ID of the caller
     * @param provisionRequestId the ID of the provision request being accessed
     */
    public ProvisionRequestAccessDeniedException(UUID requesterId, UUID provisionRequestId) {
        super(ERROR_CODE, HttpStatus.FORBIDDEN,
              "Environment '" + requesterId + "' is not permitted to access provision request '" + provisionRequestId + "'");
    }
}
