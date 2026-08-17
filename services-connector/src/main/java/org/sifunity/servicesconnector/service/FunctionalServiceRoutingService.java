// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.service;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.servicesconnector.cache.ProviderRegistryCache;
import org.sifunity.servicesconnector.client.EnvironmentServiceClient;
import org.sifunity.servicesconnector.client.ProviderHttpClient;
import org.sifunity.servicesconnector.client.QueueServiceClient;
import org.sifunity.servicesconnector.exception.NoProviderException;
import org.sifunity.servicesconnector.util.MessageBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import org.sifunity.broker.common.dto.ProtocolDTO;

/**
 * Routes functional service requests to the registered provider.
 *
 * <p>Follows the same pass-through proxy pattern as the requests-connector's
 * {@code RoutingService}. The broker does not persist job/phase state — all
 * state is managed by the external provider.</p>
 *
 * <p><strong>IMMEDIATE Flow:</strong></p>
 * <ol>
 *   <li>Query {@link ProviderRegistryCache} with functionalServiceName, zoneId, contextId</li>
 *   <li>If no provider found → throw {@link NoProviderException} (HTTP 404)</li>
 *   <li>Build {@link SifMessageEnvelope} with serviceType=FUNCTIONAL</li>
 *   <li>Construct provider URL path suffix from path segments</li>
 *   <li>Forward raw body + sanitized headers to provider endpoint</li>
 *   <li>Return provider's response unchanged</li>
 * </ol>
 *
 * <p><strong>DELAYED Flow:</strong></p>
 * <ol>
 *   <li>Query {@link ProviderRegistryCache} with functionalServiceName, zoneId, contextId</li>
 *   <li>If no provider found → check if PROVIDE ACL exists via Environment Service</li>
 *   <li>If PROVIDE ACL exists → accept with queue-only routing (202 Accepted)</li>
 *   <li>If neither provider nor PROVIDE ACL exists → throw {@link NoProviderException} (HTTP 404)</li>
 *   <li>If provider found → return 202 Accepted immediately (async forwarding via DelayedRequestHandler)</li>
 * </ol>
 *
 * <p><strong>Requirements:</strong> 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 5.1, 5.2, 5.3, 5.6, 5.7</p>
 *
 * @see ProviderRegistryCache
 * @see NoProviderException
 */
@Service
@Slf4j
public class FunctionalServiceRoutingService {

    private final ProviderRegistryCache providerCache;
    private final ProviderHttpClient providerHttpClient;
    private final TracingUtilsBase tracingUtils;
    private final MessageBuilder messageBuilder;
    private final DelayedRequestHandler delayedRequestHandler;
    private final EnvironmentServiceClient environmentServiceClient;
    private final QueueServiceClient queueClient;
    private final ConsumerQueueOwnershipValidator queueOwnershipValidator;

    public FunctionalServiceRoutingService(
            ProviderRegistryCache providerCache,
            ProviderHttpClient providerHttpClient,
            TracingUtilsBase tracingUtils,
            MessageBuilder messageBuilder,
            DelayedRequestHandler delayedRequestHandler,
            EnvironmentServiceClient environmentServiceClient,
            QueueServiceClient queueClient,
            ConsumerQueueOwnershipValidator queueOwnershipValidator) {
        this.providerCache = providerCache;
        this.providerHttpClient = providerHttpClient;
        this.tracingUtils = tracingUtils;
        this.messageBuilder = messageBuilder;
        this.delayedRequestHandler = delayedRequestHandler;
        this.environmentServiceClient = environmentServiceClient;
        this.queueClient = queueClient;
        this.queueOwnershipValidator = queueOwnershipValidator;
        log.info("FunctionalServiceRoutingService initialized");
    }

    /**
     * Routes an IMMEDIATE request synchronously to the provider and returns
     * the provider's response unchanged.
     *
     * <p>Orchestrates the IMMEDIATE request flow:</p>
     * <ol>
     *   <li>Queries provider registry (cached) with functionalServiceName, zoneId, contextId</li>
     *   <li>Throws {@link NoProviderException} if no provider found (HTTP 404)</li>
     *   <li>Validates provider supports IMMEDIATE pattern</li>
     *   <li>Builds a {@link SifMessageEnvelope} with serviceType=FUNCTIONAL</li>
     *   <li>Constructs provider URL from endpoint + path suffix</li>
     *   <li>Forwards raw body + sanitized headers to provider</li>
     *   <li>Returns provider's response unchanged</li>
     * </ol>
     *
     * @param httpMethod           the HTTP method (GET, POST, PUT, DELETE)
     * @param functionalServiceName the functional service name from the URL path
     * @param jobId                the job ID (may be null for service-level operations)
     * @param pathSuffix           additional path segments after jobId (e.g., phaseName, phaseName/states)
     * @param environmentId        the consumer's environment ID
     * @param environmentName      the consumer's environment name
     * @param domainId             the domain ID from the URL path
     * @param solutionId           the solution ID from the URL path
     * @param zoneId               the zone ID from SIF header
     * @param contextId            the context ID from SIF header (may be null)
     * @param requestBody          the raw request body (may be null for GET/DELETE)
     * @param passthroughHeaders   sanitized headers to forward to the provider
     * @return the provider's response body unchanged
     * @throws NoProviderException if no provider is registered for the service/zone/context
     */
    public Object routeImmediateRequest(
            String httpMethod,
            String functionalServiceName,
            String jobId,
            String pathSuffix,
            UUID environmentId,
            String environmentName,
            String domainId,
            String solutionId,
            String zoneId,
            String contextId,
            Object requestBody,
            Map<String, String> passthroughHeaders) {

        return tracingUtils.traceOperation(
            "sif.functional.immediate",
            Map.of(
                "sif.message_type", "REQUEST",
                "sif.service_type", "FUNCTIONAL",
                "sif.service_name", functionalServiceName,
                "sif.zone_id", zoneId,
                "sif.context_id", contextId != null ? contextId : "DEFAULT",
                "sif.pattern", "IMMEDIATE",
                "sif.http_method", httpMethod
            ),
            () -> {
                log.info("Routing IMMEDIATE functional service request: {} /{}, zoneId={}, contextId={}, environmentId={}",
                        httpMethod, functionalServiceName, zoneId, contextId, environmentId);

                // 1. Query provider registry (cached)
                ProviderRegistrationDTO provider = providerCache.getProvider(
                        domainId, solutionId, functionalServiceName, zoneId, contextId)
                    .orElseThrow(() -> new NoProviderException(
                        functionalServiceName, zoneId, contextId));

                log.debug("Found provider: endpoint={}, supportedRequestTypes={}",
                    ProtocolDTO.locationOf(provider.endPoint()), provider.supportedRequestTypes());

                // 2. Validate provider supports IMMEDIATE pattern
                if (provider.supportedRequestTypes().supportedRequestType().stream()
                        .noneMatch(srt -> srt.requestType() == RequestType.IMMEDIATE)) {
                    throw new IllegalArgumentException(
                        "Provider for '" + functionalServiceName + "' does not support IMMEDIATE pattern");
                }

                // 3. Build provider URL with path suffix
                String providerUrl = buildProviderUrl(ProtocolDTO.locationOf(provider.endPoint()), functionalServiceName, jobId, pathSuffix);

                log.info("Forwarding to provider: {} {}", httpMethod, providerUrl);

                // 4. Build SIF message envelope via MessageBuilder
                SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
                    functionalServiceName, environmentId, zoneId, contextId,
                    requestBody, passthroughHeaders);

                // 5. Forward to provider via ProviderHttpClient and return response unchanged
                return providerHttpClient.sendRequest(
                    providerUrl, HttpMethod.valueOf(httpMethod.toUpperCase()),
                    envelope, passthroughHeaders);
            }
        );
    }

    /**
     * Routes a DELAYED request: validates the provider exists, returns a 202 Accepted
     * response object immediately. Full async forwarding and queue delivery will be
     * implemented in Task 8.1 with {@code DelayedRequestHandler}.
     *
     * @param httpMethod           the HTTP method (GET, POST, PUT, DELETE)
     * @param functionalServiceName the functional service name from the URL path
     * @param jobId                the job ID (may be null for service-level operations)
     * @param pathSuffix           additional path segments after jobId
     * @param environmentId        the consumer's environment ID
     * @param environmentName      the consumer's environment name
     * @param domainId             the domain ID from the URL path
     * @param solutionId           the solution ID from the URL path
     * @param zoneId               the zone ID from SIF header
     * @param contextId            the context ID from SIF header (may be null)
     * @param queueId              the consumer's queue ID for response delivery
     * @param requestBody          the raw request body (may be null for GET/DELETE)
     * @param passthroughHeaders   sanitized headers to forward to the provider
     * @return a map containing the accepted messageId for correlation
     * @throws NoProviderException if neither a provider registration nor a PROVIDE ACL exists for the service/zone/context
     */
    public Object routeDelayedRequest(
            String httpMethod,
            String functionalServiceName,
            String jobId,
            String pathSuffix,
            UUID environmentId,
            String environmentName,
            String domainId,
            String solutionId,
            String zoneId,
            String contextId,
            UUID queueId,
            String requestId,
            Object requestBody,
            Map<String, String> passthroughHeaders) {

        return tracingUtils.traceOperation(
            "sif.functional.delayed",
            Map.of(
                "sif.message_type", "REQUEST",
                "sif.service_type", "FUNCTIONAL",
                "sif.service_name", functionalServiceName,
                "sif.zone_id", zoneId,
                "sif.context_id", contextId != null ? contextId : "DEFAULT",
                "sif.pattern", "DELAYED",
                "sif.http_method", httpMethod,
                "sif.queue_id", queueId.toString()
            ),
            () -> {
                log.info("Routing DELAYED functional service request: {} /{}, zoneId={}, contextId={}, queueId={}, environmentId={}",
                        httpMethod, functionalServiceName, zoneId, contextId, queueId, environmentId);

                // Validate queue ownership FIRST — reject unauthorized before revealing provider info
                queueOwnershipValidator.requireOwnership(domainId, solutionId, queueId, environmentId);

                // 1. Query provider registry (cached) — check if provider exists
                var providerOpt = providerCache.getProvider(
                        domainId, solutionId, functionalServiceName, zoneId, contextId);

                if (providerOpt.isEmpty()) {
                    // No ProviderRegistration found — fallback to PROVIDE ACL check
                    // Per SIF spec, DELAYED requests only require a PROVIDE ACL exists;
                    // the provider may not have registered yet or only receives via queue subscription.
                    log.info("No ProviderRegistration found for DELAYED request, checking PROVIDE ACL: service={}, zoneId={}, contextId={}",
                            functionalServiceName, zoneId, contextId);

                    UUID providerEnvironmentId = environmentServiceClient.getProvideAclEnvironmentId(
                            domainId, solutionId, functionalServiceName, zoneId, contextId);

                    if (providerEnvironmentId == null) {
                        // Neither ProviderRegistration nor PROVIDE ACL exists — reject
                        throw new NoProviderException(functionalServiceName, zoneId, contextId);
                    }

                    // PROVIDE ACL exists — proceed with queue-only routing (accept without provider endpoint)
                    log.info("PROVIDE ACL exists, accepting DELAYED request via queue-only routing: service={}, zoneId={}, contextId={}",
                            functionalServiceName, zoneId, contextId);

                    return delayedRequestHandler.handleQueueOnlyRouting(
                            httpMethod, functionalServiceName, environmentId,
                            domainId, solutionId, queueId, zoneId, contextId,
                            requestId, providerEnvironmentId, requestBody, passthroughHeaders);
                }

                ProviderRegistrationDTO provider = providerOpt.get();

                log.debug("Found provider for DELAYED request: endpoint={}, supportedRequestTypes={}",
                    ProtocolDTO.locationOf(provider.endPoint()), provider.supportedRequestTypes());

                // 2. Determine sub-pattern based on provider's supported request types
                boolean providerSupportsDelayed = provider.supportedRequestTypes().supportedRequestType().stream()
                        .anyMatch(srt -> srt.requestType() == RequestType.DELAYED);

                if (providerSupportsDelayed) {
                    // DELAYED_to_DELAYED: enqueue to provider's subscription queue
                    log.info("Provider supports DELAYED pattern, using DELAYED_to_DELAYED sub-pattern");
                    return delayedRequestHandler.handleDelayedToDelayed(
                            httpMethod, functionalServiceName, jobId, pathSuffix,
                            environmentId, environmentName, domainId, solutionId,
                            provider, queueId, zoneId, contextId,
                            requestId, requestBody, passthroughHeaders);
                } else {
                    // DELAYED_to_IMMEDIATE: store in Redis for background proxy
                    log.info("Provider supports only IMMEDIATE pattern, using DELAYED_to_IMMEDIATE sub-pattern");
                    return delayedRequestHandler.handleDelayedToImmediate(
                            httpMethod, functionalServiceName, jobId, pathSuffix,
                            environmentId, environmentName,
                            provider, queueId, zoneId, contextId,
                            requestId, requestBody, passthroughHeaders);
                }
            }
        );
    }

    /**
     * Builds the full provider URL by appending the functional service path segments
     * to the provider's registered endpoint.
     *
     * <p>URL construction rules:</p>
     * <ul>
     *   <li>{@code /services/{name}} → {@code {endpoint}/{name}}</li>
     *   <li>{@code /services/{name}/{jobId}} → {@code {endpoint}/{name}/{jobId}}</li>
     *   <li>{@code /services/{name}/{jobId}/{phase}} → {@code {endpoint}/{name}/{jobId}/{phase}}</li>
     *   <li>{@code /services/{name}/{jobId}/{phase}/states} → {@code {endpoint}/{name}/{jobId}/{phase}/states}</li>
     *   <li>{@code /services/{name}/{jobId}/{phase}/states/state} → {@code {endpoint}/{name}/{jobId}/{phase}/states/state}</li>
     * </ul>
     *
     * @param providerEndpoint     the provider's registered base endpoint URL
     * @param functionalServiceName the functional service name
     * @param jobId                the job ID (may be null)
     * @param pathSuffix           additional path after jobId (e.g., phaseName, phaseName/states/state; may be null)
     * @return the full provider URL
     */
    public static String buildProviderUrl(String providerEndpoint, String functionalServiceName,
                                   String jobId, String pathSuffix) {
        // Strip trailing slash from endpoint
        String base = providerEndpoint.endsWith("/")
                ? providerEndpoint.substring(0, providerEndpoint.length() - 1)
                : providerEndpoint;

        StringBuilder url = new StringBuilder(base);
        url.append('/').append(functionalServiceName);

        if (jobId != null && !jobId.isBlank()) {
            url.append('/').append(jobId);
        }

        if (pathSuffix != null && !pathSuffix.isBlank()) {
            // pathSuffix may already include phaseName, phaseName/states, phaseName/states/state
            if (!pathSuffix.startsWith("/")) {
                url.append('/');
            }
            url.append(pathSuffix);
        }

        return url.toString();
    }

}
