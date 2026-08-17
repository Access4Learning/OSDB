// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.MessageExchangePattern;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.requestsconnector.client.EnvironmentServiceClient;
import org.sifunity.requestsconnector.client.QueueServiceClient;
import org.sifunity.requestsconnector.client.SubscriptionServiceClient;
import org.sifunity.requestsconnector.dto.DelayedRequestDTO;
import org.sifunity.requestsconnector.dto.ProxyRequestMetadata;
import org.sifunity.requestsconnector.dto.RequestResponseDTO;
import org.sifunity.requestsconnector.exception.NoSubscriptionException;
import org.sifunity.requestsconnector.service.DelayedRequestHandler;
import org.sifunity.requestsconnector.service.LegacyTagLookupService;
import org.sifunity.requestsconnector.util.MessageBuilder;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Unit tests for DelayedRequestHandler.
 * Uses Mockito to mock dependencies.
 * 
 * Tests Requirements:
 * - 4.1: Query Subscription Service for provider's request subscription
 * - 4.2: Extract provider's queueId from subscription
 * - 4.3: Return HTTP 404 if provider has no request subscription
 * - 4.4: Enqueue message to provider's queue via Queue Service
 * - 4.5: Return HTTP 202 Accepted with messageId
 * - 5.1: Store request metadata for background processing
 * - 5.2: Include consumer's queueId, provider endpoint, and request payload
 * - 5.3: Return HTTP 202 Accepted with messageId
 */
@ExtendWith(MockitoExtension.class)
class DelayedRequestHandlerTest {
    
    @Mock
    private SubscriptionServiceClient subscriptionClient;
    
    @Mock
    private QueueServiceClient queueClient;
    
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    
    @Mock
    private StreamOperations<String, Object, Object> streamOperations;
    
    @Mock
    private EnvironmentServiceClient environmentServiceClient;
    
    @Mock
    private LegacyTagLookupService legacyTagLookupService;
    
    private MessageBuilder messageBuilder;
    private ObjectMapper objectMapper;
    private DelayedRequestHandler handler;
    
    @BeforeEach
    void setUp() {
        messageBuilder = new MessageBuilder();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        handler = new DelayedRequestHandler(
            subscriptionClient, queueClient, messageBuilder, stringRedisTemplate, objectMapper, environmentServiceClient, legacyTagLookupService);
    }
    
    // ========== DELAYED to DELAYED Flow Tests ==========
    
    @Test
    void handleDelayedToDelayed_withValidRequest_shouldEnqueueAndReturnAccepted() {
        // Given: A valid DELAYED request and provider with subscription
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        String domainId = "test-domain";
        String solutionId = "test-solution";
        UUID consumerQueueId = UUID.randomUUID();
        UUID providerQueueId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();
        
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType(ServiceType.OBJECT.name());
        request.setMessageType(MessageType.REQUEST.name());
        request.setPayload(Map.of("id", "123"));
        
        ProviderRegistrationDTO provider = createProvider(
            "StudentPersonal", "default", "default",
            Set.of(MessageExchangePattern.DELAYED));
        
        SubscriptionDTO subscription = new SubscriptionDTO(
            UUID.randomUUID(),
            providerQueueId,
            null,
            "StudentPersonal",
            "OBJECT",
            contextUuid.toString(),
            SubscriptionType.DELAYED_REQUEST,
            Instant.now()
        );
        
        // Mock context resolution
        when(environmentServiceClient.resolveContextUuid(domainId, solutionId, "default", "default"))
            .thenReturn(contextUuid);
        
        // Mock subscription service response
        when(subscriptionClient.getDelayedRequestSubscription(
            "StudentPersonal", contextUuid))
            .thenReturn(subscription);
        
        // Mock queue service response
        when(queueClient.enqueueMessage(eq(providerQueueId), any(SifMessageEnvelope.class), eq(domainId), eq(solutionId)))
            .thenReturn(messageId);
        
        // When: Handling the DELAYED_to_DELAYED request
        RequestResponseDTO response = handler.handleDelayedToDelayed(
            environmentId, environmentName, domainId, solutionId, provider, consumerQueueId, request, Collections.emptyMap(), "/StudentPersonal", "test-request-id");
        
        // Then: Response should be HTTP 202 Accepted with messageId
        assertThat(response).isNotNull();
        assertThat(response.getMessageId()).isEqualTo(messageId);
        assertThat(response.getQueueId()).isEqualTo(consumerQueueId);
        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getPayload()).isNull(); // DELAYED responses don't have payload
        
        // Verify context resolution was called
        verify(environmentServiceClient).resolveContextUuid(domainId, solutionId, "default", "default");
        
        // Verify subscription service was called with contextUuid
        verify(subscriptionClient).getDelayedRequestSubscription(
            "StudentPersonal", contextUuid);
        
        // Verify queue service was called with correct queueId
        ArgumentCaptor<SifMessageEnvelope> envelopeCaptor = 
            ArgumentCaptor.forClass(SifMessageEnvelope.class);
        verify(queueClient).enqueueMessage(eq(providerQueueId), envelopeCaptor.capture(), eq(domainId), eq(solutionId));
        
        // Verify envelope contains consumer's queueId for response routing
        SifMessageEnvelope<?> envelope = envelopeCaptor.getValue();
        assertThat(envelope.getQueueId()).isEqualTo(consumerQueueId);
        assertThat(envelope.getServiceName()).isEqualTo("StudentPersonal");
        assertThat(envelope.getPayload()).isEqualTo(Map.of("id", "123"));
    }
    
    @Test
    void handleDelayedToDelayed_withNoSubscription_shouldThrowNoSubscriptionException() {
        // Given: A provider with no request subscription
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        String domainId = "test-domain";
        String solutionId = "test-solution";
        UUID consumerQueueId = UUID.randomUUID();
        
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType(ServiceType.OBJECT.name());
        request.setMessageType(MessageType.REQUEST.name());
        request.setPayload(Map.of("id", "123"));
        
        ProviderRegistrationDTO provider = createProvider(
            "StudentPersonal", "default", "default",
            Set.of(MessageExchangePattern.DELAYED));
        
        // Mock context resolution
        UUID contextUuid = UUID.randomUUID();
        when(environmentServiceClient.resolveContextUuid(domainId, solutionId, "default", "default"))
            .thenReturn(contextUuid);
        
        // Mock subscription service returning null (no subscription)
        when(subscriptionClient.getDelayedRequestSubscription(
            "StudentPersonal", contextUuid))
            .thenReturn(null);
        
        // When/Then: Handling the request should throw NoSubscriptionException
        assertThatThrownBy(() -> 
            handler.handleDelayedToDelayed(
                environmentId, environmentName, domainId, solutionId, provider, consumerQueueId, request, Collections.emptyMap(), "/StudentPersonal", null))
            .isInstanceOf(NoSubscriptionException.class)
            .hasMessageContaining("Provider has no request subscription");
        
        // Verify context resolution was called
        verify(environmentServiceClient).resolveContextUuid(domainId, solutionId, "default", "default");
        
        // Verify subscription service was called with contextUuid
        verify(subscriptionClient).getDelayedRequestSubscription(
            "StudentPersonal", contextUuid);
        
        // Verify queue service was NOT called
        verify(queueClient, never()).enqueueMessage(any(), any());
    }
    
    @Test
    void handleDelayedToDelayed_withQueueServiceUnavailable_shouldThrowException() {
        // Given: Queue Service is unavailable
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        String domainId = "test-domain";
        String solutionId = "test-solution";
        UUID consumerQueueId = UUID.randomUUID();
        UUID providerQueueId = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();
        
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType(ServiceType.OBJECT.name());
        request.setMessageType(MessageType.REQUEST.name());
        request.setPayload(Map.of("id", "123"));
        
        ProviderRegistrationDTO provider = createProvider(
            "StudentPersonal", "default", "default",
            Set.of(MessageExchangePattern.DELAYED));
        
        SubscriptionDTO subscription = new SubscriptionDTO(
            UUID.randomUUID(),
            providerQueueId,
            null,
            "StudentPersonal",
            "OBJECT",
            contextUuid.toString(),
            SubscriptionType.DELAYED_REQUEST,
            Instant.now()
        );
        
        // Mock context resolution
        when(environmentServiceClient.resolveContextUuid(domainId, solutionId, "default", "default"))
            .thenReturn(contextUuid);
        
        // Mock subscription service response
        when(subscriptionClient.getDelayedRequestSubscription(
            "StudentPersonal", contextUuid))
            .thenReturn(subscription);
        
        // Mock queue service throwing exception (service unavailable)
        when(queueClient.enqueueMessage(eq(providerQueueId), any(SifMessageEnvelope.class), eq(domainId), eq(solutionId)))
            .thenThrow(WebClientResponseException.ServiceUnavailable.class);
        
        // When/Then: Handling the request should throw exception
        assertThatThrownBy(() -> 
            handler.handleDelayedToDelayed(
                environmentId, environmentName, domainId, solutionId, provider, consumerQueueId, request, Collections.emptyMap(), "/StudentPersonal", null))
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
        
        // Verify all services were called
        verify(environmentServiceClient).resolveContextUuid(domainId, solutionId, "default", "default");
        verify(subscriptionClient).getDelayedRequestSubscription(
            "StudentPersonal", contextUuid);
        verify(queueClient).enqueueMessage(eq(providerQueueId), any(SifMessageEnvelope.class), eq(domainId), eq(solutionId));
    }
    
    // ========== DELAYED to IMMEDIATE Flow Tests ==========
    
    @Test
    void handleDelayedToImmediate_withValidRequest_shouldStoreMetadataAndReturnAccepted() {
        // Given: A valid DELAYED request to IMMEDIATE-only provider
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        String domainId = "test-domain";
        String solutionId = "test-solution";
        UUID consumerQueueId = UUID.randomUUID();
        String providerEndpoint = "http://localhost:8080/provider";
        
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType(ServiceType.OBJECT.name());
        request.setMessageType(MessageType.REQUEST.name());
        request.setPayload(Map.of("id", "123"));
        
        ProviderRegistrationDTO provider = createProviderWithEndpoint(
            "StudentPersonal", "default", "default", providerEndpoint,
            Set.of(MessageExchangePattern.IMMEDIATE));
        
        // Mock Redis stream operations (XADD)
        when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.add(any(StringRecord.class))).thenReturn(RecordId.of("1234567890-0"));
        
        // When: Handling the DELAYED_to_IMMEDIATE request
        RequestResponseDTO response = handler.handleDelayedToImmediate(
            environmentId, environmentName, domainId, solutionId, provider, consumerQueueId, request, Collections.emptyMap(), "POST", "/StudentPersonal", "test-request-id");
        
        // Then: Response should be HTTP 202 Accepted with messageId
        assertThat(response).isNotNull();
        assertThat(response.getMessageId()).isNotNull();
        assertThat(response.getQueueId()).isEqualTo(consumerQueueId);
        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getPayload()).isNull(); // DELAYED responses don't have payload
        
        // Verify XADD was called with a StringRecord containing expected fields
        ArgumentCaptor<StringRecord> recordCaptor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOperations).add(recordCaptor.capture());
        
        StringRecord capturedRecord = recordCaptor.getValue();
        Map<String, String> fields = capturedRecord.getValue();
        assertThat(fields.get("messageId")).isEqualTo(response.getMessageId().toString());
        assertThat(fields.get("consumerQueueId")).isEqualTo(consumerQueueId.toString());
        assertThat(fields.get("providerEndpoint")).isEqualTo(providerEndpoint);
        assertThat(fields.get("environmentId")).isEqualTo(environmentId.toString());
        assertThat(fields.get("domainId")).isEqualTo(domainId);
        assertThat(fields.get("solutionId")).isEqualTo(solutionId);
        assertThat(fields.get("httpMethod")).isEqualTo("POST");
        assertThat(fields.get("requestEnvelope")).isNotNull();
        assertThat(fields.get("createdAt")).isNotNull();
        assertThat(capturedRecord.getStream()).isEqualTo("proxy-requests-stream");
    }
    
    @Test
    void handleDelayedToImmediate_shouldPreservePayload() {
        // Given: A request with a complex payload
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        String domainId = "test-domain";
        String solutionId = "test-solution";
        UUID consumerQueueId = UUID.randomUUID();
        
        Map<String, Object> complexPayload = Map.of(
            "id", "123",
            "name", "John Doe",
            "grade", 10
        );
        
        DelayedRequestDTO request = new DelayedRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType(ServiceType.OBJECT.name());
        request.setMessageType(MessageType.REQUEST.name());
        request.setPayload(complexPayload);
        
        ProviderRegistrationDTO provider = createProviderWithEndpoint(
            "StudentPersonal", "default", "default", "http://localhost:8080/provider",
            Set.of(MessageExchangePattern.IMMEDIATE));
        
        // Mock Redis stream operations (XADD)
        when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.add(any(StringRecord.class))).thenReturn(RecordId.of("1234567890-0"));
        
        // When: Handling the request
        handler.handleDelayedToImmediate(
            environmentId, environmentName, domainId, solutionId, provider, consumerQueueId, request, Collections.emptyMap(), "POST", "/StudentPersonal", null);
        
        // Then: requestEnvelope field should contain serialized envelope with payload
        ArgumentCaptor<StringRecord> recordCaptor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOperations).add(recordCaptor.capture());
        
        StringRecord capturedRecord = recordCaptor.getValue();
        Map<String, String> fields = capturedRecord.getValue();
        
        // The requestEnvelope is serialized as JSON — verify it contains the payload
        String envelopeJson = fields.get("requestEnvelope");
        assertThat(envelopeJson).isNotNull();
        assertThat(envelopeJson).contains("\"id\"");
        assertThat(envelopeJson).contains("\"name\"");
        assertThat(envelopeJson).contains("John Doe");
    }
    
    // ========== Helper Methods ==========
    
    private ProviderRegistrationDTO createProvider(
            String serviceName, String zoneId, String contextId,
            Set<MessageExchangePattern> patterns) {
        List<SupportedRequestType> requestTypes = patterns.stream()
            .filter(p -> p == MessageExchangePattern.IMMEDIATE || p == MessageExchangePattern.DELAYED)
            .map(p -> new SupportedRequestType(
                p == MessageExchangePattern.IMMEDIATE ? RequestType.IMMEDIATE : RequestType.DELAYED,
                OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))))
            .toList();
        return new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://localhost:8080/provider"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(requestTypes),
                Instant.now()
        );
    }
    
    private ProviderRegistrationDTO createProviderWithEndpoint(
            String serviceName, String zoneId, String contextId, String endpoint,
            Set<MessageExchangePattern> patterns) {
        List<SupportedRequestType> requestTypes = patterns.stream()
            .filter(p -> p == MessageExchangePattern.IMMEDIATE || p == MessageExchangePattern.DELAYED)
            .map(p -> new SupportedRequestType(
                p == MessageExchangePattern.IMMEDIATE ? RequestType.IMMEDIATE : RequestType.DELAYED,
                OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))))
            .toList();
        return new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of(endpoint),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(requestTypes),
                Instant.now()
        );
    }
}
