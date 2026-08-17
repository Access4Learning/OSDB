// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.extension.ProviderRequestContext;
import org.sifunity.requestsconnector.auth.AuthSigningInterceptor;
import org.sifunity.requestsconnector.auth.ProviderCredentialService;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Property-based test for null environment invariant.
 *
 * <p><strong>Property 3: Null Environment Invariant</strong></p>
 *
 * <p>For any ProviderRequestContext where providerEnvironmentId is null, and for any
 * initial state of the requestHeaders map, after the AuthSigningInterceptor executes
 * beforeProviderRequest, the requestHeaders map SHALL be identical to its initial state
 * (no entries added, removed, or modified).</p>
 *
 * <p><strong>Validates: Requirements 4.5, 8.4</strong></p>
 * <ul>
 *   <li>4.5: IF providerEnvironmentId is null, THEN the interceptor SHALL not modify requestHeaders</li>
 *   <li>8.4: IF providerEnvironmentId is null, THEN skip signing without ERROR/WARN logging</li>
 * </ul>
 */
@Tag("Feature: provider-auth-signing, Property 3: Null Environment Invariant")
class AuthSigningNullEnvPropertyTest {

    /**
     * Property 3: Null Environment Invariant
     *
     * <p><strong>Validates: Requirements 4.5, 8.4</strong></p>
     *
     * <p>For any initial header map state, when providerEnvironmentId is null,
     * beforeProviderRequest must leave the map completely unchanged.</p>
     */
    @Property(tries = 20)
    @Tag("Property_3_Null_Environment_Invariant")
    void headersUnmodifiedWhenProviderEnvironmentIdIsNull(
            @ForAll("arbitraryHeaderMaps") Map<String, String> initialHeaders) {

        // Given: A snapshot of the initial headers (deep copy for comparison)
        Map<String, String> expectedHeaders = new HashMap<>(initialHeaders);

        // And: A mutable headers map passed to the context
        Map<String, String> mutableHeaders = new HashMap<>(initialHeaders);

        // And: A ProviderRequestContext with null providerEnvironmentId
        ProviderRequestContext context = new ProviderRequestContext(
                "someDomain",
                "someSolution",
                null, // providerEnvironmentId is null
                "http://provider.example.com/requests",
                mutableHeaders
        );

        // And: An AuthSigningInterceptor (credential service is irrelevant since env is null)
        ProviderCredentialService credentialService = mock(ProviderCredentialService.class);
        AuthSigningInterceptor interceptor = new AuthSigningInterceptor(credentialService);

        // When: beforeProviderRequest is called
        interceptor.beforeProviderRequest(context);

        // Then: The headers map is identical to its initial state
        assertThat(mutableHeaders).hasSize(expectedHeaders.size());
        assertThat(mutableHeaders).containsExactlyInAnyOrderEntriesOf(expectedHeaders);
    }

    // ==================== Arbitraries (Data Generators) ====================

    /**
     * Generate random header maps with 0-10 entries using arbitrary keys and values.
     * Includes potential "authorization" and "timestamp" variants to ensure they are
     * NOT removed when providerEnvironmentId is null.
     *
     * <p>Keys use a fixed pool (no random generation) to avoid jqwik shrinking
     * instability when random strings collide with reserved names across seeds.</p>
     */
    @Provide
    Arbitrary<Map<String, String>> arbitraryHeaderMaps() {
        Arbitrary<String> headerKeys = Arbitraries.of(
                "authorization", "Authorization", "AUTHORIZATION",
                "timestamp", "Timestamp", "TIMESTAMP",
                "content-type", "X-Custom-Header",
                "x-forwarded-for", "accept", "cache-control",
                "x-request-id", "x-correlation-id", "host",
                "user-agent", "x-zone-id", "x-context-id",
                "x-environment-id", "x-solution-uuid", "fingerprint"
        );

        Arbitrary<String> headerValues = Arbitraries.strings()
                .withCharRange('!', '~') // printable ASCII excluding space (avoids blank-only values)
                .ofMinLength(1)
                .ofMaxLength(50);

        return Arbitraries.maps(headerKeys, headerValues)
                .ofMinSize(0)
                .ofMaxSize(10);
    }
}
