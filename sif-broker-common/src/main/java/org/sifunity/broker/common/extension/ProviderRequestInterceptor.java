// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.extension;

/**
 * Extension point for intercepting outbound provider requests.
 *
 * <p>Implementations are auto-discovered via Spring Boot auto-configuration.
 * The requests-connector iterates all registered interceptors before and after
 * making outbound HTTP calls to providers.</p>
 *
 * <p>This enables extensions (like CampusAPI translation) to set up context
 * (e.g., ThreadLocal state) that WebClient ExchangeFilterFunctions can read,
 * without requiring extension-specific code in the requests-connector.</p>
 */
public interface ProviderRequestInterceptor {

    /**
     * Called before an outbound HTTP request is sent to a provider.
     * Implementations can set up ThreadLocal context, modify headers, etc.
     *
     * @param context the provider request context containing routing metadata
     */
    void beforeProviderRequest(ProviderRequestContext context);

    /**
     * Called after an outbound HTTP request completes (in a finally block).
     * Implementations MUST clean up any ThreadLocal state here.
     *
     * @param context the same context passed to beforeProviderRequest
     */
    void afterProviderRequest(ProviderRequestContext context);
}
