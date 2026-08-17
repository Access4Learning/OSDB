// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.exception;

import org.sifunity.broker.common.exception.SifBrokerException;
import org.springframework.http.HttpStatus;

/**
 * Base exception class for all Events Connector exceptions.
 * <p>
 * This exception provides a standardized structure for error handling within the
 * Events Connector service. All specific exception types in the Events Connector
 * should extend this class.
 * </p>
 * <p>
 * The Events Connector is responsible for receiving event publications from Providers,
 * querying subscriptions, and distributing events to subscriber queues.
 * </p>
 *
 * @see EventValidationException
 * @see SubscriptionServiceException
 * @see QueueServiceException
 */
public class EventsConnectorException extends SifBrokerException {
    
    /**
     * Constructs a new EventsConnectorException with the specified error code, HTTP status, and message.
     *
     * @param errorCode  the error code identifying the specific error type
     * @param httpStatus the HTTP status code to be returned
     * @param message    the detail message explaining the error
     */
    public EventsConnectorException(String errorCode, HttpStatus httpStatus, String message) {
        super(errorCode, httpStatus, message);
    }
    
    /**
     * Constructs a new EventsConnectorException with the specified error code, HTTP status, message, and cause.
     *
     * @param errorCode  the error code identifying the specific error type
     * @param httpStatus the HTTP status code to be returned
     * @param message    the detail message explaining the error
     * @param cause      the cause of this exception
     */
    public EventsConnectorException(String errorCode, HttpStatus httpStatus, String message, Throwable cause) {
        super(errorCode, httpStatus, message, cause);
    }
    
    /**
     * Constructs a new EventsConnectorException with the specified error code, HTTP status, message,
     * and context information.
     *
     * @param errorCode     the error code identifying the specific error type
     * @param httpStatus    the HTTP status code to be returned
     * @param message       the detail message explaining the error
     * @param requestId     the request ID for tracing (may be null)
     * @param environmentId the environment ID associated with the request (may be null)
     */
    public EventsConnectorException(String errorCode, HttpStatus httpStatus, String message,
                                    String requestId, String environmentId) {
        super(errorCode, httpStatus, message, requestId, environmentId);
    }
}
