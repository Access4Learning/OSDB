// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a parameter has conflicting values between
 * URL matrix parameters and HTTP headers.
 *
 * <p>Per SIF 3.3 backward compatibility, parameters may arrive via both
 * matrix parameters (legacy) and HTTP headers (modern). When both sources
 * provide different values for the same parameter, this exception is thrown
 * to reject the request with HTTP 400.
 *
 * @see org.sifunity.broker.common.util.ParameterResolver
 */
public class ParameterConflictException extends SifBrokerException {

    private final String parameterName;
    private final String matrixValue;
    private final String headerValue;

    /**
     * Constructs a new ParameterConflictException.
     *
     * @param parameterName the name of the conflicting parameter
     * @param matrixValue   the value from the URL matrix parameter
     * @param headerValue   the value from the HTTP header
     */
    public ParameterConflictException(String parameterName, String matrixValue, String headerValue) {
        super("PARAM_CONFLICT", HttpStatus.BAD_REQUEST,
              "Parameter '" + parameterName + "' conflict: matrix value '" + matrixValue
                + "' differs from header value '" + headerValue + "'");
        this.parameterName = parameterName;
        this.matrixValue = matrixValue;
        this.headerValue = headerValue;
    }

    /**
     * Gets the name of the conflicting parameter.
     *
     * @return the parameter name
     */
    public String getParameterName() {
        return parameterName;
    }

    /**
     * Gets the value from the URL matrix parameter.
     *
     * @return the matrix parameter value
     */
    public String getMatrixValue() {
        return matrixValue;
    }

    /**
     * Gets the value from the HTTP header.
     *
     * @return the header value
     */
    public String getHeaderValue() {
        return headerValue;
    }
}
