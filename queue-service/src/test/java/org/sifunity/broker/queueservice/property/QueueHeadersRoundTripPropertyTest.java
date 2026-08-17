// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 9: Queue headers round-trip.
 *
 * <p>For any {@link SifMessageEnvelope} with an arbitrary headers map, serializing
 * the envelope to JSON (simulating StorageProvider persistence) and deserializing it
 * back should produce a headers map equal to the original. Null headers are normalized
 * to an empty map via {@code @JsonSetter(nulls = Nulls.AS_EMPTY)}.</p>
 *
 * <p><strong>Validates: Requirements 6.1, 6.2, 6.4, 8.1</strong></p>
 */
class QueueHeadersRoundTripPropertyTest {

    private static final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<Map<String, String>> headerMaps() {
        Arbitrary<String> keys = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30);
        Arbitrary<String> values = Arbitraries.strings()
                .ascii()
                .ofMinLength(0)
                .ofMaxLength(50);
        return Arbitraries.maps(keys, values)
                .ofMinSize(0)
                .ofMaxSize(20);
    }

    @Provide
    Arbitrary<MessageType> messageTypes() {
        return Arbitraries.of(MessageType.values());
    }

    @Provide
    Arbitrary<ServiceType> serviceTypes() {
        return Arbitraries.of(ServiceType.values());
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
    }

    // ========== Properties ==========

    /**
     * For any SifMessageEnvelope with arbitrary headers, a round-trip through JSON
     * serialization (simulating StorageProvider store/retrieve) preserves the headers
     * map exactly.
     */
    @Property(tries = 100)
    @Tag("Feature: header-passthrough-and-sanitization, Property 9: Queue headers round-trip")
    void headersPreservedThroughStorageRoundTrip(
            @ForAll("headerMaps") Map<String, String> headers,
            @ForAll("messageTypes") MessageType messageType,
            @ForAll("serviceTypes") ServiceType serviceType,
            @ForAll("serviceNames") String serviceName) throws Exception {

        SifMessageEnvelope<String> original = SifMessageEnvelope.<String>builder()
                .messageType(messageType)
                .serviceName(serviceName)
                .serviceType(serviceType)
                .headers(headers)
                .payload("test-payload")
                .build();

        // Simulate StorageProvider: serialize to JSON (store) then deserialize (retrieve)
        String json = objectMapper.writeValueAsString(original);
        SifMessageEnvelope<?> deserialized = objectMapper.readValue(json, SifMessageEnvelope.class);

        assertThat(deserialized.getHeaders())
                .as("Headers should be preserved through JSON round-trip (storage simulation)")
                .isEqualTo(original.getHeaders());
    }

    /**
     * When headers is empty, the round-trip preserves it as an empty map.
     */
    @Property(tries = 100)
    @Tag("Feature: header-passthrough-and-sanitization, Property 9: Queue headers round-trip")
    void emptyHeadersPreservedThroughStorageRoundTrip(
            @ForAll("messageTypes") MessageType messageType,
            @ForAll("serviceTypes") ServiceType serviceType,
            @ForAll("serviceNames") String serviceName) throws Exception {

        SifMessageEnvelope<String> original = SifMessageEnvelope.<String>builder()
                .messageType(messageType)
                .serviceName(serviceName)
                .serviceType(serviceType)
                .headers(Collections.emptyMap())
                .payload("test-payload")
                .build();

        String json = objectMapper.writeValueAsString(original);
        SifMessageEnvelope<?> deserialized = objectMapper.readValue(json, SifMessageEnvelope.class);

        assertThat(deserialized.getHeaders())
                .as("Empty headers should remain empty after round-trip")
                .isNotNull()
                .isEmpty();
    }

    /**
     * When headers field is null in stored JSON (pre-feature messages), deserialization
     * normalizes it to an empty map via @JsonSetter(nulls = Nulls.AS_EMPTY).
     */
    @Example
    @Tag("Feature: header-passthrough-and-sanitization, Property 9: Queue headers round-trip")
    void nullHeadersNormalizedToEmptyMapOnRetrieval() throws Exception {

        SifMessageEnvelope<String> original = SifMessageEnvelope.<String>builder()
                .messageType(MessageType.EVENT)
                .serviceName("TestService")
                .serviceType(ServiceType.OBJECT)
                .build();

        // Simulate a pre-feature stored message with null headers
        String json = objectMapper.writeValueAsString(original);
        String jsonWithNull = json.replace("\"headers\":{}", "\"headers\":null");

        SifMessageEnvelope<?> deserialized = objectMapper.readValue(jsonWithNull, SifMessageEnvelope.class);

        assertThat(deserialized.getHeaders())
                .as("Null headers should be normalized to empty map on retrieval")
                .isNotNull()
                .isEmpty();
    }

    /**
     * When headers field is absent from stored JSON entirely (pre-feature messages),
     * deserialization defaults to an empty map.
     */
    @Example
    @Tag("Feature: header-passthrough-and-sanitization, Property 9: Queue headers round-trip")
    void missingHeadersFieldDeserializesToEmptyMap() throws Exception {

        SifMessageEnvelope<String> original = SifMessageEnvelope.<String>builder()
                .messageType(MessageType.RESPONSE)
                .serviceName("TestService")
                .serviceType(ServiceType.UTILITY)
                .build();

        // Remove the headers field entirely from JSON to simulate pre-feature stored messages
        String json = objectMapper.writeValueAsString(original);
        String jsonWithoutHeaders = json.replaceAll(",?\"headers\":\\{[^}]*}", "");
        jsonWithoutHeaders = jsonWithoutHeaders.replace("{,", "{");

        SifMessageEnvelope<?> deserialized = objectMapper.readValue(jsonWithoutHeaders, SifMessageEnvelope.class);

        assertThat(deserialized.getHeaders())
                .as("Missing headers field should default to empty map")
                .isNotNull()
                .isEmpty();
    }

    /**
     * Full envelope fields (including all optional SIF headers) are preserved alongside
     * the passthrough headers through the storage round-trip.
     */
    @Property(tries = 100)
    @Tag("Feature: header-passthrough-and-sanitization, Property 9: Queue headers round-trip")
    void fullEnvelopeWithHeadersPreservedThroughRoundTrip(
            @ForAll("headerMaps") Map<String, String> headers,
            @ForAll("messageTypes") MessageType messageType,
            @ForAll("serviceTypes") ServiceType serviceType,
            @ForAll("serviceNames") String serviceName) throws Exception {

        SifMessageEnvelope<String> original = SifMessageEnvelope.<String>builder()
                .messageType(messageType)
                .serviceName(serviceName)
                .serviceType(serviceType)
                .zoneId("test-zone")
                .contextId("test-context")
                .headers(headers)
                .payload("test-payload")
                .build();

        String json = objectMapper.writeValueAsString(original);
        SifMessageEnvelope<?> deserialized = objectMapper.readValue(json, SifMessageEnvelope.class);

        assertThat(deserialized.getHeaders())
                .as("Headers should be preserved when full envelope fields are present")
                .isEqualTo(original.getHeaders());
        assertThat(deserialized.getMessageType()).isEqualTo(original.getMessageType());
        assertThat(deserialized.getServiceName()).isEqualTo(original.getServiceName());
        assertThat(deserialized.getServiceType()).isEqualTo(original.getServiceType());
        assertThat(deserialized.getZoneId()).isEqualTo(original.getZoneId());
        assertThat(deserialized.getContextId()).isEqualTo(original.getContextId());
    }
}
