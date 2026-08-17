// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.servicesconnector.client.EnvironmentServiceClient;
import org.sifunity.servicesconnector.client.QueueServiceClient;
import org.sifunity.servicesconnector.client.SubscriptionServiceClient;
import org.sifunity.servicesconnector.dto.ProxyRequestMetadata;
import org.sifunity.servicesconnector.exception.NoSubscriptionException;
import org.sifunity.servicesconnector.service.DelayedRequestHandler;
import org.sifunity.servicesconnector.util.MessageBuilder;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Unit tests for {@link DelayedRequestHandler}.
 *
 * <p>Tests both DELAYED_to_DELAYED and DELAYED_to_IMMEDIATE sub-patterns,
 * verifying subscription lookup, envelope building, queue enqueue, Redis storage,
 * and error handling.</p>
 *
 * <p><strong>Validates: Requirements 5.2, 5.3, 5.4, 5.5</strong></p>
 */
@ExtendWith(MockitoExtension.class)
class DelayedRequestHandlerTest {

    @Mock
    private SubscriptionServiceClient subscriptionClient;

    @Mock
    private QueueServiceClient queueClient;

    @Mock
    private RedisTemplate<String, ProxyRequestMetadata> redisTemplate;

    @Mock
    private EnvironmentServiceClient environmentServiceClient;

    @Mock
    private ListOperations<String, ProxyRequestMetadata> listOperations;

    private DelayedRequestHandler handler;

    // Common test fixtures
    private final UUID environmentId = UUID.randomUUID();
    private final UUID consumerQueueId = UUID.randomUUID();
    private final UUID providerQueueId = UUID.randomUUID();
    private final UUID contextUuid = UUID.randomUUID();
    private final String environmentName = "TestConsumer";
    private final String domainId = "domain-1";
    private final String solutionId = "solution-1";
    private final String zoneId = "zone1";
    private final String contextId = "ctx1";
    private final String serviceName = "DataExport";
    private final Map<String, String> headers = Map.of("X-Custom", "value");
    private final Map<String, Object> requestBody = Map.of("data", "test-payload");

    @BeforeEach
    void setUp() {
        handler = new DelayedRequestHandler(
                subscriptionClient, queueClient, new MessageBuilder(),
                redisTemplate, environmentServiceClient);
    }

    // ── DELAYED_to_DELAYED Tests ────────────────────────────────────────

    @Nested
    class DelayedToDelayedTests {

        private ProviderRegistrationDTO provider;

        @BeforeEach
        void setUp() {
            provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com"),
                ServiceType.FUNCTIONAL,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))))),
                Instant.now()
        );
        }

        @Test
        void returnsAcceptedResponseWithMessageIdAndQueueId() {
            // Arrange
            SubscriptionDTO subscription = new SubscriptionDTO(
                    UUID.randomUUID(), providerQueueId, null, serviceName,
                    "FUNCTIONAL", zoneId, contextId,
                    SubscriptionType.DELAYED_REQUEST, Instant.now());

            when(environmentServiceClient.resolveContextUuid(domainId, solutionId, zoneId, contextId))
                    .thenReturn(contextUuid);
            when(subscriptionClient.getDelayedRequestSubscription(domainId, solutionId, serviceName, contextUuid))
                    .thenReturn(subscription);
            when(queueClient.enqueueMessage(eq(domainId), eq(solutionId), eq(providerQueueId), any()))
                    .thenReturn(UUID.randomUUID());

            // Act
            Map<String, String> result = handler.handleDelayedToDelayed(
                    "POST", serviceName, null, null,
                    environmentId, environmentName, domainId, solutionId,
                    provider, consumerQueueId, zoneId, contextId,
                    (String) null,
                    requestBody, headers);

            // Assert
            assertThat(result).containsEntry("status", "ACCEPTED");
            assertThat(result).containsEntry("queueId", consumerQueueId.toString());
            assertThat(result).containsKey("messageId");
            assertThat(UUID.fromString(result.get("messageId"))).isNotNull();
        }

        @Test
        void resolvesContextUuidBeforeSubscriptionLookup() {
            // Arrange
            SubscriptionDTO subscription = new SubscriptionDTO(
                    UUID.randomUUID(), providerQueueId, null, serviceName,
                    "FUNCTIONAL", zoneId, contextId,
                    SubscriptionType.DELAYED_REQUEST, Instant.now());

            when(environmentServiceClient.resolveContextUuid(domainId, solutionId, zoneId, contextId))
                    .thenReturn(contextUuid);
            when(subscriptionClient.getDelayedRequestSubscription(domainId, solutionId, serviceName, contextUuid))
                    .thenReturn(subscription);
            when(queueClient.enqueueMessage(eq(domainId), eq(solutionId), eq(providerQueueId), any()))
                    .thenReturn(UUID.randomUUID());

            // Act
            handler.handleDelayedToDelayed(
                    "POST", serviceName, null, null,
                    environmentId, environmentName, domainId, solutionId,
                    provider, consumerQueueId, zoneId, contextId,
                    (String) null,
                    requestBody, headers);

            // Assert — verify context resolution happened with provider's zone/context
            verify(environmentServiceClient).resolveContextUuid(domainId, solutionId, zoneId, contextId);
            verify(subscriptionClient).getDelayedRequestSubscription(domainId, solutionId, serviceName, contextUuid);
        }

        @Test
        void enqueuesToProviderSubscriptionQueue() {
            // Arrange
            SubscriptionDTO subscription = new SubscriptionDTO(
                    UUID.randomUUID(), providerQueueId, null, serviceName,
                    "FUNCTIONAL", zoneId, contextId,
                    SubscriptionType.DELAYED_REQUEST, Instant.now());

            when(environmentServiceClient.resolveContextUuid(domainId, solutionId, zoneId, contextId))
                    .thenReturn(contextUuid);
            when(subscriptionClient.getDelayedRequestSubscription(domainId, solutionId, serviceName, contextUuid))
                    .thenReturn(subscription);

            UUID expectedMessageId = UUID.randomUUID();
            when(queueClient.enqueueMessage(eq(domainId), eq(solutionId), eq(providerQueueId), any()))
                    .thenReturn(expectedMessageId);

            // Act
            Map<String, String> result = handler.handleDelayedToDelayed(
                    "POST", serviceName, null, null,
                    environmentId, environmentName, domainId, solutionId,
                    provider, consumerQueueId, zoneId, contextId,
                    (String) null,
                    requestBody, headers);

            // Assert — verify enqueue was called with provider's queue
            ArgumentCaptor<SifMessageEnvelope> envelopeCaptor = ArgumentCaptor.forClass(SifMessageEnvelope.class);
            verify(queueClient).enqueueMessage(eq(domainId), eq(solutionId), eq(providerQueueId), envelopeCaptor.capture());

            SifMessageEnvelope<?> envelope = envelopeCaptor.getValue();
            assertThat(envelope.getServiceName()).isEqualTo(serviceName);
            assertThat(envelope.getServiceType()).isEqualTo(ServiceType.FUNCTIONAL);
            assertThat(envelope.getMessageType()).isEqualTo(MessageType.REQUEST);
            assertThat(envelope.getQueueId()).isEqualTo(consumerQueueId);
            assertThat(envelope.getZoneId()).isEqualTo(zoneId);
            assertThat(envelope.getPayload()).isEqualTo(requestBody);

            // The returned messageId should be the one from the queue service
            assertThat(result.get("messageId")).isEqualTo(expectedMessageId.toString());
        }

        @Test
        void throwsNoSubscriptionExceptionWhenNoSubscriptionFound() {
            // Arrange
            when(environmentServiceClient.resolveContextUuid(domainId, solutionId, zoneId, contextId))
                    .thenReturn(contextUuid);
            when(subscriptionClient.getDelayedRequestSubscription(domainId, solutionId, serviceName, contextUuid))
                    .thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> handler.handleDelayedToDelayed(
                    "POST", serviceName, null, null,
                    environmentId, environmentName, domainId, solutionId,
                    provider, consumerQueueId, zoneId, contextId,
                    (String) null,
                    requestBody, headers))
                .isInstanceOf(NoSubscriptionException.class)
                .hasMessageContaining(serviceName)
                .hasMessageContaining(contextUuid.toString());
        }

        @Test
        void handlesNullRequestBody() {
            // Arrange
            SubscriptionDTO subscription = new SubscriptionDTO(
                    UUID.randomUUID(), providerQueueId, null, serviceName,
                    "FUNCTIONAL", zoneId, contextId,
                    SubscriptionType.DELAYED_REQUEST, Instant.now());

            when(environmentServiceClient.resolveContextUuid(domainId, solutionId, zoneId, contextId))
                    .thenReturn(contextUuid);
            when(subscriptionClient.getDelayedRequestSubscription(domainId, solutionId, serviceName, contextUuid))
                    .thenReturn(subscription);
            when(queueClient.enqueueMessage(eq(domainId), eq(solutionId), eq(providerQueueId), any()))
                    .thenReturn(UUID.randomUUID());

            // Act — should not throw for null body (GET/DELETE)
            Map<String, String> result = handler.handleDelayedToDelayed(
                    "GET", serviceName, "job-123", null,
                    environmentId, environmentName, domainId, solutionId,
                    provider, consumerQueueId, zoneId, contextId,
                    (String) null,
                    null, headers);

            // Assert
            assertThat(result).containsEntry("status", "ACCEPTED");
        }
    }


    // ── DELAYED_to_IMMEDIATE Tests ──────────────────────────────────────

    @Nested
    class DelayedToImmediateTests {

        private ProviderRegistrationDTO provider;

        @BeforeEach
        void setUp() {
            provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com"),
                ServiceType.FUNCTIONAL,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))))),
                Instant.now()
        );

            lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        }

        @Test
        void returnsAcceptedResponseWithMessageIdAndQueueId() {
            // Act
            Map<String, String> result = handler.handleDelayedToImmediate(
                    "POST", serviceName, null, null,
                    environmentId, environmentName,
                    provider, consumerQueueId, zoneId, contextId,
                    (String) null,
                    requestBody, headers);

            // Assert
            assertThat(result).containsEntry("status", "ACCEPTED");
            assertThat(result).containsEntry("queueId", consumerQueueId.toString());
            assertThat(result).containsKey("messageId");
            assertThat(UUID.fromString(result.get("messageId"))).isNotNull();
        }

        @Test
        void storesProxyRequestMetadataInRedis() {
            // Act
            handler.handleDelayedToImmediate(
                    "POST", serviceName, "job-456", "export",
                    environmentId, environmentName,
                    provider, consumerQueueId, zoneId, contextId,
                    (String) null,
                    requestBody, headers);

            // Assert — verify Redis rightPush was called
            ArgumentCaptor<ProxyRequestMetadata> metadataCaptor =
                    ArgumentCaptor.forClass(ProxyRequestMetadata.class);
            verify(listOperations).rightPush(eq("functional-proxy-requests"), metadataCaptor.capture());

            ProxyRequestMetadata metadata = metadataCaptor.getValue();
            assertThat(metadata.getConsumerQueueId()).isEqualTo(consumerQueueId);
            assertThat(metadata.getEnvironmentId()).isEqualTo(environmentId);
            assertThat(metadata.getEnvironmentName()).isEqualTo(environmentName);
            assertThat(metadata.getHttpMethod()).isEqualTo("POST");
            assertThat(metadata.getPassthroughHeaders()).isEqualTo(headers);
            assertThat(metadata.getRetryCount()).isZero();
            assertThat(metadata.getCreatedAt()).isNotNull();
        }

        @Test
        void buildsEnvelopeWithCorrectFields() {
            // Act
            handler.handleDelayedToImmediate(
                    "PUT", serviceName, "job-789", "validation",
                    environmentId, environmentName,
                    provider, consumerQueueId, zoneId, contextId,
                    (String) null,
                    requestBody, headers);

            // Assert — capture the metadata and inspect the envelope
            ArgumentCaptor<ProxyRequestMetadata> metadataCaptor =
                    ArgumentCaptor.forClass(ProxyRequestMetadata.class);
            verify(listOperations).rightPush(eq("functional-proxy-requests"), metadataCaptor.capture());

            ProxyRequestMetadata metadata = metadataCaptor.getValue();
            assertThat(metadata.getRequestEnvelope()).isNotNull();

            // The envelope is stored as Object but was built by MessageBuilder
            @SuppressWarnings("unchecked")
            SifMessageEnvelope<Object> envelope = (SifMessageEnvelope<Object>) metadata.getRequestEnvelope();
            assertThat(envelope.getServiceName()).isEqualTo(serviceName);
            assertThat(envelope.getServiceType()).isEqualTo(ServiceType.FUNCTIONAL);
            assertThat(envelope.getMessageType()).isEqualTo(MessageType.REQUEST);
            assertThat(envelope.getQueueId()).isEqualTo(consumerQueueId);
            assertThat(envelope.getSourceId()).isEqualTo(environmentId);
            assertThat(envelope.getPayload()).isEqualTo(requestBody);
        }

        @Test
        void constructsProviderUrlInMetadata() {
            // Act
            handler.handleDelayedToImmediate(
                    "POST", serviceName, "job-123", "export/states/state",
                    environmentId, environmentName,
                    provider, consumerQueueId, zoneId, contextId,
                    (String) null,
                    requestBody, headers);

            // Assert — verify the provider endpoint path was constructed correctly
            ArgumentCaptor<ProxyRequestMetadata> metadataCaptor =
                    ArgumentCaptor.forClass(ProxyRequestMetadata.class);
            verify(listOperations).rightPush(eq("functional-proxy-requests"), metadataCaptor.capture());

            ProxyRequestMetadata metadata = metadataCaptor.getValue();
            // buildProviderUrl("http://provider.example.com", "DataExport", "job-123", "export/states/state")
            assertThat(metadata.getProviderEndpoint())
                    .isEqualTo("http://provider.example.com/DataExport/job-123/export/states/state");
        }

        @Test
        void handlesNullRequestBody() {
            // Act — should not throw for null body (GET/DELETE)
            Map<String, String> result = handler.handleDelayedToImmediate(
                    "GET", serviceName, "job-123", null,
                    environmentId, environmentName,
                    provider, consumerQueueId, zoneId, contextId,
                    (String) null,
                    null, headers);

            // Assert
            assertThat(result).containsEntry("status", "ACCEPTED");
            verify(listOperations).rightPush(eq("functional-proxy-requests"), any());
        }

        @Test
        void doesNotCallSubscriptionClientOrQueueClient() {
            // Act
            handler.handleDelayedToImmediate(
                    "POST", serviceName, null, null,
                    environmentId, environmentName,
                    provider, consumerQueueId, zoneId, contextId,
                    (String) null,
                    requestBody, headers);

            // Assert — DELAYED_to_IMMEDIATE should NOT use subscription or queue clients
            verifyNoInteractions(subscriptionClient);
            verifyNoInteractions(queueClient);
        }
    }

    // ── Error Cases ─────────────────────────────────────────────────────

    @Nested
    class ErrorCases {

        private ProviderRegistrationDTO provider;

        @BeforeEach
        void setUp() {
            provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com"),
                ServiceType.FUNCTIONAL,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))))),
                Instant.now()
        );
        }

        @Test
        void delayedToDelayed_throwsNoSubscriptionException_whenSubscriptionNull() {
            when(environmentServiceClient.resolveContextUuid(domainId, solutionId, zoneId, contextId))
                    .thenReturn(contextUuid);
            when(subscriptionClient.getDelayedRequestSubscription(domainId, solutionId, serviceName, contextUuid))
                    .thenReturn(null);

            assertThatThrownBy(() -> handler.handleDelayedToDelayed(
                    "POST", serviceName, null, null,
                    environmentId, environmentName, domainId, solutionId,
                    provider, consumerQueueId, zoneId, contextId,
                    (String) null,
                    requestBody, headers))
                .isInstanceOf(NoSubscriptionException.class);
        }

        @Test
        void delayedToDelayed_propagatesQueueClientException() {
            SubscriptionDTO subscription = new SubscriptionDTO(
                    UUID.randomUUID(), providerQueueId, null, serviceName,
                    "FUNCTIONAL", zoneId, contextId,
                    SubscriptionType.DELAYED_REQUEST, Instant.now());

            when(environmentServiceClient.resolveContextUuid(domainId, solutionId, zoneId, contextId))
                    .thenReturn(contextUuid);
            when(subscriptionClient.getDelayedRequestSubscription(domainId, solutionId, serviceName, contextUuid))
                    .thenReturn(subscription);
            when(queueClient.enqueueMessage(eq(domainId), eq(solutionId), eq(providerQueueId), any()))
                    .thenThrow(new RuntimeException("Queue service unavailable"));

            assertThatThrownBy(() -> handler.handleDelayedToDelayed(
                    "POST", serviceName, null, null,
                    environmentId, environmentName, domainId, solutionId,
                    provider, consumerQueueId, zoneId, contextId,
                    (String) null,
                    requestBody, headers))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Queue service unavailable");
        }
    }
}
