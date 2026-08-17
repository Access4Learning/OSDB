// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.servicesconnector.util.MessageBuilder;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 4: Routing parameters extracted from headers and URL, not body.
 *
 * <p>For any request to the services-connector, the functionalServiceName SHALL
 * be extracted from the URL path variable, and the zoneId and contextId SHALL
 * be extracted from SIF headers (or environment defaults), regardless of what
 * fields are present in the request body.</p>
 *
 * <p>This test verifies that the {@link MessageBuilder} uses the parameters
 * passed to it (which come from URL path variables and SIF headers in the
 * controller) rather than anything from the request body. Even when the body
 * contains fields named "serviceName", "zoneId", or "contextId", the envelope's
 * routing fields must reflect the URL/header values.</p>
 *
 * <p><strong>Validates: Requirements 3.3, 3.4, 3.5, 2.1</strong></p>
 */
@PropertyDefaults(tries = 20)
class RoutingParameterExtractionPropertyTest {

    private final MessageBuilder messageBuilder = new MessageBuilder();

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> functionalServiceNames() {
        return Arbitraries.of(
            "DataExport", "BulkImport", "StudentPersonalRefresh",
            "GradebookSync", "AttendanceReport", "EnrollmentTransfer"
        );
    }

    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.of(
            "zone-alpha", "zone-beta", "DEFAULT", "district-42", "school-99"
        );
    }

    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.of(
            "ctx-main", "ctx-test", "DEFAULT", "semester-fall", null
        );
    }

    @Provide
    Arbitrary<Map<String, Object>> bodiesWithRoutingFields() {
        // Generate bodies that deliberately contain serviceName, zoneId, contextId
        // to try to trick the system into using body values for routing
        Arbitrary<String> bodyServiceNames = Arbitraries.of(
            "SneakyService", "WrongService", "BodyService", "HackedService"
        );
        Arbitrary<String> bodyZoneIds = Arbitraries.of(
            "wrong-zone", "body-zone", "fake-zone", "hacked-zone"
        );
        Arbitrary<String> bodyContextIds = Arbitraries.of(
            "wrong-ctx", "body-ctx", "fake-ctx", "hacked-ctx"
        );
        // Additional random fields to make the body realistic
        Arbitrary<String> extraKeys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15);
        Arbitrary<Object> extraValues = Arbitraries.oneOf(
            Arbitraries.strings().ascii().ofMinLength(0).ofMaxLength(50).map(s -> (Object) s),
            Arbitraries.integers().between(-1000, 1000).map(i -> (Object) i),
            Arbitraries.of(true, false).map(b -> (Object) b)
        );

        return Combinators.combine(bodyServiceNames, bodyZoneIds, bodyContextIds,
                Arbitraries.maps(extraKeys, extraValues).ofMinSize(0).ofMaxSize(5))
            .as((sn, zi, ci, extras) -> {
                Map<String, Object> body = new LinkedHashMap<>(extras);
                body.put("serviceName", sn);
                body.put("zoneId", zi);
                body.put("contextId", ci);
                return body;
            });
    }

    @Provide
    Arbitrary<Map<String, String>> passthroughHeaders() {
        Arbitrary<String> keys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15);
        Arbitrary<String> values = Arbitraries.strings().ascii().ofMinLength(0).ofMaxLength(30);
        return Arbitraries.maps(keys, values).ofMinSize(0).ofMaxSize(5);
    }

    // ========== Properties ==========

    /**
     * The MessageBuilder SHALL use the functionalServiceName parameter (from URL
     * path variable) as the envelope's serviceName, even when the request body
     * contains a "serviceName" field with a different value.
     *
     * <p><strong>Validates: Requirements 3.5, 2.1</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 4: Routing parameters extracted from headers and URL, not body")
    void serviceNameComesFromUrlPathNotBody(
            @ForAll("functionalServiceNames") String urlServiceName,
            @ForAll("bodiesWithRoutingFields") Map<String, Object> body,
            @ForAll("passthroughHeaders") Map<String, String> headers) {

        UUID environmentId = UUID.randomUUID();
        String zoneId = "real-zone";

        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
                urlServiceName, environmentId, zoneId, "real-ctx", body, headers);

        // The envelope's serviceName MUST come from the URL path parameter, not the body
        assertThat(envelope.getServiceName()).isEqualTo(urlServiceName);
        assertThat(envelope.getServiceName()).isNotEqualTo(body.get("serviceName"));
    }

    /**
     * The MessageBuilder SHALL use the zoneId parameter (from SIF header) as the
     * envelope's zoneId, even when the request body contains a "zoneId" field
     * with a different value.
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 4: Routing parameters extracted from headers and URL, not body")
    void zoneIdComesFromHeaderNotBody(
            @ForAll("zoneIds") String headerZoneId,
            @ForAll("bodiesWithRoutingFields") Map<String, Object> body,
            @ForAll("passthroughHeaders") Map<String, String> headers) {

        UUID environmentId = UUID.randomUUID();

        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
                "TestService", environmentId, headerZoneId, "ctx-1", body, headers);

        // The envelope's zoneId MUST come from the SIF header parameter, not the body
        assertThat(envelope.getZoneId()).isEqualTo(headerZoneId);
        assertThat(envelope.getZoneId()).isNotEqualTo(body.get("zoneId"));
    }

    /**
     * The MessageBuilder SHALL use the contextId parameter (from SIF header) as the
     * envelope's contextId, even when the request body contains a "contextId" field
     * with a different value.
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 4: Routing parameters extracted from headers and URL, not body")
    void contextIdComesFromHeaderNotBody(
            @ForAll("contextIds") String headerContextId,
            @ForAll("bodiesWithRoutingFields") Map<String, Object> body,
            @ForAll("passthroughHeaders") Map<String, String> headers) {

        UUID environmentId = UUID.randomUUID();

        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
                "TestService", environmentId, "zone-1", headerContextId, body, headers);

        // The envelope's contextId MUST come from the SIF header parameter, not the body
        assertThat(envelope.getContextId()).isEqualTo(headerContextId);
        assertThat(envelope.getContextId()).isNotEqualTo(body.get("contextId"));
    }

    /**
     * For DELAYED requests, the MessageBuilder SHALL also use URL/header parameters
     * for routing, not body fields. The queueId comes from the SIF header, and
     * serviceName/zoneId/contextId come from URL path and headers respectively.
     *
     * <p><strong>Validates: Requirements 3.3, 3.4, 3.5, 2.1</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 4: Routing parameters extracted from headers and URL, not body")
    void delayedRequestRoutingParametersFromUrlAndHeaders(
            @ForAll("functionalServiceNames") String urlServiceName,
            @ForAll("zoneIds") String headerZoneId,
            @ForAll("contextIds") String headerContextId,
            @ForAll("bodiesWithRoutingFields") Map<String, Object> body,
            @ForAll("passthroughHeaders") Map<String, String> headers) {

        UUID environmentId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                urlServiceName, environmentId, headerZoneId, headerContextId,
                queueId, (String) null, body, headers);

        // All routing parameters MUST come from URL/headers, not body
        assertThat(envelope.getServiceName()).isEqualTo(urlServiceName);
        assertThat(envelope.getZoneId()).isEqualTo(headerZoneId);
        assertThat(envelope.getContextId()).isEqualTo(headerContextId);
        assertThat(envelope.getQueueId()).isEqualTo(queueId);

        // Body routing fields must NOT leak into envelope routing
        assertThat(envelope.getServiceName()).isNotEqualTo(body.get("serviceName"));
        assertThat(envelope.getZoneId()).isNotEqualTo(body.get("zoneId"));
        assertThat(envelope.getContextId()).isNotEqualTo(body.get("contextId"));

        // Envelope must still be properly typed
        assertThat(envelope.getServiceType()).isEqualTo(ServiceType.FUNCTIONAL);
        assertThat(envelope.getMessageType()).isEqualTo(MessageType.REQUEST);
    }

    /**
     * The body payload SHALL be preserved unchanged in the envelope even when it
     * contains routing-like fields. The MessageBuilder must not strip or modify
     * any body fields — it only reads routing info from its method parameters.
     *
     * <p><strong>Validates: Requirements 3.3, 3.4, 3.5</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 4: Routing parameters extracted from headers and URL, not body")
    void bodyWithRoutingFieldsIsPreservedUnchanged(
            @ForAll("functionalServiceNames") String urlServiceName,
            @ForAll("bodiesWithRoutingFields") Map<String, Object> body) {

        // Snapshot the body before building the envelope
        Map<String, Object> bodySnapshot = new LinkedHashMap<>(body);

        SifMessageEnvelope<Object> envelope = messageBuilder.buildRequestEnvelope(
                urlServiceName, UUID.randomUUID(), "zone-1", "ctx-1",
                body, Collections.emptyMap());

        // The payload must be the exact same object reference — no cloning or stripping
        assertThat(envelope.getPayload()).isSameAs(body);

        // The body's routing fields must still be present and unchanged
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadMap = (Map<String, Object>) envelope.getPayload();
        assertThat(payloadMap.get("serviceName")).isEqualTo(bodySnapshot.get("serviceName"));
        assertThat(payloadMap.get("zoneId")).isEqualTo(bodySnapshot.get("zoneId"));
        assertThat(payloadMap.get("contextId")).isEqualTo(bodySnapshot.get("contextId"));
    }
}
