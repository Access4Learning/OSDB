// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.MessageExchangePattern;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.requestsconnector.dto.ProxyRequestMetadata;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;
import org.sifunity.broker.common.dto.OperationsDTO;

/**
 * Property-based test: Redis serialization round-trip with restricted typing.
 *
 * <p>For any valid ProviderRegistrationDTO (with arbitrary field values including nested
 * collections and Instant timestamps) and ProxyRequestMetadata, serializing to JSON with
 * the restricted-typing ObjectMapper and then deserializing SHALL produce an object whose
 * field values are equal to the original.</p>
 *
 * <p><strong>Validates: Requirements 4.4, 4.5</strong></p>
 */
@Tag("Feature: n-plus-one-and-cache-fixes, Property 5: Redis serialization round-trip")
class RedisSerializationRoundTripPropertyTest {

    private final ObjectMapper redisObjectMapper;

    RedisSerializationRoundTripPropertyTest() {
        // Configure identically to production redisObjectMapper() bean
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType("org.sifunity.broker")
            .allowIfSubType("org.sifunity.broker")
            .allowIfBaseType("org.sifunity.requestsconnector")
            .allowIfSubType("org.sifunity.requestsconnector")
            .allowIfBaseType("java.util.")
            .allowIfSubType("java.util.")
            .build();

        mapper.activateDefaultTyping(ptv,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY);

        this.redisObjectMapper = mapper;
    }

    /**
     * Property: ProviderRegistrationDTO round-trips through restricted-typing ObjectMapper.
     *
     * Validates: Requirements 4.4, 4.5
     */
    @Property(tries = 20)
    void providerRegistrationDtoRoundTrip(
            @ForAll("providerRegistrations") ProviderRegistrationDTO original) throws Exception {

        // Serialize to JSON with restricted-typing ObjectMapper
        String json = redisObjectMapper.writeValueAsString(original);

        // Deserialize back
        ProviderRegistrationDTO deserialized = redisObjectMapper.readValue(json, ProviderRegistrationDTO.class);

        // Assert field equality
        assertThat(deserialized.id()).isEqualTo(original.id());
        assertThat(deserialized.environmentId()).isEqualTo(original.environmentId());
        assertThat(deserialized.serviceName()).isEqualTo(original.serviceName());
        assertThat(deserialized.zoneId()).isEqualTo(original.zoneId());
        assertThat(deserialized.contextId()).isEqualTo(original.contextId());
        assertThat(ProtocolDTO.locationOf(deserialized.endPoint())).isEqualTo(ProtocolDTO.locationOf(original.endPoint()));
        assertThat(deserialized.serviceType()).isEqualTo(original.serviceType());
        assertThat(deserialized.providerName()).isEqualTo(original.providerName());
        assertThat(deserialized.registeredAt()).isEqualTo(original.registeredAt());

        // Verify nested collections — supportedRequestTypes list
        assertThat(deserialized.supportedRequestTypes().supportedRequestType()).hasSize(original.supportedRequestTypes().supportedRequestType().size());
        for (int i = 0; i < original.supportedRequestTypes().supportedRequestType().size(); i++) {
            SupportedRequestType origSrt = original.supportedRequestTypes().supportedRequestType().get(i);
            SupportedRequestType deserSrt = deserialized.supportedRequestTypes().supportedRequestType().get(i);
            assertThat(deserSrt.requestType()).isEqualTo(origSrt.requestType());
            // Set<Operation> — verify no elements lost or duplicated
            assertThat(deserSrt.operations().operation()).containsExactlyInAnyOrderElementsOf(origSrt.operations().operation());
            assertThat(deserSrt.operations().operation()).hasSameSizeAs(origSrt.operations().operation());
        }
    }

    /**
     * Property: ProxyRequestMetadata round-trips through restricted-typing ObjectMapper.
     *
     * Validates: Requirements 4.4
     */
    @Property(tries = 20)
    void proxyRequestMetadataRoundTrip(
            @ForAll("proxyRequestMetadatas") ProxyRequestMetadata original) throws Exception {

        // Serialize to JSON with restricted-typing ObjectMapper
        String json = redisObjectMapper.writeValueAsString(original);

        // Deserialize back
        ProxyRequestMetadata deserialized = redisObjectMapper.readValue(json, ProxyRequestMetadata.class);

        // Assert field equality
        assertThat(deserialized.getMessageId()).isEqualTo(original.getMessageId());
        assertThat(deserialized.getConsumerQueueId()).isEqualTo(original.getConsumerQueueId());
        assertThat(deserialized.getEnvironmentId()).isEqualTo(original.getEnvironmentId());
        assertThat(deserialized.getEnvironmentName()).isEqualTo(original.getEnvironmentName());
        assertThat(deserialized.getDomainId()).isEqualTo(original.getDomainId());
        assertThat(deserialized.getSolutionId()).isEqualTo(original.getSolutionId());
        assertThat(deserialized.getHttpMethod()).isEqualTo(original.getHttpMethod());
        assertThat(deserialized.getProviderEnvironmentId()).isEqualTo(original.getProviderEnvironmentId());
        assertThat(deserialized.getRelativeServicePath()).isEqualTo(original.getRelativeServicePath());
        assertThat(deserialized.getProviderEndpoint()).isEqualTo(original.getProviderEndpoint());
        assertThat(deserialized.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(deserialized.getRetryCount()).isEqualTo(original.getRetryCount());
    }

    /**
     * Property: Set&lt;MessageExchangePattern&gt; round-trips correctly with no lost or duplicated elements.
     *
     * Validates: Requirements 4.5
     */
    @Property(tries = 20)
    void messageExchangePatternSetRoundTrip(
            @ForAll("messageExchangePatternSets") Set<MessageExchangePattern> original) throws Exception {

        // Serialize with restricted-typing ObjectMapper
        String json = redisObjectMapper.writeValueAsString(original);

        // Deserialize back — use TypeReference for generic Set
        Set<?> rawDeserialized = redisObjectMapper.readValue(json, Set.class);

        // Convert back to enum values (default typing serializes enums as strings within typed containers)
        Set<MessageExchangePattern> deserialized = new HashSet<>();
        for (Object item : rawDeserialized) {
            deserialized.add(MessageExchangePattern.valueOf(item.toString()));
        }

        // Assert no elements lost or duplicated
        assertThat(deserialized).containsExactlyInAnyOrderElementsOf(original);
        assertThat(deserialized).hasSameSizeAs(original);
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<ProviderRegistrationDTO> providerRegistrations() {
        return Combinators.combine(
            uuids(),
            uuids(),
            nonBlankStrings(),
            nonBlankStrings(),
            nonBlankStrings(),
            Arbitraries.of(ServiceType.values()),
            nonBlankStrings(),
            supportedRequestTypeLists()
        ).as((id, envId, serviceName, zoneId, endpoint, serviceType, consumerName, srtList) ->
            new ProviderRegistrationDTO(
                id,
                envId,
                serviceName,
                zoneId,
                null,
                consumerName,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of(endpoint),
                serviceType,
                SupportedRequestsDTO.of(srtList),
                Instant.now()
        )
        ).flatMap(dto -> instants().map(registeredAt ->
            new ProviderRegistrationDTO(
                dto.id(),
                dto.environmentId(),
                dto.serviceName(),
                dto.zoneId(),
                dto.contextId(),
                dto.providerName(),
                QuerySupportDTO.unspecified(),
                dto.endPoint(),
                dto.serviceType(),
                SupportedRequestsDTO.of(dto.supportedRequestTypes().supportedRequestType()),
                registeredAt
        )
        ));
    }

    @Provide
    Arbitrary<List<SupportedRequestType>> supportedRequestTypeLists() {
        Arbitrary<SupportedRequestType> srt = Combinators.combine(
            Arbitraries.of(RequestType.values()),
            Arbitraries.of(Operation.values()).set().ofMinSize(1).ofMaxSize(4)
        ).as((requestType, operations) ->
                new SupportedRequestType(requestType, OperationsDTO.of(operations)));
        return srt.list().ofMinSize(1).ofMaxSize(3);
    }

    @Provide
    Arbitrary<ProxyRequestMetadata> proxyRequestMetadatas() {
        return Combinators.combine(
            uuids(),
            uuids(),
            uuids(),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
            Arbitraries.of("GET", "POST", "PUT", "DELETE"),
            uuids()
        ).as((messageId, consumerQueueId, envId, envName, domainId, solutionId, httpMethod, providerEnvId) ->
            ProxyRequestMetadata.builder()
                .messageId(messageId)
                .consumerQueueId(consumerQueueId)
                .environmentId(envId)
                .environmentName(envName)
                .domainId(domainId)
                .solutionId(solutionId)
                .httpMethod(httpMethod)
                .providerEnvironmentId(providerEnvId)
                .requestEnvelope(null)
                .retryCount(0)
                .build()
        ).flatMap(base -> Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(30),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20).map(h -> "http://" + h + ".example.com/api"),
            instants(),
            Arbitraries.integers().between(0, 5)
        ).as((relPath, endpoint, createdAt, retryCount) -> {
            base.setRelativeServicePath(relPath);
            base.setProviderEndpoint(endpoint);
            base.setCreatedAt(createdAt);
            base.setRetryCount(retryCount);
            return base;
        }));
    }

    @Provide
    Arbitrary<Set<MessageExchangePattern>> messageExchangePatternSets() {
        return Arbitraries.of(MessageExchangePattern.values()).set().ofMinSize(1).ofMaxSize(3);
    }

    private Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    private Arbitrary<String> nonBlankStrings() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }

    private Arbitrary<Instant> instants() {
        return Arbitraries.longs()
            .between(0, Instant.now().getEpochSecond())
            .map(Instant::ofEpochSecond);
    }
}
