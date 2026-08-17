// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.extension;

import java.util.Map;
import java.util.UUID;

/**
 * Context object passed to ProviderRequestInterceptor before/after hooks.
 * Contains all metadata needed by extensions to make decisions about
 * the outbound provider request.
 *
 * @param domainId              the domain identifier
 * @param solutionId            the solution identifier
 * @param providerEnvironmentId the provider environment UUID
 * @param providerEndpoint      the target provider URL
 * @param requestHeaders        mutable map of passthrough headers
 * @param immediateProxy        {@code true} when the outbound call is a transparent
 *                              IMMEDIATE proxy (no broker-level auth signing needed)
 */
public record ProviderRequestContext(
    String domainId,
    String solutionId,
    UUID providerEnvironmentId,
    String providerEndpoint,
    Map<String, String> requestHeaders,
    boolean immediateProxy
) {
    /**
     * Legacy constructor for callers that don't specify immediateProxy (defaults to false).
     */
    public ProviderRequestContext(
            String domainId,
            String solutionId,
            UUID providerEnvironmentId,
            String providerEndpoint,
            Map<String, String> requestHeaders) {
        this(domainId, solutionId, providerEnvironmentId, providerEndpoint, requestHeaders, false);
    }
}
