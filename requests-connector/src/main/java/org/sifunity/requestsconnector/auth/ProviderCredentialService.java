// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for retrieving and caching provider environment credentials.
 *
 * <p>Fetches session tokens and shared secrets from the Environment Service's internal
 * endpoints and caches them using Caffeine. On any retrieval failure (exception, blank,
 * null, or timeout), returns {@link Optional#empty()} without caching the failure.</p>
 *
 * <p>Requirements:</p>
 * <ul>
 *   <li>7.1: Cache session token and shared secret keyed by provider environment UUID</li>
 *   <li>7.2: Use cached values without making HTTP calls while cache entry exists</li>
 *   <li>7.3: Expire entries after configurable TTL (default 5m), max 1000 entries</li>
 *   <li>7.4: Do not cache failures; do not evict existing entries on failure</li>
 *   <li>7.5: Return empty on failure when no cached entry exists</li>
 *   <li>8.5: Treat retrieval exceeding 5s as failure</li>
 * </ul>
 */
@Service
@Slf4j
public class ProviderCredentialService {

    private final WebClient webClient;
    private final Cache<UUID, ProviderCredentials> cache;
    private final String internalServiceToken;

    /**
     * Constructs a ProviderCredentialService with WebClient and Caffeine cache.
     *
     * @param webClientBuilder WebClient builder for HTTP calls
     * @param baseUrl          Environment Service base URL
     * @param internalServiceToken the internal service token for authenticating calls
     * @param cacheTtl         cache time-to-live duration (default 5 minutes)
     * @param maxSize          maximum number of cached entries (default 1000)
     */
    public ProviderCredentialService(
            WebClient.Builder webClientBuilder,
            @Value("${environment.service.url}") String baseUrl,
            @Value("${broker.internal-service-token:}") String internalServiceToken,
            @Value("${auth.signing.cache-ttl:5m}") Duration cacheTtl,
            @Value("${auth.signing.cache-max-size:1000}") int maxSize) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.internalServiceToken = internalServiceToken;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .maximumSize(maxSize)
                .build();
        log.info("ProviderCredentialService initialized with cache TTL: {}, max size: {}", cacheTtl, maxSize);
    }

    /**
     * Returns credentials for the given provider environment, using cache.
     *
     * <p>On cache hit, returns cached credentials immediately. On cache miss, fetches
     * from Environment Service internal endpoints with a 5-second timeout per call.
     * Returns {@link Optional#empty()} on any retrieval failure without caching.</p>
     *
     * @param providerEnvironmentId the provider environment UUID
     * @return Optional containing credentials if available, empty otherwise
     */
    public Optional<ProviderCredentials> getCredentials(UUID providerEnvironmentId) {
        ProviderCredentials cached = cache.getIfPresent(providerEnvironmentId);
        if (cached != null) {
            return Optional.of(cached);
        }
        return fetchAndCache(providerEnvironmentId);
    }

    private Optional<ProviderCredentials> fetchAndCache(UUID envId) {
        try {
            String sessionToken = webClient.get()
                    .uri("/internal/environments/{id}/session-token", envId)
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5));

            if (sessionToken == null || sessionToken.isBlank()) {
                log.error("Failed to retrieve credentials for providerEnvironmentId={}: session token is null/blank", envId);
                return Optional.empty();
            }

            String sharedSecret = webClient.get()
                    .uri("/internal/environments/{id}/shared-secret", envId)
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5));

            if (sharedSecret == null || sharedSecret.isBlank()) {
                log.error("Failed to retrieve credentials for providerEnvironmentId={}: shared secret is null/blank", envId);
                return Optional.empty();
            }

            ProviderCredentials creds = new ProviderCredentials(sessionToken, sharedSecret);
            cache.put(envId, creds);
            return Optional.of(creds);

        } catch (Exception e) {
            log.error("Failed to retrieve credentials for providerEnvironmentId={}", envId, e);
            return Optional.empty();
        }
    }
}
