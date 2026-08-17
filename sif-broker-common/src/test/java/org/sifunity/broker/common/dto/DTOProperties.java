// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for all DTOs.
 * Tests universal properties like JSON serialization round-trip and DTO equality.
 */
class DTOProperties {

    private static final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<Instant> instants() {
        return Arbitraries.longs()
            .between(0, System.currentTimeMillis())
            .map(Instant::ofEpochMilli);
    }

    @Provide
    Arbitrary<String> nonBlankStrings() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(50);
    }

    @Provide
    Arbitrary<EnvironmentType> environmentTypes() {
        return Arbitraries.of(EnvironmentType.values());
    }

    @Provide
    Arbitrary<MessageStatus> messageStatuses() {
        return Arbitraries.of(MessageStatus.values());
    }

    @Provide
    Arbitrary<SubscriptionType> subscriptionTypes() {
        return Arbitraries.of(SubscriptionType.values());
    }

    @Provide
    Arbitrary<JobState> jobStates() {
        return Arbitraries.of(JobState.values());
    }

    @Provide
    Arbitrary<AccessType> accessTypes() {
        return Arbitraries.of(AccessType.values());
    }

    @Provide
    Arbitrary<AccessLevel> accessLevels() {
        return Arbitraries.of(AccessLevel.values());
    }

    @Provide
    Arbitrary<MessageExchangePattern> messageExchangePatterns() {
        return Arbitraries.of(MessageExchangePattern.values());
    }

    @Provide
    Arbitrary<EnvironmentDTO> environments() {
        return Combinators.combine(
            uuids(),
            nonBlankStrings(),
            nonBlankStrings(),
            instants(),
            nonBlankStrings(),
            environmentTypes()
        ).as((id, appName, token, created, zoneId, type) ->
            new EnvironmentDTO(id, appName, token, created, null, zoneId, type, null)
        );
    }

    @Provide
    Arbitrary<QueueDTO> queues() {
        return Combinators.combine(
            uuids(),
            nonBlankStrings(),
            uuids(),
            instants(),
            Arbitraries.integers().between(1, 10),
            Arbitraries.longs().between(1000L, 60000L)
        ).as((id, name, ownerId, created, retries, delay) ->
            new QueueDTO(id, name, ownerId, "IMMEDIATE", null, 1,
                        created, null, null, retries, delay, 0L)
        );
    }

    @Provide
    Arbitrary<SubscriptionDTO> subscriptions() {
        return Combinators.combine(
            uuids(),
            uuids(),
            uuids(),
            nonBlankStrings(),
            nonBlankStrings(),
            nonBlankStrings(),
            subscriptionTypes(),
            instants()
        ).as((id, queueId, aclEntryId, serviceName, zoneId, contextId, type, created) ->
            new SubscriptionDTO(id, queueId, aclEntryId, serviceName, zoneId, contextId, type, created)
        );
    }

    @Provide
    Arbitrary<MessageDTO> messages() {
        return Combinators.combine(
            uuids(),
            uuids(),
            nonBlankStrings(),
            instants(),
            messageStatuses()
        ).as((id, queueId, storageKey, enqueued, status) ->
            new MessageDTO(id, queueId, storageKey, enqueued, null, 0, status)
        );
    }

    @Provide
    Arbitrary<AclEntryDTO> aclEntries() {
        return Combinators.combine(
            uuids(),
            uuids(),
            nonBlankStrings(),
            accessTypes(),
            accessLevels()
        ).as((id, envId, serviceName, accessType, accessLevel) ->
            new AclEntryDTO(id, envId, serviceName, null, null, accessType, accessLevel)
        );
    }

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
        );
    }
    
    @Provide
    Arbitrary<List<SupportedRequestType>> supportedRequestTypeLists() {
        Arbitrary<SupportedRequestType> srt = Combinators.combine(
            Arbitraries.of(RequestType.values()),
            Arbitraries.of(Operation.values()).set().ofMinSize(1).ofMaxSize(4)
        ).as((requestType, operations) ->
                new SupportedRequestType(requestType, OperationsDTO.of(operations)));
        return srt.list().ofMinSize(1).ofMaxSize(2);
    }

    @Provide
    Arbitrary<JobDTO> jobs() {
        return Combinators.combine(
            uuids(),
            uuids(),
            nonBlankStrings(),
            nonBlankStrings(),
            jobStates(),
            instants()
        ).as((id, envId, serviceName, zoneId, state, created) ->
            new JobDTO(id, envId, serviceName, zoneId, null, state, created, null, null)
        );
    }

    // ========== Property 2: DTO Equality ==========
    // **Validates: Requirements 1.3**

    @Property
    @Tag("Feature-sif-broker-common-Property-2-DTO-Equality")
    void environmentDTO_equalityIsConsistent(@ForAll("environments") EnvironmentDTO dto1,
                                             @ForAll("environments") EnvironmentDTO dto2) {
        // If two DTOs are equal, their hashCodes must be equal
        if (dto1.equals(dto2)) {
            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-2-DTO-Equality")
    void queueDTO_equalityIsConsistent(@ForAll("queues") QueueDTO dto1,
                                       @ForAll("queues") QueueDTO dto2) {
        if (dto1.equals(dto2)) {
            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-2-DTO-Equality")
    void subscriptionDTO_equalityIsConsistent(@ForAll("subscriptions") SubscriptionDTO dto1,
                                              @ForAll("subscriptions") SubscriptionDTO dto2) {
        if (dto1.equals(dto2)) {
            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-2-DTO-Equality")
    void messageDTO_equalityIsConsistent(@ForAll("messages") MessageDTO dto1,
                                         @ForAll("messages") MessageDTO dto2) {
        if (dto1.equals(dto2)) {
            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-2-DTO-Equality")
    void aclEntryDTO_equalityIsConsistent(@ForAll("aclEntries") AclEntryDTO dto1,
                                          @ForAll("aclEntries") AclEntryDTO dto2) {
        if (dto1.equals(dto2)) {
            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-2-DTO-Equality")
    void providerRegistrationDTO_equalityIsConsistent(@ForAll("providerRegistrations") ProviderRegistrationDTO dto1,
                                                      @ForAll("providerRegistrations") ProviderRegistrationDTO dto2) {
        if (dto1.equals(dto2)) {
            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-2-DTO-Equality")
    void jobDTO_equalityIsConsistent(@ForAll("jobs") JobDTO dto1,
                                     @ForAll("jobs") JobDTO dto2) {
        if (dto1.equals(dto2)) {
            assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        }
    }

    // ========== Property 3: JSON Serialization Round-Trip ==========
    // **Validates: Requirements 1.6, 8.1, 8.2, 8.3**

    @Property
    @Tag("Feature-sif-broker-common-Property-3-JSON-Serialization-Round-Trip")
    void environmentDTO_jsonRoundTrip(@ForAll("environments") EnvironmentDTO original) throws Exception {
        String json = objectMapper.writeValueAsString(original);
        EnvironmentDTO deserialized = objectMapper.readValue(json, EnvironmentDTO.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-3-JSON-Serialization-Round-Trip")
    void queueDTO_jsonRoundTrip(@ForAll("queues") QueueDTO original) throws Exception {
        String json = objectMapper.writeValueAsString(original);
        QueueDTO deserialized = objectMapper.readValue(json, QueueDTO.class);
        // maxRetries and retryDelayMs are @JsonIgnore (internal fields excluded from SIF 3.7 wire),
        // so they become null after round-trip — same pattern as SubscriptionDTO.createdAt
        QueueDTO expected = new QueueDTO(
            original.id(), original.name(), original.ownerId(), original.polling(),
            original.queueUri(), original.maxConcurrentConnections(), original.created(),
            original.lastAccessed(), original.lastModified(), null, null, original.messageCount()
        );
        assertThat(deserialized).isEqualTo(expected);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-3-JSON-Serialization-Round-Trip")
    void subscriptionDTO_jsonRoundTrip(@ForAll("subscriptions") SubscriptionDTO original) throws Exception {
        String json = objectMapper.writeValueAsString(original);
        SubscriptionDTO deserialized = objectMapper.readValue(json, SubscriptionDTO.class);
        // aclEntryId is WRITE_ONLY and createdAt is @JsonIgnore (both excluded from serialization),
        // so both become null after round-trip
        SubscriptionDTO expected = new SubscriptionDTO(
            original.id(), original.queueId(), null, original.serviceName(),
            original.serviceType(), original.zoneId(), original.contextId(), original.messageType(), null
        );
        assertThat(deserialized).isEqualTo(expected);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-3-JSON-Serialization-Round-Trip")
    void messageDTO_jsonRoundTrip(@ForAll("messages") MessageDTO original) throws Exception {
        String json = objectMapper.writeValueAsString(original);
        MessageDTO deserialized = objectMapper.readValue(json, MessageDTO.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-3-JSON-Serialization-Round-Trip")
    void aclEntryDTO_jsonRoundTrip(@ForAll("aclEntries") AclEntryDTO original) throws Exception {
        String json = objectMapper.writeValueAsString(original);
        AclEntryDTO deserialized = objectMapper.readValue(json, AclEntryDTO.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-3-JSON-Serialization-Round-Trip")
    void providerRegistrationDTO_jsonRoundTrip(@ForAll("providerRegistrations") ProviderRegistrationDTO original) throws Exception {
        String json = objectMapper.writeValueAsString(original);
        ProviderRegistrationDTO deserialized = objectMapper.readValue(json, ProviderRegistrationDTO.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-3-JSON-Serialization-Round-Trip")
    void jobDTO_jsonRoundTrip(@ForAll("jobs") JobDTO original) throws Exception {
        String json = objectMapper.writeValueAsString(original);
        JobDTO deserialized = objectMapper.readValue(json, JobDTO.class);
        assertThat(deserialized).isEqualTo(original);
    }
}
