// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when an invalid service type is provided.
 * 
 * <p>Valid service types are: UTILITY, OBJECT, FUNCTIONAL, SERVICEPATH,
 * XQUERYTEMPLATE, SERVICE. Returns HTTP 400 Bad Request.</p>
 */
public class InvalidServiceTypeException extends SifBrokerException {

    private static final String ERROR_CODE = "CTX_SVC_004";

    public InvalidServiceTypeException(String serviceType) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST,
              String.format("Invalid service type: '%s'. Must be one of: UTILITY, OBJECT, FUNCTIONAL, SERVICEPATH, XQUERYTEMPLATE, SERVICE",
                          serviceType));
    }
}
