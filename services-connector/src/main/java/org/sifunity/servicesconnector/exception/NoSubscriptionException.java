// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.exception;

/**
 * Exception thrown when a provider has no request subscription for DELAYED_to_DELAYED routing.
 *
 * <p>In the DELAYED_to_DELAYED pattern, the provider must have a DELAYED_REQUEST
 * subscription so the broker can enqueue the request to the provider's queue.
 * If no subscription exists, this exception is thrown and mapped to HTTP 404.</p>
 *
 * <p><strong>Requirements:</strong> 5.2</p>
 */
public class NoSubscriptionException extends RuntimeException {

    public NoSubscriptionException(String message) {
        super(message);
    }

    public NoSubscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
