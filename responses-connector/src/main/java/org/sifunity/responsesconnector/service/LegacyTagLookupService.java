// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.responsesconnector.client.EnvironmentServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cached lookup service for the {@code SIF_LEGACY_MATRIX_PARAMS} entity tag.
 *
 * <p>Queries the Environment Service to determine whether a given environment
 * has the legacy matrix parameter tag set to {@code "true"}. Results are cached
 * using a Caffeine in-process cache with a configurable TTL (default 5 minutes)
 * to avoid repeated HTTP calls for the same environment.</p>
 *
 * <p><b>Requirements:</b> 6.1, 6.2, 6.3, 6.4</p>
 *
 * @see EnvironmentServiceClient#getEntityTagValue(String, String, String, UUID, String)
 */
@Service
@Slf4j
public class LegacyTagLookupService {

    private static final String TAG_KEY = "SIF_LEGACY_MATRIX_PARAMS";
    private static final String ENTITY_TYPE = "ENVIRONMENT";

    private final EnvironmentServiceClient environmentServiceClient;
    private final Cache<UUID, Boolean> cache;

    public LegacyTagLookupService(
            EnvironmentServiceClient environmentServiceClient,
            @Value("${sif.legacy-tag.cache-ttl:5m}") Duration cacheTtl) {
        this.environmentServiceClient = environmentServiceClient;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .maximumSize(1000)
                .build();
        log.info("LegacyTagLookupService initialized: cacheTtl={}", cacheTtl);
    }

    /**
     * Returns {@code true} if the environment has the {@code SIF_LEGACY_MATRIX_PARAMS=true} tag.
     *
     * <p>Results are cached by {@code environmentId} with the configured TTL.
     * Returns {@code false} if the tag is absent, has a non-"true" value, or
     * if the lookup fails for any reason.</p>
     *
     * @param domainId      the domain ID (string) for the environment service call
     * @param solutionId    the solution ID (string) for the environment service call
     * @param environmentId the UUID of the environment to check
     * @return {@code true} if the environment expects legacy matrix parameters
     */
    public boolean isLegacyEnvironment(String domainId, String solutionId, UUID environmentId) {
        return cache.get(environmentId, id -> lookupTag(domainId, solutionId, id));
    }

    private boolean lookupTag(String domainId, String solutionId, UUID environmentId) {
        log.debug("Cache miss — looking up legacy tag: environmentId={}", environmentId);

        Optional<String> tagValue = environmentServiceClient.getEntityTagValue(
                domainId, solutionId, ENTITY_TYPE, environmentId, TAG_KEY);

        boolean legacy = tagValue.map("true"::equalsIgnoreCase).orElse(false);
        log.debug("Legacy tag lookup result: environmentId={}, legacy={}", environmentId, legacy);
        return legacy;
    }
}
