// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.dto;

import org.sifunity.broker.common.dto.QueueDTO;

/**
 * Response wrapper for a single queue from the Queue Service.
 *
 * <p>The Queue Service wraps single-resource GET responses under a {@code "queue"} key
 * per SIF 3.7 convention. This record maps that structure for deserialization.</p>
 *
 * @param queue the queue data
 */
public record QueueResponse(
    QueueDTO queue
) {}
