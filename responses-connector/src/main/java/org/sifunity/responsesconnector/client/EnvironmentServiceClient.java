// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.sifunity.responsesconnector.dto.AnyGrantCheckResult;

import java.util.Optional;
import java.util.UUID;

/**
 * HTTP client for communicating with the Environment Service.
 *
 * <p>Provides methods for querying entity tags used by feature toggles
 * such as {@code SIF_LEGACY_MATRIX_PARAMS}. Uses Spring's synchronous
 * {@link RestClient} with JDK HttpClient transport — consistent with
 * the other clients in this module.</p>
 *
 * <p><b>Requirements:</b> 6.1, 6.3</p>
 */
@Component
@Slf4j
public class EnvironmentServiceClient {

    private final RestClient restClient;

    @Value("${broker.internal-service-token:}")
    private String internalServiceToken;

    public EnvironmentServiceClient(
            @Qualifier("environmentServiceRestClient") RestClient restClient,
            @Value("${environment.service.url}") String baseUrl) {
        this.restClient = restClient;
        log.info("EnvironmentServiceClient initialized: baseUrl={}", baseUrl);
    }

    /**
     * Looks up an entity tag value from the Environment Service internal API.
     *
     * <p>Calls the internal entity tag endpoint to retrieve a tag value for a given
     * entity type, entity ID, and tag key. Used by {@code LegacyTagLookupService} to
     * check the {@code SIF_LEGACY_MATRIX_PARAMS} feature toggle per environment.</p>
     *
     * @param domainId   the domain ID for the request path
     * @param solutionId the solution ID for the request path
     * @param entityType the entity type (e.g., "ENVIRONMENT")
     * @param entityId   the entity UUID
     * @param tagKey     the tag key to look up (e.g., "SIF_LEGACY_MATRIX_PARAMS")
     * @return Optional containing the tag value if found, empty if tag does not exist
     */
    public Optional<String> getEntityTagValue(String domainId, String solutionId,
            String entityType, UUID entityId, String tagKey) {

        log.debug("Looking up entity tag: domainId={}, solutionId={}, entityType={}, entityId={}, tagKey={}",
            domainId, solutionId, entityType, entityId, tagKey);

        try {
            String tagValue = restClient.get()
                .uri("/internal/{domainId}/{solutionId}/entity-tags?entityType={entityType}&entityId={entityId}&tagKey={tagKey}",
                    domainId, solutionId, entityType, entityId, tagKey)
                .header("X-Internal-Service-Token", internalServiceToken)
                .retrieve()
                .body(String.class);

            log.debug("Entity tag found: tagKey={}, tagValue={}", tagKey, tagValue);
            return Optional.ofNullable(tagValue);

        } catch (HttpClientErrorException.NotFound e) {
            log.debug("Entity tag not found: entityType={}, entityId={}, tagKey={}", entityType, entityId, tagKey);
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            log.error("Environment service returned error looking up entity tag: status={}, entityType={}, entityId={}, tagKey={}",
                e.getStatusCode(), entityType, entityId, tagKey);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to look up entity tag: entityType={}, entityId={}, tagKey={}", entityType, entityId, tagKey, e);
            return Optional.empty();
        }
    }

    /**
     * Checks whether an environment holds ANY approved ACL grant for a service in a zone/context.
     *
     * <p>Used by the response-destination entitlement gate: the environment checked is the
     * <strong>owner of the caller-supplied destination queue</strong>, not the caller. A provider
     * legitimately names the consumer's queue, so the question is not "do you own this queue" but
     * "is the queue's owner entitled to this object's data at all".</p>
     *
     * <p>Access type is deliberately not a parameter. A delayed response is delivered to the queue
     * of the consumer that issued the original request, and that consumer may hold only
     * {@code CREATE}/{@code UPDATE}/{@code DELETE} rather than {@code QUERY} — requiring a specific
     * type would reject legitimate responses.</p>
     *
     * <p><strong>Fails closed.</strong> Every failure mode — 404, 4xx, 5xx, malformed body,
     * transport error, absent body — returns {@code false}, i.e. deny. The caller must not
     * distinguish "no grant" from "could not determine"; both mean the response is not delivered.
     * Callers that cache this result must therefore cache <em>positives only</em>, or a momentary
     * environment-service outage would become a sticky denial.</p>
     *
     * @param domainId      the domain ID for the request path
     * @param solutionId    the solution ID for the request path
     * @param environmentId the environment whose entitlement is checked (the queue owner)
     * @param serviceName   the SIF object/service name being delivered
     * @param zoneId        the zone qualifying the service
     * @param contextId     the context qualifying the service
     * @return true only if entitlement was positively confirmed
     */
    public boolean hasAnyGrant(String domainId, String solutionId, UUID environmentId,
            String serviceName, String zoneId, String contextId) {

        log.debug("Checking destination entitlement: environmentId={}, service={}, zone={}, context={}",
            environmentId, serviceName, zoneId, contextId);

        try {
            AnyGrantCheckResult result = restClient.post()
                .uri("/internal/check-any-permission")
                .header("X-Internal-Service-Token", internalServiceToken)
                .body(new AnyGrantCheckRequestBody(environmentId, serviceName, zoneId, contextId))
                .retrieve()
                .body(AnyGrantCheckResult.class);

            if (result == null) {
                log.warn("Entitlement check returned no body — denying: environmentId={}, service={}",
                    environmentId, serviceName);
                return false;
            }

            log.debug("Destination entitlement resolved: environmentId={}, service={}, granted={}",
                environmentId, serviceName, result.granted());
            return result.granted();

        } catch (HttpClientErrorException e) {
            log.warn("Entitlement check rejected by environment-service — denying: status={}, environmentId={}, service={}",
                e.getStatusCode(), environmentId, serviceName);
            return false;
        } catch (Exception e) {
            log.warn("Entitlement check failed — denying: environmentId={}, service={}, error={}",
                environmentId, serviceName, e.getMessage());
            return false;
        }
    }

    /**
     * Request body for the any-grant entitlement check.
     *
     * <p>Field names must match {@code AnyGrantCheckRequest} in environment-service.</p>
     */
    private record AnyGrantCheckRequestBody(UUID environmentId, String serviceName,
                                            String zoneId, String contextId) {
    }
}
