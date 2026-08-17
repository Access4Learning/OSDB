// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

import java.util.UUID;

/**
 * Exception thrown when attempting to create a duplicate context service.
 * 
 * <p>A context service is considered duplicate when the same (contextUuid, serviceName)
 * combination already exists. Returns HTTP 409 Conflict.</p>
 */
public class DuplicateContextServiceException extends SifBrokerException {

    private static final String ERROR_CODE = "CTX_SVC_002";

    public DuplicateContextServiceException(UUID contextUuid, String serviceName) {
        super(ERROR_CODE, HttpStatus.CONFLICT,
              String.format("Context service already exists for context %s, service '%s'",
                          contextUuid, serviceName));
    }
}
