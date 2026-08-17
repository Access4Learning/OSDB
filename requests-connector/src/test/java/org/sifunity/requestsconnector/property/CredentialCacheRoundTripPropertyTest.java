// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sifunity.requestsconnector.auth.ProviderCredentialService;
import org.sifunity.requestsconnector.auth.ProviderCredentials;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based test for credential cache round-trip behavior.
 *
 * <p><strong>Property 5: Credential Cache Round-Trip</strong></p>
 *
 * <p>For any provider environment UUID and for any valid session token and shared secret
 * returned by the Environment Service, after the ProviderCredentialService successfully
 * fetches credentials, subsequent calls to getCredentials with the same UUID return the
 * same ProviderCredentials without making additional HTTP calls.</p>
 *
 * <p><strong>Validates: Requirements 7.1, 7.2</strong></p>
 * <ul>
 *   <li>7.1: Cache session token and shared secret keyed by provider environment UUID</li>
 *   <li>7.2: Use cached values without making HTTP calls while cache entry exists</li>
 * </ul>
 */
@Tag("Feature: provider-auth-signing, Property 5: Credential Cache Round-Trip")
class CredentialCacheRoundTripPropertyTest {

    /**
     * Property 5: Credential Cache Round-Trip
     *
     * <p><strong>Validates: Requirements 7.1, 7.2</strong></p>
     *
     * <p>For any provider environment UUID and valid session token/shared secret,
     * after the first successful fetch, subsequent calls return the same credentials
     * without additional HTTP calls to Environment Service.</p>
     */
    @Property(tries = 20)
    @Tag("Property_5_Credential_Cache_Round_Trip")
    @SuppressWarnings("unchecked")
    void cachedCredentialsReturnedWithoutAdditionalHttpCalls(
            @ForAll("validSessionTokens") String sessionToken,
            @ForAll("validSharedSecrets") String sharedSecret,
            @ForAll("subsequentCallCounts") int subsequentCalls) {

        // Given: A mocked WebClient that returns specific credentials
        UUID providerEnvironmentId = UUID.randomUUID();

        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        // First call for session-token, second call for shared-secret
        when(responseSpec.bodyToMono(String.class))
                .thenReturn(Mono.just(sessionToken))
                .thenReturn(Mono.just(sharedSecret));

        ProviderCredentialService service = new ProviderCredentialService(
                builder, "http://localhost:8080",
                "test-internal-token",
                Duration.ofMinutes(5), 1000);

        // When: First call fetches credentials (cache miss)
        Optional<ProviderCredentials> firstResult = service.getCredentials(providerEnvironmentId);

        // Then: First call should succeed and return credentials
        assertThat(firstResult).isPresent();
        assertThat(firstResult.get().sessionToken()).isEqualTo(sessionToken);
        assertThat(firstResult.get().sharedSecret()).isEqualTo(sharedSecret);

        // When: Subsequent calls with same UUID
        for (int i = 0; i < subsequentCalls; i++) {
            Optional<ProviderCredentials> cachedResult = service.getCredentials(providerEnvironmentId);

            // Then: Each subsequent call returns same credentials
            assertThat(cachedResult).isPresent();
            assertThat(cachedResult.get()).isEqualTo(firstResult.get());
            assertThat(cachedResult.get().sessionToken()).isEqualTo(sessionToken);
            assertThat(cachedResult.get().sharedSecret()).isEqualTo(sharedSecret);
        }

        // Then: WebClient.get() was only called for the initial fetch (2 calls: session-token + shared-secret)
        // The requestHeadersUriSpec.uri() should have been called exactly twice
        verify(webClient, times(2)).get();
    }

    // ==================== Arbitraries (Data Generators) ====================

    /**
     * Generate valid session tokens: non-blank ASCII strings (1-64 chars),
     * excluding control characters.
     */
    @Provide
    Arbitrary<String> validSessionTokens() {
        return Arbitraries.strings()
                .withCharRange('!', '~') // printable ASCII excluding space
                .ofMinLength(1)
                .ofMaxLength(64);
    }

    /**
     * Generate valid shared secrets: non-blank strings (8-128 chars), printable.
     */
    @Provide
    Arbitrary<String> validSharedSecrets() {
        return Arbitraries.strings()
                .withCharRange('!', '~') // printable ASCII excluding space
                .ofMinLength(8)
                .ofMaxLength(128);
    }

    /**
     * Generate subsequent call counts (2-10 calls after initial fetch).
     */
    @Provide
    Arbitrary<Integer> subsequentCallCounts() {
        return Arbitraries.integers().between(2, 10);
    }
}
