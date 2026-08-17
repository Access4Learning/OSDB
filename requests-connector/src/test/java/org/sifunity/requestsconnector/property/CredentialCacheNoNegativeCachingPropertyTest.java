// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import com.github.benmanes.caffeine.cache.Cache;
import net.jqwik.api.*;
import org.sifunity.requestsconnector.auth.ProviderCredentialService;
import org.sifunity.requestsconnector.auth.ProviderCredentials;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based test for no negative caching in ProviderCredentialService.
 *
 * <p><strong>Property 6: No Negative Caching</strong></p>
 *
 * <p><strong>Validates: Requirements 7.4</strong></p>
 *
 * <p>For any provider environment UUID where credential retrieval from the Environment
 * Service fails (returns empty or throws), and for any prior state of the credential
 * cache, the cache SHALL NOT contain a new entry for that UUID after the failed retrieval,
 * and any previously cached entry for a different UUID SHALL remain intact.</p>
 */
@Tag("Feature: provider-auth-signing, Property 6: No Negative Caching")
class CredentialCacheNoNegativeCachingPropertyTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ProviderCredentialService buildServiceWithFailingWebClient(FailureMode failureMode) {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient webClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn(headersSpec);
        when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        switch (failureMode) {
            case RETURNS_NULL -> when(responseSpec.bodyToMono(String.class))
                    .thenReturn(Mono.justOrEmpty(null));
            case RETURNS_BLANK -> when(responseSpec.bodyToMono(String.class))
                    .thenReturn(Mono.just("   "));
            case THROWS_EXCEPTION -> when(responseSpec.bodyToMono(String.class))
                    .thenReturn(Mono.error(new RuntimeException("Connection refused")));
            case THROWS_404 -> when(responseSpec.bodyToMono(String.class))
                    .thenReturn(Mono.error(WebClientResponseException.create(404, "Not Found", null, null, null)));
        }

        return new ProviderCredentialService(builder, "http://localhost:8080", "test-internal-token", Duration.ofMinutes(5), 1000);
    }

    @SuppressWarnings("unchecked")
    private Cache<UUID, ProviderCredentials> getInternalCache(ProviderCredentialService service) {
        try {
            Field cacheField = ProviderCredentialService.class.getDeclaredField("cache");
            cacheField.setAccessible(true);
            return (Cache<UUID, ProviderCredentials>) cacheField.get(service);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access internal cache via reflection", e);
        }
    }

    /**
     * Property 6: No Negative Caching
     *
     * <p><strong>Validates: Requirements 7.4</strong></p>
     *
     * <p>For any provider environment UUID where credential retrieval fails, the cache
     * SHALL NOT contain a new entry for that UUID after the failed retrieval, and any
     * previously cached entry for a different UUID SHALL remain intact.</p>
     */
    @Property(tries = 20)
    @Tag("Feature: provider-auth-signing, Property 6: No Negative Caching")
    void failedRetrievalDoesNotCacheAndPreservesExistingEntries(
            @ForAll("uuids") UUID failingUuid,
            @ForAll("uuids") UUID existingUuid,
            @ForAll("failureModes") FailureMode failureMode,
            @ForAll("sessionTokens") String existingSessionToken,
            @ForAll("sharedSecrets") String existingSharedSecret) {

        // Ensure the two UUIDs are different
        Assume.that(!failingUuid.equals(existingUuid));

        // Build service with WebClient that will fail for any request
        ProviderCredentialService service = buildServiceWithFailingWebClient(failureMode);
        Cache<UUID, ProviderCredentials> cache = getInternalCache(service);

        // Pre-populate cache with an existing entry for a different UUID
        ProviderCredentials existingCreds = new ProviderCredentials(existingSessionToken, existingSharedSecret);
        cache.put(existingUuid, existingCreds);

        // Attempt to get credentials for the failing UUID
        Optional<ProviderCredentials> result = service.getCredentials(failingUuid);

        // Assert: result is empty (retrieval failed)
        assertThat(result).isEmpty();

        // Assert: no cache entry created for the failing UUID
        assertThat(cache.getIfPresent(failingUuid)).isNull();

        // Assert: pre-existing entry for different UUID remains intact
        ProviderCredentials preserved = cache.getIfPresent(existingUuid);
        assertThat(preserved).isNotNull();
        assertThat(preserved.sessionToken()).isEqualTo(existingSessionToken);
        assertThat(preserved.sharedSecret()).isEqualTo(existingSharedSecret);
    }

    /**
     * Supplementary property: Failed retrieval with empty cache does not create entry.
     *
     * <p>For any provider environment UUID where retrieval fails and the cache is
     * initially empty, the cache SHALL remain empty after the failed call.</p>
     */
    @Property(tries = 20)
    @Tag("Feature: provider-auth-signing, Property 6: No Negative Caching")
    void failedRetrievalWithEmptyCacheCreatesNoEntry(
            @ForAll("uuids") UUID failingUuid,
            @ForAll("failureModes") FailureMode failureMode) {

        // Build service with WebClient that will fail
        ProviderCredentialService service = buildServiceWithFailingWebClient(failureMode);
        Cache<UUID, ProviderCredentials> cache = getInternalCache(service);

        // Cache starts empty
        assertThat(cache.estimatedSize()).isZero();

        // Attempt to get credentials
        Optional<ProviderCredentials> result = service.getCredentials(failingUuid);

        // Assert: result is empty
        assertThat(result).isEmpty();

        // Assert: cache still empty (no negative caching)
        assertThat(cache.getIfPresent(failingUuid)).isNull();
        assertThat(cache.estimatedSize()).isZero();
    }

    // ==================== Failure Modes ====================

    enum FailureMode {
        RETURNS_NULL,
        RETURNS_BLANK,
        THROWS_EXCEPTION,
        THROWS_404
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<FailureMode> failureModes() {
        return Arbitraries.of(FailureMode.values());
    }

    @Provide
    Arbitrary<String> sessionTokens() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(8)
                .ofMaxLength(64);
    }

    @Provide
    Arbitrary<String> sharedSecrets() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(8)
                .ofMaxLength(128);
    }
}
