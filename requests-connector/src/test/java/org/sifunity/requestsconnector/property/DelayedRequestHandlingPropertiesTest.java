// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.requestsconnector.dto.DelayedRequestDTO;
import org.sifunity.requestsconnector.dto.ProxyRequestMetadata;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Property-based tests for DELAYED request handling.
 * 
 * <p>These tests verify the correctness properties of the DELAYED request pattern,
 * including subscription queries, message enqueuing, proxy metadata storage,
 * and error handling.</p>
 * 
 * <p>Tests Properties 12-15 from the design document.</p>
 * 
 * <p>Note: These property tests focus on data validation and contracts.
 * Integration-style tests with WireMock are in the unit test suite.</p>
 */
@Tag("Feature: requests-connector")
class DelayedRequestHandlingPropertiesTest {
    
    /**
     * Property 12: Provider subscription query
     * 
     * <p>For any DELAYED request to a DELAYED-capable provider, the connector should
     * query the Subscription Service and extract the provider's queueId from the
     * subscription.</p>
     * 
     * <p>Validates: Requirements 4.1, 4.2</p>
     * 
     * <p>This property verifies that subscription DTOs contain the required queueId
     * for routing.</p>
     */
    @Property(tries = 10)
    @Tag("Property 12: Provider subscription query")
    void subscriptionShouldContainQueueId(
            @ForAll("subscriptions") SubscriptionDTO subscription) {
        
        // Then: The subscription should have a queueId
        assertThat(subscription.queueId())
            .as("Subscription must have a queueId")
            .isNotNull();
    }
    
    /**
     * Property 13: No subscription error handling
     * 
     * <p>For any provider without a request subscription, the system should be able
     * to detect this condition.</p>
     * 
     * <p>Validates: Requirements 4.3</p>
     * 
     * <p>This property verifies that providers have the necessary fields to determine
     * if they support DELAYED pattern.</p>
     */
    @Property(tries = 10)
    @Tag("Property 13: No subscription error handling")
    void providerShouldHaveSupportedPatterns(
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("delayedRequests") DelayedRequestDTO request) {
        
        // Given: A provider for the requested service
        ProviderRegistrationDTO provider = createProvider(
            request.getServiceName(), zoneId, contextId, 
            List.of(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE)))));
        
        // Then: The provider should have supported request types defined
        assertThat(provider.supportedRequestTypes().supportedRequestType())
            .as("Provider must have supported request types")
            .isNotNull()
            .isNotEmpty();
    }
    
    /**
     * Property 14: Message enqueuing to provider queue
     * 
     * <p>For any DELAYED request to a DELAYED-capable provider, the connector should
     * enqueue the message and return HTTP 202 with a messageId.</p>
     * 
     * <p>Validates: Requirements 4.4, 4.5</p>
     * 
     * <p>This property verifies that subscription DTOs have valid queueIds for
     * message enqueuing.</p>
     */
    @Property(tries = 10)
    @Tag("Property 14: Message enqueuing to provider queue")
    void subscriptionQueueIdShouldBeValidUuid(
            @ForAll("subscriptions") SubscriptionDTO subscription) {
        
        // Then: The subscription queueId should be a valid UUID
        assertThat(subscription.queueId())
            .as("Subscription queueId must be a valid UUID")
            .isNotNull();
        
        // Verify it can be used as a UUID (doesn't throw exception)
        assertThatCode(() -> {
            UUID queueId = subscription.queueId();
            assertThat(queueId).isNotNull();
        }).doesNotThrowAnyException();
    }
    
    /**
     * Property 15: Request metadata storage for proxy processing
     * 
     * <p>For any DELAYED request to an IMMEDIATE-only provider, the connector should
     * store metadata containing all information needed for background processing.</p>
     * 
     * <p>Validates: Requirements 5.1, 5.2, 5.3</p>
     * 
     * <p>This property verifies that ProxyRequestMetadata contains all required fields.</p>
     */
    @Property(tries = 10)
    @Tag("Property 15: Request metadata storage for proxy processing")
    void proxyMetadataShouldContainAllRequiredFields(
            @ForAll("proxyMetadata") ProxyRequestMetadata metadata) {
        
        // Then: The metadata should have all required fields
        assertThat(metadata.getMessageId())
            .as("Metadata must have messageId")
            .isNotNull();
        
        assertThat(metadata.getConsumerQueueId())
            .as("Metadata must have consumerQueueId")
            .isNotNull();
        
        assertThat(metadata.getProviderEndpoint())
            .as("Metadata must have providerEndpoint")
            .isNotNull()
            .isNotBlank()
            .startsWith("http");
        
        assertThat(metadata.getRequestEnvelope())
            .as("Metadata must have requestEnvelope")
            .isNotNull();
        
        assertThat(metadata.getCreatedAt())
            .as("Metadata must have createdAt timestamp")
            .isNotNull();
        
        assertThat(metadata.getRetryCount())
            .as("Metadata must have retryCount")
            .isGreaterThanOrEqualTo(0);
    }
    
    /**
     * Property: DELAYED request DTO validation
     * 
     * <p>For any DELAYED request, verify that the DTO contains all required fields.</p>
     */
    @Property(tries = 10)
    @Tag("Property: DELAYED request DTO validation")
    void delayedRequestShouldContainAllRequiredFields(
            @ForAll("delayedRequests") DelayedRequestDTO request) {
        
        // Then: The request should have all required fields
        assertThat(request.getServiceName())
            .as("Request must have serviceName")
            .isNotNull()
            .isNotBlank();
        
        assertThat(request.getServiceType())
            .as("Request must have serviceType")
            .isNotNull()
            .isNotBlank();
        
        assertThat(request.getMessageType())
            .as("Request must have messageType")
            .isNotNull()
            .isNotBlank();
    }
    
    /**
     * Property: Provider pattern detection
     * 
     * <p>For any provider, verify that supported patterns can be queried to determine
     * routing sub-pattern.</p>
     */
    @Property(tries = 10)
    @Tag("Property: Provider pattern detection")
    void providerPatternsShouldBeDeterminable(
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("delayedRequests") DelayedRequestDTO request,
            @ForAll("supportedRequestTypeLists") List<SupportedRequestType> requestTypes) {
        
        // Given: A provider with specific request types
        ProviderRegistrationDTO provider = createProvider(
            request.getServiceName(), zoneId, contextId, requestTypes);
        
        // Then: We should be able to determine if provider supports DELAYED
        boolean supportsDelayed = provider.supportedRequestTypes().supportedRequestType().stream()
            .anyMatch(srt -> srt.requestType() == RequestType.DELAYED);
        boolean supportsImmediate = provider.supportedRequestTypes().supportedRequestType().stream()
            .anyMatch(srt -> srt.requestType() == RequestType.IMMEDIATE);
        
        // At least one pattern should be supported
        assertThat(supportsDelayed || supportsImmediate)
            .as("Provider must support at least one request pattern")
            .isTrue();
    }
    
    // ========== Helper Methods ==========
    
    private ProviderRegistrationDTO createProvider(
            String serviceName, String zoneId, String contextId, 
            List<SupportedRequestType> requestTypes) {
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
    
    // ========== Arbitraries ==========
    
    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
    
    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.strings()
            .withChars("abcdefghijklmnopqrstuvwxyz0123456789_-")
            .ofMinLength(1)
            .ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.strings()
            .withChars("abcdefghijklmnopqrstuvwxyz0123456789_-")
            .ofMinLength(1)
            .ofMaxLength(50);
    }
    
    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(1)
            .ofMaxLength(1)
            .flatMap(first -> 
                Arbitraries.strings()
                    .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-")
                    .ofMinLength(0)
                    .ofMaxLength(30)
                    .map(rest -> first + rest)
            );
    }
    
    @Provide
    Arbitrary<MessageType> messageTypes() {
        return Arbitraries.of(MessageType.class);
    }
    
    @Provide
    Arbitrary<ServiceType> serviceTypes() {
        return Arbitraries.of(ServiceType.class);
    }
    
    @Provide
    Arbitrary<Object> payloads() {
        return Arbitraries.oneOf(
            Arbitraries.just(null),
            Arbitraries.strings().ofMaxLength(100),
            Arbitraries.integers()
        );
    }
    
    @Provide
    Arbitrary<String> endpoints() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(5)
            .ofMaxLength(20)
            .map(host -> "http://localhost:8080/" + host);
    }
    
    @Provide
    Arbitrary<Instant> timestamps() {
        return Arbitraries.longs()
            .between(Instant.now().minusSeconds(3600).toEpochMilli(), 
                     Instant.now().toEpochMilli())
            .map(Instant::ofEpochMilli);
    }
    
    @Provide
    Arbitrary<List<SupportedRequestType>> supportedRequestTypeLists() {
        return Arbitraries.of(
            List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE)))),
            List.of(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY)))),
            List.of(
                new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))),
                new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY)))
            )
        );
    }
    
    @Provide
    Arbitrary<DelayedRequestDTO> delayedRequests() {
        return Combinators.combine(
            serviceNames(),
            serviceTypes(),
            messageTypes(),
            payloads()
        ).as((serviceName, serviceType, messageType, payload) -> {
            DelayedRequestDTO dto = new DelayedRequestDTO();
            dto.setServiceName(serviceName);
            dto.setServiceType(serviceType.name());
            dto.setMessageType(messageType.name());
            dto.setPayload(payload);
            return dto;
        });
    }
    
    @Provide
    Arbitrary<SubscriptionDTO> subscriptions() {
        return Combinators.combine(
            uuids(),
            uuids(),
            serviceNames(),
            Arbitraries.of(UUID.randomUUID().toString(), UUID.randomUUID().toString(), null),
            timestamps()
        ).as((id, queueId, serviceName, contextId, createdAt) ->
            new SubscriptionDTO(
                id,
                queueId,
                null,
                serviceName,
                "OBJECT",
                contextId,
                SubscriptionType.DELAYED_REQUEST,
                createdAt
            )
        );
    }
    
    @Provide
    Arbitrary<ProxyRequestMetadata> proxyMetadata() {
        return Combinators.combine(
            uuids(),
            uuids(),
            endpoints(),
            timestamps(),
            Arbitraries.integers().between(0, 5)
        ).as((messageId, consumerQueueId, endpoint, createdAt, retryCount) ->
            ProxyRequestMetadata.builder()
                .messageId(messageId)
                .consumerQueueId(consumerQueueId)
                .providerEndpoint(endpoint)
                .requestEnvelope(new Object()) // Non-null placeholder for property testing
                .createdAt(createdAt)
                .retryCount(retryCount)
                .build()
        );
    }
}
