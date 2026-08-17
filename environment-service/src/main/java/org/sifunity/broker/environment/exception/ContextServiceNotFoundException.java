// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

import java.util.UUID;

/**
 * Exception thrown when a context service record is not found.
 * 
 * <p>This exception results in HTTP 404 Not Found response.</p>
 */
public class ContextServiceNotFoundException extends SifBrokerException {

    private static final String ERROR_CODE = "CTX_SVC_003";

    public ContextServiceNotFoundException(UUID id) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, "Context service not found: " + id);
    }
}
