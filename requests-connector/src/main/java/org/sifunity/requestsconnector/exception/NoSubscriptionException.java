// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.exception;

/**
 * Exception thrown when a provider has no request subscription.
 * 
 * <p>This exception is thrown in the DELAYED_to_DELAYED routing pattern when
 * the Subscription Service indicates that the provider does not have a
 * DELAYED_REQUEST subscription. This means the provider cannot receive
 * asynchronous requests via a queue.</p>
 * 
 * <p>This exception should result in HTTP 404 Not Found being returned to
 * the consumer with the message "Provider has no request subscription".</p>
 * 
 * <p>Requirement 4.3: Return HTTP 404 when provider has no request subscription</p>
 */
public class NoSubscriptionException extends RuntimeException {
    
    /**
     * Constructs a NoSubscriptionException with the specified message.
     * 
     * @param message the detail message explaining why the provider has no subscription
     */
    public NoSubscriptionException(String message) {
        super(message);
    }
    
    /**
     * Constructs a NoSubscriptionException with the specified message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public NoSubscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
