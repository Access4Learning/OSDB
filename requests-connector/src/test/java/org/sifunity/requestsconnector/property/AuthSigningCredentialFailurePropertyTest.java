// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.extension.ProviderRequestContext;
import org.sifunity.requestsconnector.auth.AuthSigningInterceptor;
import org.sifunity.requestsconnector.auth.ProviderCredentialService;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test for credential failure means no signing.
 *
 * <p><strong>Property 4: Credential Failure Means No Signing</strong></p>
 *
 * <p>For any ProviderRequestContext with a non-null providerEnvironmentId, if the
 * ProviderCredentialService returns Optional.empty() (simulating any retrieval failure —
 * network error, timeout, blank response, or missing environment), then after the
 * AuthSigningInterceptor executes beforeProviderRequest, the requestHeaders map SHALL NOT
 * contain an "authorization" key or a "timestamp" key (case-insensitive).</p>
 *
 * <p>The generator produces header maps that do NOT contain pre-existing authorization or
 * timestamp entries, reflecting the real-world scenario where HeaderSanitizer has already
 * stripped consumer auth headers upstream.</p>
 *
 * <p><strong>Validates: Requirements 8.1, 8.2, 8.3</strong></p>
 * <ul>
 *   <li>8.1: If session token cannot be retrieved, log ERROR and skip signing</li>
 *   <li>8.2: If shared secret cannot be retrieved, log ERROR and skip signing</li>
 *   <li>8.3: If signing is skipped due to credential failure, forward request without adding auth headers</li>
 * </ul>
 */
@Tag("Feature: provider-auth-signing, Property 4: Credential Failure Means No Signing")
class AuthSigningCredentialFailurePropertyTest {

    /**
     * Property 4: Credential Failure Means No Signing
     *
     * <p><strong>Validates: Requirements 8.1, 8.2, 8.3</strong></p>
     *
     * <p>When ProviderCredentialService returns Optional.empty(), the interceptor must not
     * inject authorization or timestamp headers into the request. The headers map (which
     * does not contain pre-existing auth/timestamp entries) must remain free of those keys
     * after the interceptor runs.</p>
     */
    @Property(tries = 20)
    @Tag("Property_4_Credential_Failure_Means_No_Signing")
    void noSigningHeadersWhenCredentialsFail(
            @ForAll("headerMapsWithoutAuth") Map<String, String> initialHeaders) {

        // Given: ProviderCredentialService returns empty (credential failure)
        ProviderCredentialService credentialService = mock(ProviderCredentialService.class);
        when(credentialService.getCredentials(any(UUID.class))).thenReturn(Optional.empty());

        AuthSigningInterceptor interceptor = new AuthSigningInterceptor(credentialService);

        // Use a mutable HashMap for the request context
        Map<String, String> requestHeaders = new HashMap<>(initialHeaders);

        UUID providerEnvironmentId = UUID.randomUUID();
        ProviderRequestContext context = new ProviderRequestContext(
                "domain-1", "solution-1", providerEnvironmentId,
                "https://provider.example.com/requests", requestHeaders);

        // When: The interceptor executes beforeProviderRequest
        interceptor.beforeProviderRequest(context);

        // Then: No key in the map matches "authorization" case-insensitively
        assertThat(context.requestHeaders().keySet().stream()
                .noneMatch(k -> k.equalsIgnoreCase("authorization")))
                .as("No 'authorization' header (any case) should be present after credential failure")
                .isTrue();

        // And: No key in the map matches "timestamp" case-insensitively
        assertThat(context.requestHeaders().keySet().stream()
                .noneMatch(k -> k.equalsIgnoreCase("timestamp")))
                .as("No 'timestamp' header (any case) should be present after credential failure")
                .isTrue();
    }

    // ==================== Arbitraries (Data Generators) ====================

    /**
     * Generate random header maps (0-10 entries) that do NOT contain authorization
     * or timestamp keys (any case variant). This reflects the real scenario where
     * HeaderSanitizer has already stripped consumer auth headers upstream before
     * the interceptor runs.
     */
    @Provide
    Arbitrary<Map<String, String>> headerMapsWithoutAuth() {
        // Header keys that are NOT authorization or timestamp
        Arbitrary<String> safeHeaderKeys = Arbitraries.of(
                "content-type", "accept", "x-request-id",
                "x-forwarded-for", "host", "user-agent",
                "x-custom-header", "cache-control", "connection",
                "x-correlation-id", "x-zone-id", "x-context-id"
        );

        Arbitrary<String> headerValues = Arbitraries.strings()
                .withCharRange('!', '~')
                .ofMinLength(1)
                .ofMaxLength(64);

        return Arbitraries.maps(safeHeaderKeys, headerValues)
                .ofMinSize(0)
                .ofMaxSize(10);
    }
}
