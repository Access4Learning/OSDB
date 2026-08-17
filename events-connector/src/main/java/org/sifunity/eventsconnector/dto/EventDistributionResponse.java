// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for event distribution operations.
 * Contains the result of distributing an event to all matching subscriber queues.
 * 
 * @param messageId Unique identifier of the distributed event
 * @param timestamp Timestamp when the event was processed
 * @param subscriberCount Number of subscribers that matched the event
 * @param deliveryStatus Overall delivery status (SUCCESS, PARTIAL, FAILED, NO_SUBSCRIBERS)
 * @param deliveryResults List of individual queue delivery results (empty for NO_SUBSCRIBERS)
 */
public record EventDistributionResponse(
    String messageId,
    Instant timestamp,
    int subscriberCount,
    DeliveryStatus deliveryStatus,
    List<QueueDeliveryResult> deliveryResults
) {
    /**
     * Canonical constructor with validation.
     * Ensures deliveryResults is never null.
     */
    public EventDistributionResponse {
        if (deliveryResults == null) {
            deliveryResults = List.of();
        }
    }
    
    /**
     * Creates a response for when no subscribers are found.
     * 
     * @param messageId Event message identifier
     * @param timestamp Event timestamp
     * @return EventDistributionResponse with NO_SUBSCRIBERS status
     */
    public static EventDistributionResponse noSubscribers(String messageId, Instant timestamp) {
        return new EventDistributionResponse(
            messageId,
            timestamp,
            0,
            DeliveryStatus.NO_SUBSCRIBERS,
            List.of()
        );
    }
    
    /**
     * Creates a response from delivery results.
     * Automatically determines the delivery status based on success/failure counts.
     * 
     * @param messageId Event message identifier
     * @param timestamp Event timestamp
     * @param subscriberCount Total number of subscribers
     * @param deliveryResults Individual queue delivery results
     * @return EventDistributionResponse with appropriate status
     */
    public static EventDistributionResponse fromResults(
        String messageId,
        Instant timestamp,
        int subscriberCount,
        List<QueueDeliveryResult> deliveryResults
    ) {
        if (deliveryResults.isEmpty()) {
            return noSubscribers(messageId, timestamp);
        }
        
        long successCount = deliveryResults.stream()
            .filter(r -> r.status() == DeliveryResultStatus.SUCCESS)
            .count();
        
        DeliveryStatus status;
        if (successCount == deliveryResults.size()) {
            status = DeliveryStatus.SUCCESS;
        } else if (successCount > 0) {
            status = DeliveryStatus.PARTIAL;
        } else {
            status = DeliveryStatus.FAILED;
        }
        
        return new EventDistributionResponse(
            messageId,
            timestamp,
            subscriberCount,
            status,
            deliveryResults
        );
    }
}
