// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a required field is missing from a request.
 * This exception maps to HTTP 400 Bad Request status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Required HTTP header is missing</li>
 *   <li>Required field in request body is null or empty</li>
 *   <li>Required query parameter is not provided</li>
 *   <li>Required path parameter is missing</li>
 * </ul>
 * 
 * <p>This exception indicates that the request is missing a mandatory field
 * that is required for processing. The client should include the missing field
 * and retry the request.
 * 
 * @see SifBrokerException
 * @see InvalidRequestException
 * @see InvalidParameterException
 */
public class MissingRequiredFieldException extends SifBrokerException {
    
    /**
     * Constructs a new MissingRequiredFieldException for the specified field.
     * The error code is set to "VAL_002" and HTTP status to 400 Bad Request.
     * The message is automatically formatted as "Required field missing: {field}".
     * 
     * @param field the name of the missing required field
     */
    public MissingRequiredFieldException(String field) {
        super("VAL_002", HttpStatus.BAD_REQUEST, "Required field missing: " + field);
    }
}
