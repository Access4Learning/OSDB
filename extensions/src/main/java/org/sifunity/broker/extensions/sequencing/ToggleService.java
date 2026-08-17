package org.sifunity.broker.extensions.sequencing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.extensions.config.SequencingProperties;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Determines whether sequencing is enabled for a given solution by querying
 * the entity-tags REST API and caching the result.
 *
 * <p>Queries {@code GET {baseUrl}/{domainId}/{solutionId}/tags?entityType=SOLUTION&tagKey=SEQUENCING_ENABLED}
 * and returns {@code true} only if a matching tag exists with value exactly {@code "true"}.
 * Any other value, empty result, or error returns {@code false} (fail-open).</p>
 *
 * <p>Results are cached in a Caffeine cache with configurable TTL to avoid
 * querying the entity-tags API on every request.</p>
 */
@Slf4j
public class ToggleService {

    private final RestTemplate restTemplate;
    private final SequencingProperties properties;
    private final Cache<String, Boolean> toggleCache;

    public ToggleService(RestTemplate restTemplate, SequencingProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.toggleCache = Caffeine.newBuilder()
                .expireAfterWrite(properties.getToggleCacheTtl())
                .build();
    }

    /**
     * Returns {@code true} if sequencing is enabled for the given domain/solution pair.
     *
     * <p>Checks the Caffeine cache first. On cache miss, queries the entity-tags
     * REST API. The result (including {@code false} for errors) is cached until TTL expires.</p>
     *
     * @param domainId   the domain identifier from the URL path
     * @param solutionId the solution identifier from the URL path
     * @return {@code true} if sequencing is enabled, {@code false} otherwise
     */
    public boolean isSequencingEnabled(String domainId, String solutionId) {
        String cacheKey = domainId + ":" + solutionId;
        Boolean cached = toggleCache.get(cacheKey, k -> queryEntityTag(domainId, solutionId));
        if (log.isTraceEnabled() && cached != null) {
            log.trace("Toggle lookup for domain={}, solution={}: enabled={}", domainId, solutionId, cached);
        }
        return Boolean.TRUE.equals(cached);
    }

    /**
     * Queries the entity-tags REST API for the SEQUENCING_ENABLED tag on the given solution.
     *
     * <p>Uses the list endpoint with query params to avoid needing the solution's internal UUID:
     * {@code GET {baseUrl}/{domainId}/{solutionId}/tags?entityType=SOLUTION&tagKey=SEQUENCING_ENABLED}</p>
     *
     * <p>Returns {@code true} only if a matching tag exists with value exactly {@code "true"}.
     * Returns {@code false} on 404 (tag not found), any other value, or any error.
     * Exceptions are caught and logged so the cache stores {@code false} for errors
     * (re-queried after TTL).</p>
     */
    private boolean queryEntityTag(String domainId, String solutionId) {
        try {
            String url = properties.getEntityTagsBaseUrl()
                    + "/" + domainId + "/" + solutionId
                    + "/tags?entityType=SOLUTION&tagKey=SEQUENCING_ENABLED";

            // The list endpoint returns an array of EntityTagDTO objects
            EntityTagResult[] tags = restTemplate.getForObject(url, EntityTagResult[].class);
            if (tags != null && tags.length > 0) {
                return "true".equals(tags[0].tagValue());
            }
            return false;
        } catch (HttpClientErrorException.NotFound ex) {
            log.trace("SEQUENCING_ENABLED tag not found for domain={}, solution={}, treating as disabled",
                    domainId, solutionId);
            return false;
        } catch (Exception ex) {
            log.warn("Failed to query SEQUENCING_ENABLED tag for domain={}, solution={}: {}",
                    domainId, solutionId, ex.getMessage());
            return false;
        }
    }

    /**
     * Minimal record to deserialize the entity-tag list response.
     * Only the tagValue field is needed for the toggle check.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record EntityTagResult(String tagValue) {}
}
