// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a parameter has an invalid value or format.
 * This exception maps to HTTP 400 Bad Request status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Parameter value does not match expected format (e.g., invalid UUID)</li>
 *   <li>Parameter value is outside acceptable range</li>
 *   <li>Parameter value violates validation rules</li>
 *   <li>Enum parameter has an unrecognized value</li>
 * </ul>
 * 
 * <p>This exception indicates that a specific parameter in the request has an
 * invalid value. The client should correct the parameter value according to
 * the validation rules and retry the request.
 * 
 * @see SifBrokerException
 * @see InvalidRequestException
 * @see MissingRequiredFieldException
 */
public class InvalidParameterException extends SifBrokerException {
    
    /**
     * Constructs a new InvalidParameterException for the specified parameter.
     * The error code is set to "VAL_003" and HTTP status to 400 Bad Request.
     * The message is automatically formatted as "Invalid parameter '{parameter}': {reason}".
     * 
     * @param parameter the name of the invalid parameter
     * @param reason    the reason why the parameter is invalid
     */
    public InvalidParameterException(String parameter, String reason) {
        super("VAL_003", HttpStatus.BAD_REQUEST, 
              "Invalid parameter '" + parameter + "': " + reason);
    }
}
