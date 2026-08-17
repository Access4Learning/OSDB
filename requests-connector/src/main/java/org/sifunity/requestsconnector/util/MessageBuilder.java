// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.util;

import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.requestsconnector.dto.ImmediateRequestDTO;
import org.sifunity.requestsconnector.dto.DelayedRequestDTO;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for building SIF message envelopes for requests.
 * 
 * <p>This class constructs properly formatted SIF message envelopes for both
 * IMMEDIATE and DELAYED request patterns, ensuring all required headers are
 * included and properly formatted.</p>
 * 
 * <p>Requirements: 6.1, 6.2, 6.3, 6.4, 6.5</p>
 */
@Component
public class MessageBuilder {
    
    /**
     * Builds a SIF message envelope for an IMMEDIATE request.
     * 
     * <p>Creates an envelope with all required SIF headers including messageId,
     * timestamp, messageType, serviceName, and serviceType. The messageId and
     * timestamp are automatically generated.</p>
     * 
     * @param environmentId the environment ID from the request context
     * @param environmentName the environment name from the request context
     * @param request the IMMEDIATE request DTO containing service details and payload
     * @return a SIF message envelope ready to be sent to the provider
     */
    public SifMessageEnvelope<Object> buildRequestEnvelope(
            UUID environmentId, 
            String environmentName,
            ImmediateRequestDTO request,
            Map<String, String> passthroughHeaders) {
        
        return SifMessageEnvelope.builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .messageType(MessageType.valueOf(request.getMessageType()))
            .serviceName(request.getServiceName())
            .serviceType(ServiceType.valueOf(request.getServiceType()))
            .sourceId(environmentId)
            .headers(passthroughHeaders != null ? passthroughHeaders : Collections.emptyMap())
            .payload(request.getPayload())
            .build();
    }
    
    /**
     * Builds a SIF message envelope for a DELAYED request.
     * 
     * <p>Creates an envelope with all required SIF headers plus the consumer's
     * queueId for response routing. The messageId and timestamp are automatically
     * generated.</p>
     * 
     * @param environmentId the environment ID from the request context
     * @param environmentName the environment name from the request context
     * @param consumerQueueId the consumer's queue ID for receiving the response
     * @param request the DELAYED request DTO containing service details and payload
     * @param requestId the consumer-supplied requestId for correlation (may be null;
     *                  falls back to the generated messageId)
     * @return a SIF message envelope ready to be sent to the provider or queued
     */
    public SifMessageEnvelope<Object> buildDelayedRequestEnvelope(
            UUID environmentId, 
            String environmentName,
            UUID consumerQueueId, 
            DelayedRequestDTO request,
            Map<String, String> passthroughHeaders,
            String zoneId,
            String contextId,
            String requestId) {
        
        UUID messageId = UUID.randomUUID();
        // Resolve RID: honor consumer-supplied requestId, fall back to generated messageId
        String rid = (requestId != null && !requestId.isBlank())
                ? requestId
                : messageId.toString();

        // Ensure headers map is mutable and carries requestId for the provider-facing path
        Map<String, String> headers = passthroughHeaders != null
                ? new java.util.HashMap<>(passthroughHeaders)
                : new java.util.HashMap<>();
        headers.put("requestId", rid);

        return SifMessageEnvelope.builder()
            .messageId(messageId)
            .requestId(rid)
            .timestamp(Instant.now())
            .messageType(MessageType.valueOf(request.getMessageType()))
            .serviceName(request.getServiceName())
            .serviceType(ServiceType.valueOf(request.getServiceType()))
            .sourceId(environmentId)
            .queueId(consumerQueueId)
            .zoneId(zoneId)
            .contextId(contextId != null ? contextId : "DEFAULT")
            .headers(headers)
            .payload(request.getPayload() != null ? request.getPayload() : Collections.emptyMap())
            .build();
    }
}
