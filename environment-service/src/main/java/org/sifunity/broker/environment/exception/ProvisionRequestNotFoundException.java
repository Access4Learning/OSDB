// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

import java.util.UUID;

/**
 * Exception thrown when a provision request cannot be found by its ID.
 *
 * <p>Returns HTTP 404 Not Found status code.</p>
 */
public class ProvisionRequestNotFoundException extends SifBrokerException {

    private static final String ERROR_CODE = "PROV_002";

    /**
     * Creates a new ProvisionRequestNotFoundException.
     *
     * @param id the ID of the provision request that was not found
     */
    public ProvisionRequestNotFoundException(UUID id) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND,
              "Provision request with ID '" + id + "' not found");
    }
}
