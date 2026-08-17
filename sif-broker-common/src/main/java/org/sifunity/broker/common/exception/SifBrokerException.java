// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception class for all SIF Broker exceptions.
 * <p>
 * This exception provides a standardized structure for error handling across all
 * SIF Unity Broker microservices. It includes error codes, HTTP status codes,
 * and optional context information for debugging.
 * </p>
 * <p>
 * All specific exception types in the broker system should extend this class.
 * </p>
 *
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.3/">SIF 3.7 Specification</a>
 */
public class SifBrokerException extends RuntimeException {
    /**
     * The error code identifying the specific error type (e.g., "AUTH_001", "VAL_002").
     */
    private final String errorCode;
    
    /**
     * The HTTP status code to be returned in the response.
     */
    private final HttpStatus httpStatus;
    
    /**
     * The request ID for tracing the request that caused this exception (optional).
     */
    private final String requestId;
    
    /**
     * The environment ID associated with the request that caused this exception (optional).
     */
    private final String environmentId;
    
    /**
     * Constructs a new SifBrokerException with the specified error code, HTTP status, and message.
     *
     * @param errorCode  the error code identifying the specific error type
     * @param httpStatus the HTTP status code to be returned
     * @param message    the detail message explaining the error
     */
    public SifBrokerException(String errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.requestId = null;
        this.environmentId = null;
    }
    
    /**
     * Constructs a new SifBrokerException with the specified error code, HTTP status, message, and cause.
     *
     * @param errorCode  the error code identifying the specific error type
     * @param httpStatus the HTTP status code to be returned
     * @param message    the detail message explaining the error
     * @param cause      the cause of this exception
     */
    public SifBrokerException(String errorCode, HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.requestId = null;
        this.environmentId = null;
    }
    
    /**
     * Constructs a new SifBrokerException with the specified error code, HTTP status, message,
     * and context information.
     *
     * @param errorCode     the error code identifying the specific error type
     * @param httpStatus    the HTTP status code to be returned
     * @param message       the detail message explaining the error
     * @param requestId     the request ID for tracing (may be null)
     * @param environmentId the environment ID associated with the request (may be null)
     */
    public SifBrokerException(String errorCode, HttpStatus httpStatus, String message, 
                              String requestId, String environmentId) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.requestId = requestId;
        this.environmentId = environmentId;
    }
    
    /**
     * Gets the error code identifying the specific error type.
     *
     * @return the error code
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * Gets the HTTP status code to be returned in the response.
     *
     * @return the HTTP status code
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
    
    /**
     * Gets the request ID for tracing the request that caused this exception.
     *
     * @return the request ID, or null if not set
     */
    public String getRequestId() {
        return requestId;
    }
    
    /**
     * Gets the environment ID associated with the request that caused this exception.
     *
     * @return the environment ID, or null if not set
     */
    public String getEnvironmentId() {
        return environmentId;
    }
}
