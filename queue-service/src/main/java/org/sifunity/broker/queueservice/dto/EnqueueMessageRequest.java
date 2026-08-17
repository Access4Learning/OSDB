// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for enqueuing a message to a queue.
 *
 * <p>Accepts both the legacy field names (environment, zone, context, service)
 * and the SifMessageEnvelope field names (serviceName, zoneId, contextId, sourceId)
 * via {@code @JsonAlias}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnqueueMessageRequest(
    UUID messageId,

    @JsonAlias("sourceId")
    String environment,

    @JsonAlias("zoneId")
    String zone,

    @JsonAlias("contextId")
    String context,

    @JsonAlias("serviceName")
    String service,

    String messageType,
    String serviceType,
    String requestId,
    String queueId,

    /** All non-blacklisted headers to pass through to message consumer. */
    Map<String, String> headers,

    Object payload
) {
}
