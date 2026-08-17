// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

import jakarta.validation.Valid;

/**
 * Request wrapper for queue creation.
 *
 * <p>Wraps the queue creation body under a {@code queue} key,
 * matching the SIF 3.7 request envelope pattern.</p>
 *
 * @param queue the queue creation body
 * @see CreateQueueRequest
 */
public record QueueCreateRequest(
    @Valid CreateQueueRequest queue
) {}
