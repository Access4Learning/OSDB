// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.client;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;

/**
 * HTTP client for communicating with the Subscription Service.
 *
 * <p>Queries the Subscription Service to find a provider's DELAYED_REQUEST
 * subscription. This subscription contains the queueId where the provider
 * expects to receive request messages in the DELAYED_to_DELAYED pattern.</p>
 *
 * <p>Mirrors the requests-connector's {@code SubscriptionServiceClient}.</p>
 *
 * <p><strong>Requirements:</strong> 5.2</p>
 */
@Component
@Slf4j
public class SubscriptionServiceClient {

    private final WebClient webClient;

    public SubscriptionServiceClient(
            @Qualifier("environmentServiceWebClientBuilder") WebClient.Builder webClientBuilder,
            @Value("${subscription.service.url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * Gets the DELAYED_REQUEST subscription for a provider.
     *
     * @param domainId    the domain ID for the request path
     * @param solutionId  the solution ID for the request path
     * @param serviceName the name of the service (e.g., "DataExport")
     * @param contextUuid the context UUID for the subscription (nullable for wildcard)
     * @return the subscription DTO containing the queueId, or null if not found
     */
    public SubscriptionDTO getDelayedRequestSubscription(
            String domainId, String solutionId,
            String serviceName, UUID contextUuid) {

        log.debug("Querying DELAYED_REQUEST subscription: domainId={}, solutionId={}, serviceName={}, contextUuid={}",
                domainId, solutionId, serviceName, contextUuid);

        try {
            SubscriptionDTO subscription = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/{domainId}/{solutionId}/subscriptions/delayed-requests")
                                .queryParam("serviceName", serviceName);
                        if (contextUuid != null) {
                            builder.queryParam("contextUuid", contextUuid);
                        }
                        return builder.build(domainId, solutionId);
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
            log.info("No DELAYED_REQUEST subscription found: serviceName={}, contextUuid={}",
                    serviceName, contextUuid);
            return null;

        } catch (Exception e) {
            log.error("Failed to query DELAYED_REQUEST subscription: serviceName={}, contextUuid={}",
                    serviceName, contextUuid, e);
            throw e;
        }
    }
}
