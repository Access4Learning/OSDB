// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.util;

import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for building SIF message envelopes for responses.
 * 
 * <p>This class constructs properly formatted SIF message envelopes for both
 * success and error responses from providers and the Requests Connector. It ensures
 * all required headers are included and properly formatted according to SIF 3.7
 * specification.</p>
 * 
 * <p>The MessageBuilder generates unique messageIds and timestamps for each message,
 * and includes correlation metadata (requestId) to link responses with their
 * originating requests.</p>
 * 
 * <p>Requirements: 4.1, 4.2, 4.3, 4.4, 6.1, 6.2, 6.3, 6.4, 6.5</p>
 */
@Component
public class MessageBuilder {
    
    /**
     * Builds a SIF message envelope for a provider response.
     * 
     * <p>Creates an envelope with all required SIF headers including messageId,
     * timestamp, messageType, serviceName, and serviceType. The messageId and
     * timestamp are automatically generated. The requestId is included for
     * correlation with the original request.</p>
     * 
     * <p>This method is used for responses from providers that come through the
     * Gateway with full environment context.</p>
     * 
     * @param environmentId the environment ID from the request context
     * @param environmentName the environment name from the request context (not used in envelope)
     * @param requestId the original request ID for correlation
     * @param response the response DTO containing service details and payload
     * @return a SIF message envelope ready to be enqueued to the consumer's queue
     */
    public SifMessageEnvelope<Object> buildResponseEnvelope(
            UUID environmentId, 
            String environmentName,
            String requestId, 
            ResponseDTO response,
            Map<String, String> passthroughHeaders) {
        
        return SifMessageEnvelope.builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .messageType(MessageType.RESPONSE)
            .serviceName(response.getServiceName())
            .serviceType(ServiceType.valueOf(response.getServiceType()))
            .requestId(requestId)
            .sourceId(environmentId)
            .headers(passthroughHeaders)
            .payload(response.getPayload())
            .build();
    }
    
    /**
     * Builds a SIF message envelope for an internal response from Requests Connector.
     * 
     * <p>Creates an envelope with all required SIF headers. This method is used for
     * responses from the Requests Connector (DELAYED_to_IMMEDIATE pattern) which
     * don't have environment context headers. The messageId and timestamp are
     * automatically generated.</p>
     * 
     * @param requestId the original request ID for correlation
     * @param response the response DTO containing service details and payload
     * @return a SIF message envelope ready to be enqueued to the consumer's queue
     */
    public SifMessageEnvelope<Object> buildInternalResponseEnvelope(
            String requestId, 
            ResponseDTO response) {
        
        return SifMessageEnvelope.builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .messageType(MessageType.RESPONSE)
            .serviceName(response.getServiceName())
            .serviceType(ServiceType.valueOf(response.getServiceType()))
            .requestId(requestId)
            .headers(response.getHeaders() != null ? response.getHeaders() : java.util.Collections.emptyMap())
            .payload(response.getPayload())
            .build();
    }
    
    /**
     * Builds a SIF message envelope for an internal error from Requests Connector.
     *
     * <p>Creates an envelope with error details for internal errors (e.g. provider timeout,
     * provider unavailable, paging failure), so the consumer learns that its DELAYED request will
     * not be answered.</p>
     *
     * <p>The sender's classification is <b>preserved</b>: {@code code}, {@code subCode},
     * {@code description} and {@code scope} pass through as supplied, each falling back to
     * 500 / {@code INTERNAL_ERROR} / {@code BROKER} only when absent. Previously this method
     * hardcoded those three values, so a provider-scoped failure reached the consumer labelled as a
     * broker-internal 500 and the real cause was recoverable only by reading the message text.</p>
     *
     * @param requestId the original request ID for correlation
     * @param error the error detail describing what went wrong
     * @return a SIF message envelope with error details ready to be enqueued
     */
    public SifMessageEnvelope<SifErrorResponse> buildInternalErrorEnvelope(
            String requestId,
            org.sifunity.responsesconnector.dto.InternalErrorDetail error) {

        SifErrorResponse sifError = new SifErrorResponse(
            UUID.randomUUID().toString(),
            error.codeOrDefault(),
            error.subCodeOrDefault(),
            error.message(),
            error.description(),
            error.scopeOrDefault(),
            Instant.now(),
            null,  // field
            null   // stackTrace
        );

        return SifMessageEnvelope.<SifErrorResponse>builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .messageType(MessageType.RESPONSE)
            .serviceName("InternalError")
            .serviceType(ServiceType.UTILITY)
            .requestId(requestId)
            .payload(sifError)
            .build();
    }
}
