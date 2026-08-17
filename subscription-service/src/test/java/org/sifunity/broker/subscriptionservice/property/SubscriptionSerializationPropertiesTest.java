// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for subscription JSON serialization correctness properties.
 */
class SubscriptionSerializationPropertiesTest {

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Property(tries = 100)
    void subscriptionDTOSerializationRoundTripPreservesAllFields(
            @ForAll("subscriptionDTOs") SubscriptionDTO original) throws JsonProcessingException {

        String json = objectMapper.writeValueAsString(original);
        assertThat(json).isNotBlank();

        SubscriptionDTO deserialized = objectMapper.readValue(json, SubscriptionDTO.class);

        assertThat(deserialized).isNotNull();
        assertThat(deserialized.id()).isEqualTo(original.id());
        assertThat(deserialized.queueId()).isEqualTo(original.queueId());
        assertThat(deserialized.serviceName()).isEqualTo(original.serviceName());
        assertThat(deserialized.zoneId()).isEqualTo(original.zoneId());
        assertThat(deserialized.contextId()).isEqualTo(original.contextId());
        assertThat(deserialized.messageType()).isEqualTo(original.messageType());

        // createdAt is @JsonIgnore (internal, not part of the SIF 3.7 wire format), so it is
        // always dropped during serialization and is null after a JSON round-trip.
        assertThat(deserialized.createdAt()).isNull();
    }

    @Property(tries = 100)
    void subscriptionDTOSerializationPreservesNullZoneAndContext(
            @ForAll("subscriptionDTOsWithNullContext") SubscriptionDTO original) throws JsonProcessingException {

        assertThat(original.zoneId()).isNull();
        assertThat(original.contextId()).isNull();

        String json = objectMapper.writeValueAsString(original);
        SubscriptionDTO deserialized = objectMapper.readValue(json, SubscriptionDTO.class);

        assertThat(deserialized.zoneId()).isNull();
        assertThat(deserialized.contextId()).isNull();
    }

    @Property(tries = 100)
    void subscriptionDTOSerializationPreservesNonNullZoneAndContext(
            @ForAll("subscriptionDTOsWithNonNullContext") SubscriptionDTO original) throws JsonProcessingException {

        assertThat(original.zoneId()).isNotNull();
        assertThat(original.contextId()).isNotNull();

        String json = objectMapper.writeValueAsString(original);
        SubscriptionDTO deserialized = objectMapper.readValue(json, SubscriptionDTO.class);

        assertThat(deserialized.zoneId()).isEqualTo(original.zoneId());
        assertThat(deserialized.contextId()).isEqualTo(original.contextId());
    }

    @Property(tries = 100)
    void subscriptionDTOSerializationIncludesAllRequiredFields(
            @ForAll("subscriptionDTOs") SubscriptionDTO original) throws JsonProcessingException {

        String json = objectMapper.writeValueAsString(original);

        assertThat(json).contains("\"id\"");
        assertThat(json).contains("\"queueId\"");
        assertThat(json).contains("\"serviceName\"");
        assertThat(json).contains("\"messageType\"");
        // createdAt is internal (@JsonIgnore) — not part of the SIF 3.7 wire format
        assertThat(json).doesNotContain("\"createdAt\"");
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<SubscriptionDTO> subscriptionDTOs() {
        return Combinators.combine(
                Arbitraries.create(UUID::randomUUID),
                Arbitraries.create(UUID::randomUUID),
                validServiceNames(),
                optionalZoneIds(),
                optionalContextIds(),
                Arbitraries.of(SubscriptionType.values()),
                timestamps()
        ).as((id, queueId, sn, zone, ctx, type, ts) ->
                new SubscriptionDTO(id, queueId, UUID.randomUUID(), sn, zone, ctx, type, ts));
    }

    @Provide
    Arbitrary<SubscriptionDTO> subscriptionDTOsWithNullContext() {
        return Combinators.combine(
                Arbitraries.create(UUID::randomUUID),
                Arbitraries.create(UUID::randomUUID),
                validServiceNames(),
                Arbitraries.of(SubscriptionType.values()),
                timestamps()
        ).as((id, queueId, sn, type, ts) ->
                new SubscriptionDTO(id, queueId, UUID.randomUUID(), sn, null, null, type, ts));
    }

    @Provide
    Arbitrary<SubscriptionDTO> subscriptionDTOsWithNonNullContext() {
        return Combinators.combine(
                Arbitraries.create(UUID::randomUUID),
                Arbitraries.create(UUID::randomUUID),
                validServiceNames(),
                validServiceNames(),
                validServiceNames(),
                Arbitraries.of(SubscriptionType.values()),
                timestamps()
        ).as((id, queueId, sn, zone, ctx, type, ts) ->
                new SubscriptionDTO(id, queueId, UUID.randomUUID(), sn, zone, ctx, type, ts));
    }

    @Provide
    Arbitrary<String> validServiceNames() {
        Arbitrary<Character> firstChar = Arbitraries.chars().range('a', 'z');
        Arbitrary<String> restChars = Arbitraries.strings()
                .withCharRange('a', 'z').withCharRange('A', 'Z')
                .withCharRange('0', '9').withChars('_', '-')
                .ofMinLength(2).ofMaxLength(29);
        return Combinators.combine(firstChar, restChars).as((first, rest) -> first + rest);
    }

    @Provide
    Arbitrary<String> optionalZoneIds() {
        return Arbitraries.oneOf(Arbitraries.just(null), validServiceNames());
    }

    @Provide
    Arbitrary<String> optionalContextIds() {
        return Arbitraries.oneOf(Arbitraries.just(null), validServiceNames());
    }

    @Provide
    Arbitrary<Instant> timestamps() {
        return Arbitraries.longs()
                .between(0, Instant.now().getEpochSecond())
                .map(Instant::ofEpochSecond);
    }
}
