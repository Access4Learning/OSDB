// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

/**
 * Administrative view of a message currently reserved in a Redis consumer-group PEL.
 *
 * @param messageId SIF message identifier
 * @param streamId Redis stream entry identifier
 * @param consumerName consumer connection currently holding the message
 * @param idleTimeMs milliseconds since the message was last delivered
 * @param deliveryCount number of deliveries recorded by Redis
 */
public record PendingQueueMessageResponse(
        String messageId,
        String streamId,
        String consumerName,
        long idleTimeMs,
        long deliveryCount) {
}
