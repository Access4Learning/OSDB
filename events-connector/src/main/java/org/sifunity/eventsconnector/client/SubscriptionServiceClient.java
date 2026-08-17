// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.eventsconnector.dto.SubscriptionQueryResponse;
import org.sifunity.eventsconnector.exception.SubscriptionServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * HTTP client for Subscription Service communication.
 * Implements retry logic with exponential backoff, circuit breaker pattern, and timeout handling.
 * 
 * <p>This client is responsible for querying the Subscription Service to find all subscriptions
 * matching an event's service name and context UUID. It includes resilience patterns to handle
 * transient failures and prevent cascading failures.</p>
 * 
 * <p>Uses Spring's synchronous {@link RestClient} with JDK HttpClient transport — no Reactor
 * or Netty dependencies. The RestClient throws {@code HttpClientErrorException} (4xx) and
 * {@code HttpServerErrorException} (5xx) by default, so no custom error mapping is needed.</p>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>2: Subscription Querying - Query subscriptions matching event criteria</li>
 *   <li>8: Subscription Service Integration - RestClient for synchronous HTTP calls</li>
 *   <li>12: Service Call Timeouts - Configurable connection and read timeouts</li>
 *   <li>13: Retry Strategy - Exponential backoff with 3 attempts for 5xx errors</li>
 * </ul>
 */
@Component
@Slf4j
public class SubscriptionServiceClient {
    
    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final String subscriptionServiceUrl;
    
    /**
     * Constructs a SubscriptionServiceClient with RestClient, retry, and circuit breaker.
     * 
     * @param restClient RestClient configured for Subscription Service
     * @param retry Retry instance for handling transient failures
     * @param circuitBreaker Circuit breaker instance for preventing cascading failures
     * @param subscriptionServiceUrl Base URL of the Subscription Service (used in logging)
     */
    public SubscriptionServiceClient(
        @Qualifier("subscriptionServiceRestClient") RestClient restClient,
        @Qualifier("subscriptionServiceRetry") Retry retry,
        @Qualifier("subscriptionServiceCircuitBreaker") CircuitBreaker circuitBreaker,
        @Value("${subscription-service.url}") String subscriptionServiceUrl
    ) {
        this.restClient = restClient;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        this.subscriptionServiceUrl = subscriptionServiceUrl;
    }
    
    /**
     * Queries subscriptions matching the specified service name and context UUID.
     * 
     * <p>This method calls the Subscription Service query API with the event criteria
     * and returns all matching subscriptions. It includes:</p>
     * <ul>
     *   <li>Retry logic: 3 attempts with exponential backoff for 5xx errors</li>
     *   <li>Timeout: Configured in RestClient via JdkClientHttpRequestFactory</li>
     *   <li>Circuit breaker: Prevents cascading failures</li>
     * </ul>
     * 
     * <p>The method does NOT retry on 4xx client errors as these indicate invalid
     * requests that won't succeed on retry.</p>
     * 
     * @param domainId Domain identifier
     * @param solutionId Solution identifier
     * @param serviceName Name of the service/object type (e.g., StudentPersonal)
     * @param contextUuid Context UUID (nullable for wildcard)
     * @return List of subscriptions matching the criteria (empty list if no matches)
     * @throws SubscriptionServiceException if the Subscription Service is unavailable or returns an error
     */
    @Cacheable(value = "subscription-query",
               key = "#domainId + ':' + #solutionId + ':' + #serviceName + ':' + #contextUuid")
    public List<SubscriptionDTO> querySubscriptions(
        String domainId,
        String solutionId,
        String serviceName,
        UUID contextUuid
    ) {
        log.debug("Querying subscriptions: domainId={}, solutionId={}, service={}, contextUuid={}", 
            domainId, solutionId, serviceName, contextUuid);
        
        try {
            // Execute query with circuit breaker and retry protection
            SubscriptionQueryResponse response = circuitBreaker.executeSupplier(() ->
                retry.executeSupplier(() ->
                    executeQuery(domainId, solutionId, serviceName, contextUuid)
                )
            );
            
            log.info("Found {} subscriptions for service={}, contextUuid={}", 
                response.size(), serviceName, contextUuid);
            
            return response.asList();
            
        } catch (Exception e) {
            log.error("Failed to query subscriptions: service={}, contextUuid={}", 
                serviceName, contextUuid, e);
            throw new SubscriptionServiceException(
                "Failed to query subscriptions from Subscription Service", e
            );
        }
    }
    
    /**
     * Executes the actual HTTP query to the Subscription Service.
     * This method is called by the retry and circuit breaker wrappers.
     * 
     * @param domainId Domain identifier
     * @param solutionId Solution identifier
     * @param serviceName Name of the service/object type
     * @param contextUuid Context UUID (nullable for wildcard)
     * @return SubscriptionQueryResponse containing matching subscriptions
     */
    private SubscriptionQueryResponse executeQuery(
        String domainId,
        String solutionId,
        String serviceName,
        UUID contextUuid
    ) {
        return restClient.get()
            .uri(uriBuilder -> {
                uriBuilder
                    .path("/{domainId}/{solutionId}/subscriptions/events")
                    .queryParam("serviceName", serviceName);
                if (contextUuid != null) {
                    uriBuilder.queryParam("contextUuid", contextUuid);
                }
                return uriBuilder.build(domainId, solutionId);
            })
            .retrieve()
            .body(SubscriptionQueryResponse.class);
    }
    
    /**
     * Pings the Subscription Service to check if it's available.
     * Used by health indicators to monitor service availability.
     */
    public void ping() {
        log.debug("Pinging Subscription Service at {}", subscriptionServiceUrl);
        
        restClient.get()
            .uri("/actuator/health")
            .retrieve()
            .toBodilessEntity();
        
        log.debug("Subscription Service is available");
    }
}
