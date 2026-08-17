// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

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
 * Property 1: Envelope headers serialization round-trip.
 *
 * <p>For any valid {@link SifMessageEnvelope} with an arbitrary {@code Map<String, String>}
 * headers field (including empty and null), serializing the envelope to JSON and deserializing
 * it back should produce an envelope whose headers field equals the original (with null
 * normalized to empty map).</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3</strong></p>
 */
class EnvelopeHeadersRoundTripPropertyTest {

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
    Arbitrary<Map<String, String>> headerMapsWithNullAndEmpty() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(Collections.emptyMap()),
                headerMaps()
        );
    }

    // ========== Properties ==========

    /**
     * For any arbitrary headers map, a round-trip through JSON serialization
     * and deserialization preserves the headers field exactly.
     */
    @Property(tries = 100)
    @Tag("Feature: header-passthrough-and-sanitization, Property 1: Envelope headers serialization round-trip")
    void headersRoundTripThroughJson(
            @ForAll("headerMaps") Map<String, String> headers) throws Exception {

        SifMessageEnvelope<String> original = SifMessageEnvelope.<String>builder()
                .messageType(MessageType.EVENT)
                .serviceName("TestService")
                .serviceType(ServiceType.OBJECT)
                .headers(headers)
                .build();

        String json = objectMapper.writeValueAsString(original);
        SifMessageEnvelope<?> deserialized = objectMapper.readValue(json, SifMessageEnvelope.class);

        assertThat(deserialized.getHeaders()).isEqualTo(original.getHeaders());
    }

    /**
     * When headers is null, deserialization normalizes it to an empty map
     * (backward compatibility via @JsonSetter(nulls = Nulls.AS_EMPTY)).
     */
    @Example
    @Tag("Feature: header-passthrough-and-sanitization, Property 1: Envelope headers serialization round-trip")
    void nullHeadersNormalizedToEmptyMapOnRoundTrip() throws Exception {

        SifMessageEnvelope<String> original = SifMessageEnvelope.<String>builder()
                .messageType(MessageType.REQUEST)
                .serviceName("TestService")
                .serviceType(ServiceType.FUNCTIONAL)
                .build();

        // Manually inject null into the JSON to simulate pre-feature stored messages
        String json = objectMapper.writeValueAsString(original);
        String jsonWithNull = json.replace("\"headers\":{}", "\"headers\":null");

        SifMessageEnvelope<?> deserialized = objectMapper.readValue(jsonWithNull, SifMessageEnvelope.class);

        assertThat(deserialized.getHeaders()).isNotNull();
        assertThat(deserialized.getHeaders()).isEmpty();
    }

    /**
     * When headers field is absent from JSON entirely (pre-feature messages),
     * deserialization defaults to empty map.
     */
    @Example
    @Tag("Feature: header-passthrough-and-sanitization, Property 1: Envelope headers serialization round-trip")
    void missingHeadersFieldDeserializesToEmptyMap() throws Exception {

        SifMessageEnvelope<String> original = SifMessageEnvelope.<String>builder()
                .messageType(MessageType.RESPONSE)
                .serviceName("TestService")
                .serviceType(ServiceType.UTILITY)
                .build();

        // Remove the headers field entirely from JSON
        String json = objectMapper.writeValueAsString(original);
        String jsonWithoutHeaders = json.replaceAll(",?\"headers\":\\{[^}]*}", "");
        // Clean up any leading comma after removal
        jsonWithoutHeaders = jsonWithoutHeaders.replace("{,", "{");

        SifMessageEnvelope<?> deserialized = objectMapper.readValue(jsonWithoutHeaders, SifMessageEnvelope.class);

        assertThat(deserialized.getHeaders()).isNotNull();
        assertThat(deserialized.getHeaders()).isEmpty();
    }

    /**
     * For any headers map (including null and empty), the round-trip preserves
     * headers correctly, with null normalized to empty map.
     */
    @Property(tries = 100)
    @Tag("Feature: header-passthrough-and-sanitization, Property 1: Envelope headers serialization round-trip")
    void headersRoundTripIncludingNullAndEmpty(
            @ForAll("headerMapsWithNullAndEmpty") Map<String, String> headers) throws Exception {

        Map<String, String> effectiveHeaders = headers != null ? headers : Collections.emptyMap();

        SifMessageEnvelope<String> original = SifMessageEnvelope.<String>builder()
                .messageType(MessageType.EVENT)
                .serviceName("TestService")
                .serviceType(ServiceType.OBJECT)
                .headers(effectiveHeaders)
                .build();

        String json = objectMapper.writeValueAsString(original);
        SifMessageEnvelope<?> deserialized = objectMapper.readValue(json, SifMessageEnvelope.class);

        assertThat(deserialized.getHeaders()).isEqualTo(effectiveHeaders);
    }
}
