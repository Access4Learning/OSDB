// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.NoOpTracingUtils;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.util.NoOpTracingUtils;
import org.sifunity.responsesconnector.client.QueueServiceClient;
import org.sifunity.responsesconnector.dto.InternalErrorDetail;
import org.sifunity.responsesconnector.dto.InternalErrorRequest;
import org.sifunity.responsesconnector.dto.ResponseResultDTO;
import org.sifunity.responsesconnector.service.LegacyTagLookupService;
import org.sifunity.responsesconnector.service.ResponseDestinationValidator;
import org.sifunity.responsesconnector.service.ResponseRoutingService;
import org.sifunity.responsesconnector.util.MessageBuilder;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-Based Tests for Error Response Handling.
 * <p>
 * This test class validates the correctness properties for error response handling
 * as defined in the design document. It uses jqwik for property-based testing
 * to verify that the properties hold across a wide range of randomized inputs.
 * </p>
 * 
 * <p><b>Properties Tested:</b></p>
 * <ul>
 *   <li>Property 10: Error response acceptance</li>
 *   <li>Property 11: Error response correlation</li>
 *   <li>Property 12: SIF error format compliance</li>
 * </ul>
 * 
 * <p><b>Test Configuration:</b></p>
 * <ul>
 *   <li>Each property runs 100+ iterations with randomized inputs</li>
 *   <li>Uses mocked QueueServiceClient to simulate Queue Service behavior</li>
 *   <li>Validates error response routing and SIF 3.7 format compliance</li>
 * </ul>
 * 
 * @see org.sifunity.responsesconnector.service.ResponseRoutingService
 * @see org.sifunity.responsesconnector.util.MessageBuilder
 */
class ErrorResponsePropertiesTest {
    
    /**
     * Property 10: Error response acceptance from internal source
     * <p>
     * For any error response from Requests Connector (internal), the Responses Connector
     * should accept and enqueue it to the consumer's queue with error indicator.
     * </p>
     * 
     * <p><b>Validates: Requirements 5.1, 5.3</b></p>
     */
    @Property(tries = 20)
    @Label("Property 10: Error response acceptance from internal source")
    void shouldAcceptAndEnqueueInternalErrorResponse(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validErrorMessage") String errorMessage) {
        
        // Arrange: Create mocks
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        LegacyTagLookupService legacyTagLookupService = mock(LegacyTagLookupService.class);
        MessageBuilder messageBuilder = new MessageBuilder();
        when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class))).thenReturn(UUID.randomUUID());
        when(legacyTagLookupService.isLegacyEnvironment(anyString(), anyString(), any(UUID.class))).thenReturn(false);
        ResponseRoutingService routingService = new ResponseRoutingService(queueClient, messageBuilder, new NoOpTracingUtils(), legacyTagLookupService, mock(ResponseDestinationValidator.class));
        
        UUID messageId = UUID.randomUUID();
        when(queueClient.enqueueMessage(anyString(), anyString(), any(UUID.class), any(SifMessageEnvelope.class)))
                .thenReturn(messageId);
        
        // Act: Route the internal error response
        ResponseResultDTO result = routingService.routeInternalErrorResponse("test-domain", "test-solution", queueId, requestId, InternalErrorDetail.ofMessage(errorMessage), Collections.emptyMap());
        
        // Assert: Verify internal error response is accepted and enqueued
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo(messageId);
        assertThat(result.getStatus()).isEqualTo("ERROR_DELIVERED");
        assertThat(result.getTimestamp()).isNotNull();
        
        // Verify QueueServiceClient was called with correct queueId
        verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), any(SifMessageEnvelope.class));
        
        // Verify the envelope contains error details
        verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), argThat(envelope -> 
            envelope.getPayload() != null &&
            envelope.getPayload() instanceof SifErrorResponse
        ));
    }
    
    /**
     * Property 11: Error response correlation for internal errors
     * <p>
     * For any internal error response, the Responses Connector should include the requestId
     * for correlation with the original request.
     * </p>
     * 
     * <p><b>Validates: Requirements 5.5</b></p>
     */
    @Property(tries = 20)
    @Label("Property 11: Error response correlation for internal errors")
    void shouldPreserveRequestIdInInternalErrorResponse(
            @ForAll("validUUID") UUID queueId,
            @ForAll("validUUIDString") String requestId,
            @ForAll("validErrorMessage") String errorMessage) {
        
        // Arrange: Create mocks
        QueueServiceClient queueClient = mock(QueueServiceClient.class);
        LegacyTagLookupService legacyTagLookupService = mock(LegacyTagLookupService.class);
        MessageBuilder messageBuilder = new MessageBuilder();
        when(queueClient.getQueueOwnerEnvironmentId(anyString(), anyString(), any(UUID.class))).thenReturn(UUID.randomUUID());
        when(legacyTagLookupService.isLegacyEnvironment(anyString(), anyString(), any(UUID.class))).thenReturn(false);
        ResponseRoutingService routingService = new ResponseRoutingService(queueClient, messageBuilder, new NoOpTracingUtils(), legacyTagLookupService, mock(ResponseDestinationValidator.class));
        
        UUID messageId = UUID.randomUUID();
        when(queueClient.enqueueMessage(anyString(), anyString(), any(UUID.class), any(SifMessageEnvelope.class)))
                .thenReturn(messageId);
        
        // Act: Route the internal error response
        routingService.routeInternalErrorResponse("test-domain", "test-solution", queueId, requestId, InternalErrorDetail.ofMessage(errorMessage), Collections.emptyMap());
        
        // Assert: Verify requestId is preserved in the envelope
        verify(queueClient).enqueueMessage(anyString(), anyString(), eq(queueId), argThat(envelope -> 
            envelope.getRequestId() != null &&
            envelope.getRequestId().equals(requestId)
        ));
    }
    
    /**
     * Property 12: SIF error format compliance for internal errors
     * <p>
     * For any internal error response, the Responses Connector should format it according to
     * SIF 3.7 error format with code, message, and scope.
     * </p>
     * 
     * <p><b>Validates: Requirements 5.4, 5.6</b></p>
     */
    @Property(tries = 20)
    @Label("Property 12: SIF error format compliance for internal errors")
    void shouldFormatInternalErrorResponseInSifFormat(
            @ForAll("validUUIDString") String requestId,
            @ForAll("validErrorMessage") String errorMessage) {
        
        // Arrange: Create MessageBuilder
        MessageBuilder messageBuilder = new MessageBuilder();
        
        // Act: Build internal error envelope
        SifMessageEnvelope<SifErrorResponse> envelope = messageBuilder.buildInternalErrorEnvelope(
                requestId, InternalErrorDetail.ofMessage(errorMessage));
        
        // Assert: Verify SIF error format compliance
        assertThat(envelope).isNotNull();
        assertThat(envelope.getPayload()).isNotNull();
        assertThat(envelope.getPayload()).isInstanceOf(SifErrorResponse.class);
        
        SifErrorResponse error = envelope.getPayload();
        
        // Verify error code is present
        assertThat(error.subCode()).isNotNull();
        assertThat(error.subCode()).isEqualTo("INTERNAL_ERROR");
        
        // Verify error message is preserved
        assertThat(error.message()).isNotNull();
        assertThat(error.message()).isEqualTo(errorMessage);
        
        // Verify error scope is BROKER for internal errors
        assertThat(error.scope()).isNotNull();
        assertThat(error.scope()).isEqualTo("BROKER");
    }
    
    // ========== Arbitrary Providers ==========
    
    /**
     * Provides valid UUID values for property tests.
     */
    @Provide
    Arbitrary<UUID> validUUID() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> validUUIDString() {
        return Arbitraries.randomValue(random -> UUID.randomUUID().toString());
    }
    
    /**
     * Provides valid environment names for property tests.
     */
    @Provide
    Arbitrary<String> validEnvironmentName() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_', ' ')
                .ofMinLength(3)
                .ofMaxLength(50);
    }
    
    /**
     * Provides valid error messages for property tests.
     */
    @Provide
    Arbitrary<String> validErrorMessage() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '.', ',', ':', '-', '!', '?')
                .ofMinLength(10)
                .ofMaxLength(200);
    }
    
}
