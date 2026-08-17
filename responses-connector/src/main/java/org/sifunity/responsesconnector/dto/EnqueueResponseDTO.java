// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Data Transfer Object for Queue Service enqueue responses.
 * <p>
 * This DTO represents the response received from the Queue Service after successfully
 * enqueuing a message to a consumer queue. It is used internally by the QueueServiceClient
 * to parse the Queue Service API response.
 * </p>
 * <p>
 * The DTO contains the message ID assigned by the Queue Service and the status of the
 * enqueue operation.
 * </p>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Received from Queue Service API
 * EnqueueResponseDTO response = webClient.post()
 *     .uri("/queues/{queueId}/messages", queueId)
 *     .bodyValue(envelope)
 *     .retrieve()
 *     .bodyToMono(EnqueueResponseDTO.class)
 *     .block();
 * 
 * UUID messageId = response.getMessageId();
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnqueueResponseDTO {
    
    /**
     * The unique identifier for the enqueued message.
     * This is assigned by the Queue Service when the message is successfully enqueued.
     */
    private UUID messageId;
    
    /**
     * The status of the enqueue operation.
     * Typically "ENQUEUED" for successful operations.
     */
    private String status;
}
