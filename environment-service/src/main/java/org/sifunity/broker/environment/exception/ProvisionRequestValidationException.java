// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when a provision request payload fails validation.
 * Supports multiple error codes for different validation failures:
 * PROV_001 (payload validation), PROV_005 (resolution validation),
 * PROV_006 (zone not found), PROV_007 (context not found).
 *
 * <p>Returns HTTP 400 Bad Request status code.</p>
 */
public class ProvisionRequestValidationException extends SifBrokerException {

    /**
     * Creates a new ProvisionRequestValidationException.
     *
     * @param errorCode the specific error code (e.g., PROV_001, PROV_005, PROV_006, PROV_007)
     * @param message   the descriptive validation error message
     */
    public ProvisionRequestValidationException(String errorCode, String message) {
        super(errorCode, HttpStatus.BAD_REQUEST, message);
    }
}
