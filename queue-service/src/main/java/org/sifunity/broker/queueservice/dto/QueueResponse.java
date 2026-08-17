// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

import org.sifunity.broker.common.dto.QueueDTO;

/**
 * Response wrapper for a single queue.
 *
 * <p>Wraps the queue DTO under a {@code queue} key per SIF 3.7 convention.</p>
 *
 * @param queue the queue data
 * @see QueueDTO
 */
public record QueueResponse(
    QueueDTO queue
) {}
