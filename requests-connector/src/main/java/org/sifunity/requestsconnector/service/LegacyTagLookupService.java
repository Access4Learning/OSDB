// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.requestsconnector.client.EnvironmentServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cached lookup service for the SIF_LEGACY_MATRIX_PARAMS entity tag.
 *
 * <p>Determines whether a recipient environment expects legacy SIF 3.3 matrix
 * parameters in the {@code relativeServicePath} header. Results are cached with
 * a configurable TTL (default 5 minutes) to avoid querying the Environment Service
 * on every delivery.</p>
 *
 * <p>Requirements:</p>
 * <ul>
 *   <li>6.1: Query entity tag from Environment Service</li>
 *   <li>6.2: Return false (modern format) when tag absent</li>
 *   <li>6.3: Cache lookup results with configurable TTL</li>
 *   <li>6.4: Reflect tag changes after cache TTL expires</li>
 * </ul>
 */
@Service
@Slf4j
public class LegacyTagLookupService {

    private static final String TAG_KEY = "SIF_LEGACY_MATRIX_PARAMS";
    private static final String ENTITY_TYPE = "ENVIRONMENT";

    private final EnvironmentServiceClient environmentServiceClient;
    private final Cache<UUID, Boolean> cache;

    /**
     * Constructs a LegacyTagLookupService with the configured cache TTL.
     *
     * @param environmentServiceClient client for querying entity tags
     * @param cacheTtl                 cache time-to-live duration (default 5 minutes)
     */
    public LegacyTagLookupService(
            EnvironmentServiceClient environmentServiceClient,
            @Value("${sif.legacy-tag.cache-ttl:5m}") Duration cacheTtl) {
        this.environmentServiceClient = environmentServiceClient;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .maximumSize(1000)
                .build();
        log.info("LegacyTagLookupService initialized with cache TTL: {}", cacheTtl);
    }

    /**
     * Returns true if the environment has SIF_LEGACY_MATRIX_PARAMS=true tag.
     *
     * <p>Results are cached by environmentId with configurable TTL. On cache miss,
     * queries the Environment Service entity tag API. Returns false on any error
     * or when the tag is absent (fail-open to modern format).</p>
     *
     * @param domainId      the domain ID for the API call
     * @param solutionId    the solution ID for the API call
     * @param environmentId the environment UUID to check
     * @return true if environment expects legacy matrix parameters, false otherwise
     */
    public boolean isLegacyEnvironment(String domainId, String solutionId, UUID environmentId) {
        Boolean cached = cache.get(environmentId,
                id -> queryLegacyTag(domainId, solutionId, id));
        return Boolean.TRUE.equals(cached);
    }

    private boolean queryLegacyTag(String domainId, String solutionId, UUID environmentId) {
        log.debug("Querying legacy tag for environment: domainId={}, solutionId={}, environmentId={}",
                domainId, solutionId, environmentId);

        Optional<String> tagValue = environmentServiceClient.getEntityTagValue(
                domainId, solutionId, ENTITY_TYPE, environmentId, TAG_KEY);

        boolean isLegacy = tagValue.map("true"::equalsIgnoreCase).orElse(false);
        log.debug("Legacy tag result for environmentId={}: {}", environmentId, isLegacy);
        return isLegacy;
    }
}
