// SPDX-License-Identifier: MIT
package org.sifunity.integration.generator;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Generator for creating test subscriptions during integration tests.
 * 
 * <p>This class provides utilities for:
 * <ul>
 *   <li>Creating subscriptions linking consumers to event streams</li>
 *   <li>Supporting EVENT and DELAYED_REQUEST subscription types</li>
 *   <li>Querying subscriptions by service/zone/context</li>
 *   <li>Managing subscription lifecycle</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * SubscriptionGenerator generator = new SubscriptionGenerator(subscriptionServiceUrl);
 * 
 * // Create an EVENT subscription
 * SubscriptionDTO eventSub = generator.createEventSubscription(
 *     queueId,
 *     "StudentPersonals",
 *     contextUuid
 * );
 * 
 * // Create a DELAYED_REQUEST subscription for a provider
 * SubscriptionDTO delayedSub = generator.createDelayedRequestSubscription(
 *     providerQueueId,
 *     "StudentPersonals",
 *     contextUuid
 * );
 * 
 * // Query EVENT subscriptions
 * List&lt;SubscriptionDTO&gt; subs = generator.queryEventSubscriptions(
 *     "StudentPersonals",
 *     contextUuid
 * );
 * 
 * // Delete subscription after test
 * generator.deleteSubscription(eventSub.id());
 * </pre>
 * 
 * @since 1.0.0
 */
@Slf4j
public class SubscriptionGenerator {
    
    private final String subscriptionServiceUrl;
    private final RestTemplate restTemplate;
    
    /**
     * Creates a new subscription generator.
     * 
     * @param subscriptionServiceUrl the base URL of the Subscription Service
     */
    public SubscriptionGenerator(String subscriptionServiceUrl) {
        this.subscriptionServiceUrl = subscriptionServiceUrl;
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * Creates a new subscription with the specified parameters.
     * 
     * @param queueId the queue ID
     * @param serviceName the service name
     * @param contextUuid the context UUID (can be null for wildcard)
     * @param type the subscription type (EVENT or DELAYED_REQUEST)
     * @return the created subscription
     */
    public SubscriptionDTO createSubscription(
        UUID queueId,
        String serviceName,
        UUID contextUuid,
        SubscriptionType type
    ) {
        log.info("Creating subscription: queueId={}, serviceName={}, contextUuid={}, type={}",
            queueId, serviceName, contextUuid, type);
        
        // Create request DTO (using SubscriptionDTO as request since controller accepts it)
        SubscriptionDTO request = new SubscriptionDTO(
            null, // id will be generated
            queueId,
            null, // aclEntryId
            serviceName,
            "OBJECT", // serviceType
            null, // zoneId
            contextUuid != null ? contextUuid.toString() : null, // contextId
            type,
            Instant.now() // will be overridden by server
        );
        
        String url = subscriptionServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN 
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/subscriptions";
        
        SubscriptionDTO response = restTemplate.postForObject(
            url,
            request,
            SubscriptionDTO.class
        );
        
        log.info("Subscription created: id={}, queueId={}, serviceName={}, type={}",
            response.id(), response.queueId(), response.serviceName(), response.messageType());
        
        return response;
    }
    
    /**
     * Creates an EVENT subscription.
     * 
     * @param queueId the queue ID
     * @param serviceName the service name
     * @param contextUuid the context UUID (can be null for wildcard)
     * @return the created subscription
     */
    public SubscriptionDTO createEventSubscription(
        UUID queueId,
        String serviceName,
        UUID contextUuid
    ) {
        return createSubscription(
            queueId,
            serviceName,
            contextUuid,
            SubscriptionType.EVENT
        );
    }
    
    /**
     * Creates an EVENT subscription with wildcard context (null contextUuid).
     * 
     * @param queueId the queue ID
     * @param serviceName the service name
     * @return the created subscription
     */
    public SubscriptionDTO createEventSubscriptionWildcard(
        UUID queueId,
        String serviceName
    ) {
        return createEventSubscription(queueId, serviceName, null);
    }
    
    /**
     * Creates a DELAYED_REQUEST subscription for a provider.
     * 
     * @param queueId the provider's queue ID
     * @param serviceName the service name
     * @param contextUuid the context UUID (can be null)
     * @return the created subscription
     */
    public SubscriptionDTO createDelayedRequestSubscription(
        UUID queueId,
        String serviceName,
        UUID contextUuid
    ) {
        return createSubscription(
            queueId,
            serviceName,
            contextUuid,
            SubscriptionType.DELAYED_REQUEST
        );
    }
    
    /**
     * Queries EVENT subscriptions matching the given service and context.
     * 
     * @param serviceName the service name
     * @param contextUuid the context UUID (can be null)
     * @return list of matching EVENT subscriptions
     */
    public List<SubscriptionDTO> queryEventSubscriptions(
        String serviceName,
        UUID contextUuid
    ) {
        log.debug("Querying EVENT subscriptions: serviceName={}, contextUuid={}",
            serviceName, contextUuid);
        
        String url = subscriptionServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN 
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/subscriptions/events" +
            "?serviceName=" + serviceName;
        
        if (contextUuid != null) {
            url += "&contextUuid=" + contextUuid;
        }
        
        SubscriptionDTO[] response = restTemplate.getForObject(url, SubscriptionDTO[].class);
        
        List<SubscriptionDTO> subscriptions = response != null ? List.of(response) : List.of();
        
        log.debug("EVENT subscriptions found: count={}", subscriptions.size());
        
        return subscriptions;
    }
    
    /**
     * Queries the DELAYED_REQUEST subscription matching the given service and context.
     * 
     * @param serviceName the service name
     * @param contextUuid the context UUID (can be null)
     * @return the matching DELAYED_REQUEST subscription, or null if not found
     */
    public SubscriptionDTO queryDelayedRequestSubscription(
        String serviceName,
        UUID contextUuid
    ) {
        log.debug("Querying DELAYED_REQUEST subscription: serviceName={}, contextUuid={}",
            serviceName, contextUuid);
        
        String url = subscriptionServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN 
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/subscriptions/delayed-requests" +
            "?serviceName=" + serviceName;
        
        if (contextUuid != null) {
            url += "&contextUuid=" + contextUuid;
        }
        
        try {
            SubscriptionDTO response = restTemplate.getForObject(url, SubscriptionDTO.class);
            
            log.debug("DELAYED_REQUEST subscription found: id={}", response.id());
            
            return response;
            
        } catch (Exception e) {
            log.debug("DELAYED_REQUEST subscription not found");
            return null;
        }
    }
    
    /**
     * Retrieves a subscription by ID.
     * 
     * @param subscriptionId the subscription ID
     * @return the subscription
     */
    public SubscriptionDTO getSubscription(UUID subscriptionId) {
        log.debug("Getting subscription: id={}", subscriptionId);
        
        String url = subscriptionServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN 
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/subscriptions/" + subscriptionId;
        
        SubscriptionDTO response = restTemplate.getForObject(url, SubscriptionDTO.class);
        
        log.debug("Subscription retrieved: id={}, serviceName={}", response.id(), response.serviceName());
        
        return response;
    }
    
    /**
     * Lists all subscriptions for the specified queue.
     * 
     * @param queueId the queue ID
     * @return list of subscriptions
     */
    public List<SubscriptionDTO> listSubscriptionsByQueue(UUID queueId) {
        log.debug("Listing subscriptions for queue: {}", queueId);
        
        String url = subscriptionServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN 
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/subscriptions?queueId=" + queueId;
        
        SubscriptionDTO[] response = restTemplate.getForObject(url, SubscriptionDTO[].class);
        
        List<SubscriptionDTO> subscriptions = response != null ? List.of(response) : List.of();
        
        log.debug("Subscriptions retrieved: queueId={}, count={}", queueId, subscriptions.size());
        
        return subscriptions;
    }
    
    /**
     * Deletes a subscription by ID.
     * 
     * @param subscriptionId the subscription ID
     */
    public void deleteSubscription(UUID subscriptionId) {
        log.info("Deleting subscription: {}", subscriptionId);
        
        String url = subscriptionServiceUrl + "/" + GlobalAdminConstants.GLOBAL_DOMAIN 
            + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/subscriptions/" + subscriptionId;
        
        restTemplate.delete(url);
        
        log.info("Subscription deleted: {}", subscriptionId);
    }
    
    /**
     * Deletes all subscriptions for the specified queue.
     * 
     * @param queueId the queue ID
     */
    public void deleteAllForQueue(UUID queueId) {
        log.info("Deleting all subscriptions for queue: {}", queueId);
        
        List<SubscriptionDTO> subscriptions = listSubscriptionsByQueue(queueId);
        
        for (SubscriptionDTO subscription : subscriptions) {
            deleteSubscription(subscription.id());
        }
        
        log.info("All subscriptions deleted for queue: {}", queueId);
    }
}
