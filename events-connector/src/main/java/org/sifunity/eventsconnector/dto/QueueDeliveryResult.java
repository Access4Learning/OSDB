// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.dto;

/**
 * Result of attempting to deliver an event to a specific queue.
 * Contains the queue identifier, delivery status, and error message if failed.
 * 
 * @param queueId Identifier of the queue
 * @param status Delivery status (SUCCESS or FAILED)
 * @param error Error message if delivery failed, null if successful
 */
public record QueueDeliveryResult(
    String queueId,
    DeliveryResultStatus status,
    String error
) {
    /**
     * Creates a successful delivery result.
     * 
     * @param queueId Identifier of the queue
     * @return QueueDeliveryResult with SUCCESS status and no error
     */
    public static QueueDeliveryResult success(String queueId) {
        return new QueueDeliveryResult(queueId, DeliveryResultStatus.SUCCESS, null);
    }
    
    /**
     * Creates a failed delivery result.
     * 
     * @param queueId Identifier of the queue
     * @param error Error message describing the failure
     * @return QueueDeliveryResult with FAILED status and error message
     */
    public static QueueDeliveryResult failure(String queueId, String error) {
        return new QueueDeliveryResult(queueId, DeliveryResultStatus.FAILED, error);
    }
}
