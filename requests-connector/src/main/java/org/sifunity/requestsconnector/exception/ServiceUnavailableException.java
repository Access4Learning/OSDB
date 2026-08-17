// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.exception;

/**
 * Thrown when a required <strong>core broker service</strong> (environment-service,
 * queue-service, subscription-service) cannot be reached — connection refused, DNS
 * failure, connection reset, etc.
 *
 * <p>This is distinct from a provider endpoint being unreachable (which maps to HTTP 502
 * Bad Gateway): a core service being down is an internal dependency failure and maps to
 * HTTP 503 Service Unavailable. It is also distinct from a core service returning an HTTP
 * error status (handled via {@code WebClientResponseException}); this exception represents a
 * <em>transport-level</em> failure where no HTTP response was received at all.</p>
 *
 * <p>Carrying the failing service name lets the global handler produce an actionable message
 * (e.g. "subscription-service is currently unavailable") instead of a generic 500.</p>
 */
public class ServiceUnavailableException extends RuntimeException {

    private final String serviceName;

    /**
     * @param serviceName the core service that could not be reached (e.g. "subscription-service")
     * @param cause       the underlying transport failure
     */
    public ServiceUnavailableException(String serviceName, Throwable cause) {
        super(String.format("%s is currently unavailable", serviceName), cause);
        this.serviceName = serviceName;
    }

    /** @return the name of the core service that could not be reached */
    public String getServiceName() {
        return serviceName;
    }
}
