// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.requestsconnector.dto.ImmediateRequestDTO;
import org.sifunity.requestsconnector.dto.DelayedRequestDTO;
import org.sifunity.requestsconnector.util.MessageBuilder;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for MessageBuilder.
 * 
 * <p>Tests the construction of SIF message envelopes for both IMMEDIATE
 * and DELAYED request patterns.</p>
 * 
 * <p>Requirements: 6.1, 6.2, 6.3, 6.4, 6.5</p>
 */
class MessageBuilderTest {
    
    private MessageBuilder messageBuilder;
    private UUID environmentId;
    private String environmentName;
    private UUID consumerQueueId;
    
    @BeforeEach
    void setUp() {
        messageBuilder = new MessageBuilder();
        environmentId = UUID.randomUUID();
        environmentName = "TestEnvironment";
        consumerQueueId = UUID.randomUUID();
    }
    
    @Test
    void buildRequestEnvelope_shouldGenerateMessageIdAndTimestamp() {
        // Given: An IMMEDIATE request
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(Map.of("key", "value"));
        
        // When: Building the envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
            environmentId, environmentName, request, Collections.emptyMap());
        
        // Then: MessageId and timestamp should be generated
        assertThat(envelope.getMessageId()).isNotNull();
        assertThat(envelope.getTimestamp()).isNotNull();
    }
    
    @Test
    void buildRequestEnvelope_shouldIncludeAllRequiredHeaders() {
        // Given: An IMMEDIATE request
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(Map.of("key", "value"));
        
        // When: Building the envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
            environmentId, environmentName, request, Collections.emptyMap());
        
        // Then: All required headers should be present
        assertThat(envelope.getMessageType()).isEqualTo(MessageType.REQUEST);
        assertThat(envelope.getServiceName()).isEqualTo("StudentPersonal");
        assertThat(envelope.getServiceType()).isEqualTo(ServiceType.OBJECT);
        assertThat(envelope.getSourceId()).isEqualTo(environmentId);
    }
    
    @Test
    void buildRequestEnvelope_shouldPreservePayload() {
        // Given: An IMMEDIATE request with a complex payload
        Map<String, Object> payload = Map.of(
            "studentId", "12345",
            "name", "John Doe",
            "grade", 10
        );
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(payload);
        
        // When: Building the envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
            environmentId, environmentName, request, Collections.emptyMap());
        
        // Then: Payload should be preserved unchanged
        assertThat(envelope.getPayload()).isEqualTo(payload);
    }
    
    @Test
    void buildRequestEnvelope_shouldHandleNullPayload() {
        // Given: An IMMEDIATE request with null payload
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(null);
        
        // When: Building the envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
            environmentId, environmentName, request, Collections.emptyMap());
        
        // Then: Payload should be null
        assertThat(envelope.getPayload()).isNull();
    }
    
    @Test
    void buildDelayedRequestEnvelope_shouldGenerateMessageIdAndTimestamp() {
        // Given: A DELAYED request
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(Map.of("key", "value"));
        
        // When: Building the envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
            environmentId, environmentName, consumerQueueId, request, Collections.emptyMap(), "default", "default", null);
        
        // Then: MessageId and timestamp should be generated
        assertThat(envelope.getMessageId()).isNotNull();
        assertThat(envelope.getTimestamp()).isNotNull();
    }
    
    @Test
    void buildDelayedRequestEnvelope_shouldIncludeAllRequiredHeaders() {
        // Given: A DELAYED request
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(Map.of("key", "value"));
        
        // When: Building the envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
            environmentId, environmentName, consumerQueueId, request, Collections.emptyMap(), "default", "default", null);
        
        // Then: All required headers should be present
        assertThat(envelope.getMessageType()).isEqualTo(MessageType.REQUEST);
        assertThat(envelope.getServiceName()).isEqualTo("StudentPersonal");
        assertThat(envelope.getServiceType()).isEqualTo(ServiceType.OBJECT);
        assertThat(envelope.getSourceId()).isEqualTo(environmentId);
    }
    
    @Test
    void buildDelayedRequestEnvelope_shouldIncludeConsumerQueueId() {
        // Given: A DELAYED request
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(Map.of("key", "value"));
        
        // When: Building the envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
            environmentId, environmentName, consumerQueueId, request, Collections.emptyMap(), "default", "default", null);
        
        // Then: Consumer's queueId should be included
        assertThat(envelope.getQueueId()).isEqualTo(consumerQueueId);
    }
    
    @Test
    void buildDelayedRequestEnvelope_shouldPreservePayload() {
        // Given: A DELAYED request with a complex payload
        Map<String, Object> payload = Map.of(
            "studentId", "12345",
            "name", "John Doe",
            "grade", 10
        );
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(payload);
        
        // When: Building the envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
            environmentId, environmentName, consumerQueueId, request, Collections.emptyMap(), "default", "default", null);
        
        // Then: Payload should be preserved unchanged
        assertThat(envelope.getPayload()).isEqualTo(payload);
    }
    
    @Test
    void buildDelayedRequestEnvelope_shouldHandleNullPayload() {
        // Given: A DELAYED request with null payload (e.g., DELAYED GET query)
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(null);
        
        // When: Building the envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
            environmentId, environmentName, consumerQueueId, request, Collections.emptyMap(), "default", "default", null);
        
        // Then: Payload should default to empty map (not null) to avoid NPE downstream
        assertThat(envelope.getPayload()).isEqualTo(Collections.emptyMap());
    }
    
    @Test
    void buildRequestEnvelope_shouldHandleDifferentServiceTypes() {
        // Given: Requests with different service types
        ImmediateRequestDTO objectRequest = new ImmediateRequestDTO();
        objectRequest.setServiceName("StudentPersonal");
        objectRequest.setServiceType("OBJECT");
        objectRequest.setMessageType("REQUEST");
        
        ImmediateRequestDTO functionalRequest = new ImmediateRequestDTO();
        functionalRequest.setServiceName("DataProcessing");
        functionalRequest.setServiceType("FUNCTIONAL");
        functionalRequest.setMessageType("REQUEST");
        
        ImmediateRequestDTO utilityRequest = new ImmediateRequestDTO();
        utilityRequest.setServiceName("QueueManagement");
        utilityRequest.setServiceType("UTILITY");
        utilityRequest.setMessageType("REQUEST");
        
        // When: Building envelopes
        SifMessageEnvelope<Object> objectEnvelope = messageBuilder.buildRequestEnvelope(
            environmentId, environmentName, objectRequest, Collections.emptyMap());
        SifMessageEnvelope<Object> functionalEnvelope = messageBuilder.buildRequestEnvelope(
            environmentId, environmentName, functionalRequest, Collections.emptyMap());
        SifMessageEnvelope<Object> utilityEnvelope = messageBuilder.buildRequestEnvelope(
            environmentId, environmentName, utilityRequest, Collections.emptyMap());
        
        // Then: Service types should be correctly set
        assertThat(objectEnvelope.getServiceType()).isEqualTo(ServiceType.OBJECT);
        assertThat(functionalEnvelope.getServiceType()).isEqualTo(ServiceType.FUNCTIONAL);
        assertThat(utilityEnvelope.getServiceType()).isEqualTo(ServiceType.UTILITY);
    }
    
    @Test
    void buildRequestEnvelope_shouldHandleDifferentMessageTypes() {
        // Given: Requests with different message types
        ImmediateRequestDTO requestMsg = new ImmediateRequestDTO();
        requestMsg.setServiceName("StudentPersonal");
        requestMsg.setServiceType("OBJECT");
        requestMsg.setMessageType("REQUEST");
        
        ImmediateRequestDTO responseMsg = new ImmediateRequestDTO();
        responseMsg.setServiceName("StudentPersonal");
        responseMsg.setServiceType("OBJECT");
        responseMsg.setMessageType("RESPONSE");
        
        ImmediateRequestDTO eventMsg = new ImmediateRequestDTO();
        eventMsg.setServiceName("StudentPersonal");
        eventMsg.setServiceType("OBJECT");
        eventMsg.setMessageType("EVENT");
        
        // When: Building envelopes
        SifMessageEnvelope<Object> requestEnvelope = messageBuilder.buildRequestEnvelope(
            environmentId, environmentName, requestMsg, Collections.emptyMap());
        SifMessageEnvelope<Object> responseEnvelope = messageBuilder.buildRequestEnvelope(
            environmentId, environmentName, responseMsg, Collections.emptyMap());
        SifMessageEnvelope<Object> eventEnvelope = messageBuilder.buildRequestEnvelope(
            environmentId, environmentName, eventMsg, Collections.emptyMap());
        
        // Then: Message types should be correctly set
        assertThat(requestEnvelope.getMessageType()).isEqualTo(MessageType.REQUEST);
        assertThat(responseEnvelope.getMessageType()).isEqualTo(MessageType.RESPONSE);
        assertThat(eventEnvelope.getMessageType()).isEqualTo(MessageType.EVENT);
    }
}
