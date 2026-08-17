// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.eventsconnector.client.SubscriptionServiceClient;
import org.sifunity.eventsconnector.exception.SubscriptionServiceException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for querying subscriptions from the Subscription Service.
 * 
 * <p>This service acts as a facade over the SubscriptionServiceClient, providing
 * a clean interface for querying subscriptions matching event criteria. It handles
 * errors from the Subscription Service and wraps them in appropriate exceptions.</p>
 * 
 * <p>The service queries subscriptions based on:</p>
 * <ul>
 *   <li>serviceName: The name of the service/object type (e.g., StudentPersonal)</li>
 *   <li>contextUuid: The resolved context UUID for routing (null for wildcard)</li>
 * </ul>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>2: Subscription Querying - Query Subscription Service for matching subscriptions</li>
 *   <li>8: Subscription Service Integration - Call Subscription Service API with parameters</li>
 *   <li>9.3: Subscription query accepts contextUuid parameter</li>
 * </ul>
 * 
 * @see SubscriptionServiceClient
 * @see SubscriptionServiceException
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionQueryService {
    
    private final SubscriptionServiceClient subscriptionServiceClient;
    
    /**
     * Queries subscriptions matching the specified service name and context UUID.
     * 
     * <p>This method calls the Subscription Service to find all subscriptions that match
     * the event criteria. The returned subscriptions contain queue IDs that indicate where
     * the event should be delivered.</p>
     * 
     * <p>Behavior:</p>
     * <ul>
     *   <li>Returns empty list if no subscriptions match (not an error)</li>
     *   <li>Throws SubscriptionServiceException if the Subscription Service is unavailable</li>
     *   <li>Logs query parameters and result count</li>
     * </ul>
     * 
     * @param domainId Domain identifier - must not be null or blank
     * @param solutionId Solution identifier - must not be null or blank
     * @param serviceName Name of the service/object type (e.g., StudentPersonal) - must not be null or blank
     * @param contextUuid Resolved context UUID (null for wildcard/DEFAULT context)
     * @return List of subscriptions matching the criteria - empty list if no matches, never null
     * @throws SubscriptionServiceException if the Subscription Service is unavailable or returns an error
     * @throws IllegalArgumentException if serviceName is null or blank
     */
    public List<SubscriptionDTO> querySubscriptions(
        String domainId,
        String solutionId,
        String serviceName,
        UUID contextUuid
    ) {
        validateParameters(serviceName);
        
        log.info("Querying subscriptions: domainId={}, solutionId={}, service={}, contextUuid={}", 
            domainId, solutionId, serviceName, contextUuid);
        
        try {
            List<SubscriptionDTO> subscriptions = subscriptionServiceClient.querySubscriptions(
                domainId, solutionId, serviceName, contextUuid
            );
            
            if (subscriptions.isEmpty()) {
                log.warn("No subscriptions found for service={}, contextUuid={}", 
                    serviceName, contextUuid);
            } else {
                log.info("Found {} subscription(s) for service={}, contextUuid={}", 
                    subscriptions.size(), serviceName, contextUuid);
            }
            
            return subscriptions;
            
        } catch (SubscriptionServiceException e) {
            log.error("Failed to query subscriptions: service={}, contextUuid={}", 
                serviceName, contextUuid, e);
            throw e;
            
        } catch (Exception e) {
            log.error("Unexpected error querying subscriptions: service={}, contextUuid={}", 
                serviceName, contextUuid, e);
            throw new SubscriptionServiceException(
                "Failed to query subscriptions from Subscription Service", e
            );
        }
    }
    
    /**
     * Validates that the serviceName parameter is provided and not blank.
     * 
     * @param serviceName Service name to validate
     * @throws IllegalArgumentException if serviceName is null or blank
     */
    private void validateParameters(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be null or blank");
        }
    }
}
