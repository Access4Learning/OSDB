// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.servicesconnector.util.MessageBuilder;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1: Pass-through payload preservation.
 *
 * <p>For any valid JSON request body sent by a consumer, the services-connector
 * SHALL forward the exact same body to the provider without modification, and
 * for any provider response body, the services-connector SHALL return it to the
 * consumer without transformation.</p>
 *
 * <p>This test verifies two aspects:</p>
 * <ol>
 *   <li>The {@link MessageBuilder} preserves the payload exactly as-is in the
 *       {@link SifMessageEnvelope} — no transformation, no field extraction.</li>
 *   <li>The envelope payload survives JSON serialization round-trip unchanged,
 *       ensuring the provider receives the exact payload the consumer sent.</li>
 * </ol>
 *
 * <p><strong>Validates: Requirements 2.2, 2.5, 3.1, 3.2, 6.1, 6.2, 6.3, 6.4</strong></p>
 */
@PropertyDefaults(tries = 20)
class PassthroughPayloadPreservationPropertyTest {

    private static final ObjectMapper objectMapper = createObjectMapper();
    private final MessageBuilder messageBuilder = new MessageBuilder();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<Map<String, Object>> jsonPayloads() {
        Arbitrary<String> keys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<Object> values = Arbitraries.oneOf(
            Arbitraries.strings().ascii().ofMinLength(0).ofMaxLength(100).map(s -> (Object) s),
            Arbitraries.integers().between(-10000, 10000).map(i -> (Object) i),
            Arbitraries.doubles().between(-1e6, 1e6)
                    .filter(d -> !d.isNaN() && !d.isInfinite())
                    .map(d -> (Object) d),
            Arbitraries.of(true, false).map(b -> (Object) b)
        );
        return Arbitraries.maps(keys, values)
                .ofMinSize(0)
                .ofMaxSize(10)
                .map(m -> new LinkedHashMap<>(m));
    }

    @Provide
    Arbitrary<Object> nestedJsonObjects() {
        Arbitrary<String> keys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<Object> values = Arbitraries.oneOf(
            Arbitraries.strings().ascii().ofMinLength(0).ofMaxLength(50).map(s -> (Object) s),
            Arbitraries.integers().between(-1000, 1000).map(i -> (Object) i),
            Arbitraries.of(true, false).map(b -> (Object) b)
        );
        return Arbitraries.maps(keys, values)
                .ofMinSize(0)
                .ofMaxSize(10)
                .map(m -> (Object) new LinkedHashMap<>(m));
    }

    @Provide
    Arbitrary<Object> jsonArrays() {
        Arbitrary<Object> elements = Arbitraries.oneOf(
            Arbitraries.strings().ascii().ofMinLength(0).ofMaxLength(50).map(s -> (Object) s),
            Arbitraries.integers().between(-1000, 1000).map(i -> (Object) i),
            Arbitraries.of(true, false).map(b -> (Object) b)
        );
        return elements.list().ofMinSize(0).ofMaxSize(10).map(l -> (Object) l);
    }

    @Provide
    Arbitrary<Object> providerResponses() {
        return Arbitraries.oneOf(
            nestedJsonObjects(),
            jsonArrays(),
            Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(200).map(s -> (Object) s),
            Arbitraries.integers().between(-10000, 10000).map(i -> (Object) i)
        );
    }

    @Provide
    Arbitrary<Map<String, String>> headerMaps() {
        Arbitrary<String> keys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> values = Arbitraries.strings().ascii().ofMinLength(0).ofMaxLength(50);
        return Arbitraries.maps(keys, values).ofMinSize(0).ofMaxSize(5);
    }

    // ========== Properties ==========

    /**
     * The MessageBuilder SHALL place the consumer's request body into the envelope
     * payload field without any modification. The payload stored in the envelope
     * must be reference-equal to the input.
     *
     * <p><strong>Validates: Requirements 2.2, 3.1, 3.2, 6.1, 6.2, 6.3, 6.4</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 1: Pass-through payload preservation")
    void messageBuilderPreservesRequestPayloadExactly(
            @ForAll("jsonPayloads") Map<String, Object> payload,
            @ForAll("headerMaps") Map<String, String> headers) {

        UUID environmentId = UUID.randomUUID();
        String zoneId = "testZone";
        String contextId = "testContext";

        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
                "TestService", environmentId, zoneId, contextId, payload, headers);

        // Payload must be the exact same object — no cloning, no transformation
        assertThat(envelope.getPayload()).isSameAs(payload);
        // Envelope metadata must be set correctly
        assertThat(envelope.getServiceType()).isEqualTo(ServiceType.FUNCTIONAL);
        assertThat(envelope.getMessageType()).isEqualTo(MessageType.REQUEST);
    }

    /**
     * The MessageBuilder for DELAYED requests SHALL also place the consumer's
     * request body into the envelope payload field without modification.
     *
     * <p><strong>Validates: Requirements 2.2, 3.1, 3.2, 6.1, 6.2, 6.3, 6.4</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 1: Pass-through payload preservation")
    void messageBuilderPreservesDelayedRequestPayloadExactly(
            @ForAll("jsonPayloads") Map<String, Object> payload,
            @ForAll("headerMaps") Map<String, String> headers) {

        UUID environmentId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                "TestService", environmentId, "zone1", "ctx1", queueId, (String) null, payload, headers);

        assertThat(envelope.getPayload()).isSameAs(payload);
        assertThat(envelope.getServiceType()).isEqualTo(ServiceType.FUNCTIONAL);
        assertThat(envelope.getQueueId()).isEqualTo(queueId);
    }

    /**
     * The envelope payload SHALL survive JSON serialization round-trip unchanged.
     * When the provider deserializes the envelope, the payload must be structurally
     * identical to what the consumer sent.
     *
     * <p><strong>Validates: Requirements 2.2, 2.5</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 1: Pass-through payload preservation")
    void payloadSurvivesJsonSerializationRoundTrip(
            @ForAll("jsonPayloads") Map<String, Object> payload) throws Exception {

        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
                "TestService", UUID.randomUUID(), "zone1", null, payload, Collections.emptyMap());

        // Serialize to JSON (as would happen when sending to provider)
        String json = objectMapper.writeValueAsString(envelope);

        // Deserialize back (as provider would receive it)
        SifMessageEnvelope<?> deserialized = objectMapper.readValue(json, SifMessageEnvelope.class);

        // Payload must be structurally equal after round-trip
        assertThat(deserialized.getPayload()).isEqualTo(envelope.getPayload());
    }

    /**
     * Null payloads (e.g., GET/DELETE requests with no body) SHALL be preserved
     * as null through the MessageBuilder — no default empty object substitution.
     *
     * <p><strong>Validates: Requirements 2.2, 6.1, 6.2</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 1: Pass-through payload preservation")
    void nullPayloadIsPreserved(
            @ForAll("headerMaps") Map<String, String> headers) {

        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
                "TestService", UUID.randomUUID(), "zone1", "ctx1", null, headers);

        assertThat(envelope.getPayload()).isNull();
    }

    /**
     * The MessageBuilder SHALL not extract or interpret any fields from the payload.
     * Even if the payload contains fields like "serviceName", "zoneId", or "contextId",
     * they must remain untouched in the envelope payload — routing uses URL/headers only.
     *
     * <p><strong>Validates: Requirements 3.1, 3.2, 6.3, 6.4</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 1: Pass-through payload preservation")
    void payloadWithRoutingFieldsIsNotInterpreted(
            @ForAll("jsonPayloads") Map<String, Object> basePayload) {

        // Inject routing-like fields into the payload
        Map<String, Object> payloadWithRoutingFields = new LinkedHashMap<>(basePayload);
        payloadWithRoutingFields.put("serviceName", "SneakyService");
        payloadWithRoutingFields.put("zoneId", "sneakyZone");
        payloadWithRoutingFields.put("contextId", "sneakyContext");

        String actualServiceName = "RealService";
        String actualZoneId = "realZone";
        String actualContextId = "realContext";

        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
                actualServiceName, UUID.randomUUID(), actualZoneId, actualContextId,
                payloadWithRoutingFields, Collections.emptyMap());

        // Envelope routing fields come from parameters, not payload
        assertThat(envelope.getServiceName()).isEqualTo(actualServiceName);
        assertThat(envelope.getZoneId()).isEqualTo(actualZoneId);
        assertThat(envelope.getContextId()).isEqualTo(actualContextId);

        // Payload is preserved with the "sneaky" fields still in it
        @SuppressWarnings("unchecked")
        Map<String, Object> envelopePayload = (Map<String, Object>) envelope.getPayload();
        assertThat(envelopePayload).containsEntry("serviceName", "SneakyService");
        assertThat(envelopePayload).containsEntry("zoneId", "sneakyZone");
        assertThat(envelopePayload).containsEntry("contextId", "sneakyContext");
    }

    /**
     * A provider response object SHALL be returnable as-is without transformation.
     * This verifies that arbitrary response shapes survive JSON round-trip,
     * confirming the connector can return any provider response unchanged.
     *
     * <p><strong>Validates: Requirements 2.5</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 1: Pass-through payload preservation")
    void providerResponseSurvivesJsonRoundTrip(
            @ForAll("providerResponses") Object providerResponse) throws Exception {

        // Simulate: provider response is serialized to JSON and deserialized back
        String json = objectMapper.writeValueAsString(providerResponse);
        Object deserialized = objectMapper.readValue(json, Object.class);

        assertThat(deserialized).isEqualTo(providerResponse);
    }
}
