// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Module-local wrapper record for parsing the SIF 3.7 queue response envelope.
 *
 * <p>The queue-service returns {@code {"queue": {"id": ..., "ownerId": ..., ...}}}. This record
 * captures only the fields needed for the ownership lookup.</p>
 *
 * <p><strong>{@code ignoreUnknown = true} is load-bearing, not cosmetic.</strong> The real payload
 * carries the full SIF 3.7 queue object ({@code name}, {@code polling}, {@code queueUri},
 * {@code created}, {@code messageCount}, and more). Without this annotation Jackson's
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} rejects the body, the owner lookup returns empty, and — because
 * the ownership gate is deliberately fail-closed — <em>every DELAYED request is rejected with 404</em>.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueueResponse(QueueContent queue) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QueueContent(UUID id, UUID ownerId) {}
}
