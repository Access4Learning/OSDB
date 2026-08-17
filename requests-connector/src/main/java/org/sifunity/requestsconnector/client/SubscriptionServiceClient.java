// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.client;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.sifunity.requestsconnector.exception.ServiceUnavailableException;

import java.util.UUID;

/**
 * HTTP client for communicating with the Subscription Service.
 * Provides methods for querying provider request subscriptions.
 * 
 * <p>This client is responsible for querying the Subscription Service to find a provider's
 * DELAYED_REQUEST subscription. This subscription contains the queueId where the provider
 * expects to receive request messages in the DELAYED_to_DELAYED routing pattern.</p>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>4.1: Query Subscription Service for provider's request subscription</li>
 *   <li>4.2: Extract provider's queueId from subscription</li>
 *   <li>4.3: Return HTTP 404 if provider has no request subscription</li>
 * </ul>
 */
@Component
@Slf4j
public class SubscriptionServiceClient {
    
    private final WebClient webClient;
    private final String baseUrl;
    
    /**
     * Constructs a SubscriptionServiceClient with the configured WebClient and base URL.
     * 
     * @param webClientBuilder WebClient builder configured with timeouts
     * @param baseUrl Subscription Service base URL from configuration
     */
    public SubscriptionServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${subscription.service.url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
    }
    
    /**
     * Gets the DELAYED_REQUEST subscription for a provider.
     * 
     * <p>This method queries the Subscription Service to find the provider's request
     * subscription, which contains the queueId where the provider expects to receive
     * request messages. The subscription is identified by service name and context UUID.</p>
     * 
     * <p>If no subscription is found (HTTP 404), this method returns null. The caller
     * is responsible for handling this case (typically by throwing NoSubscriptionException
     * which results in HTTP 404 Not Found to the consumer).</p>
     * 
     * <p>If the Subscription Service is unavailable (HTTP 503), this method will throw
     * a WebClientResponseException which should be handled by the caller.</p>
     * 
     * @param serviceName the name of the service (e.g., "StudentPersonal")
     * @param contextUuid the context UUID for the subscription (nullable for wildcard)
     * @return the subscription DTO containing the queueId, or null if not found
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException 
     *         if Subscription Service returns an error other than 404
     */
    public SubscriptionDTO getDelayedRequestSubscription(
            String serviceName, UUID contextUuid) {
        
        log.debug("Querying DELAYED_REQUEST subscription: serviceName={}, contextUuid={}", 
            serviceName, contextUuid);
        
        try {
            SubscriptionDTO subscription = webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .path("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/subscriptions/delayed-requests")
                        .queryParam("serviceName", serviceName);
                    
                    if (contextUuid != null) {
                        builder.queryParam("contextUuid", contextUuid);
                    }
                    
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(SubscriptionDTO.class)
                .block();
            
            if (subscription != null) {
                log.info("Found DELAYED_REQUEST subscription: serviceName={}, contextUuid={}, queueId={}", 
                    serviceName, contextUuid, subscription.queueId());
            }
            
            return subscription;
            
        } catch (WebClientResponseException.NotFound e) {
            // Provider has no request subscription - return null
            log.info("No DELAYED_REQUEST subscription found: serviceName={}, contextUuid={}", 
                serviceName, contextUuid);
            return null;

        } catch (WebClientRequestException e) {
            // Transport-level failure (connection refused, DNS, reset): the subscription-service
            // could not be reached at all. Surface as a clear 503 rather than a generic 500.
            log.error("subscription-service unreachable while querying DELAYED_REQUEST subscription: "
                + "serviceName={}, contextUuid={}", serviceName, contextUuid, e);
            throw new ServiceUnavailableException("subscription-service", e);

        } catch (Exception e) {
            log.error("Failed to query DELAYED_REQUEST subscription: serviceName={}, contextUuid={}", 
                serviceName, contextUuid, e);
            throw e;
        }
    }
}
