// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.gateway.util.GatewayErrorResponseUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for {@link GatewayErrorResponseUtils}.
 *
 * <p>Validates correctness properties of the shared gateway error response utility:
 * metadata correctness, body serialization round-trip, correlation ID propagation,
 * and header preservation.</p>
 */
class GatewayErrorResponseUtilsPropertyTest {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<HttpStatus> httpStatuses() {
        return Arbitraries.of(
                HttpStatus.BAD_REQUEST,
                HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN,
                HttpStatus.NOT_FOUND,
                HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.INTERNAL_SERVER_ERROR,
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @Provide
    Arbitrary<String> errorCodes() {
        return Arbitraries.of(
                "AUTH_001", "AUTH_010", "AUTHZ_001", "RATE_001",
                "ACCESS_001", "MEMBERSHIP_001", "SVC_TYPE_001",
                "GATEWAY_001", "PROXY_003", "PARAM_CONFLICT"
        );
    }

    @Provide
    Arbitrary<String> messages() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(5)
                .ofMaxLength(80);
    }

    @Provide
    Arbitrary<String> descriptions() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(10)
                .ofMaxLength(120);
    }

    @Provide
    Arbitrary<String> correlationIds() {
        return Arbitraries.strings()
                .withCharRange('0', '9')
                .withCharRange('a', 'f')
                .withChars('-')
                .ofLength(36);
    }

    // ========== Property 1a: Explicit-param overload sets status and Content-Type ==========

    @Property(tries = 20)
    @Tag("Feature: gateway-error-response-utils, Property 1: Gateway response metadata correctness")
    void explicitParamOverload_setsStatusAndContentType(
            @ForAll("httpStatuses") HttpStatus status,
            @ForAll("errorCodes") String errorCode,
            @ForAll("messages") String message) {

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayErrorResponseUtils.writeErrorResponse(
                exchange, OBJECT_MAPPER, status, errorCode, message, null).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(status);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }

    // ========== Property 1b: Pre-built overload sets status and Content-Type ==========

    @Property(tries = 20)
    @Tag("Feature: gateway-error-response-utils, Property 1: Gateway response metadata correctness")
    void preBuiltOverload_setsStatusAndContentType(
            @ForAll("httpStatuses") HttpStatus status,
            @ForAll("errorCodes") String errorCode,
            @ForAll("messages") String message) {

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        SifErrorResponse errorResponse = new SifErrorResponse(
                UUID.randomUUID().toString(), status.value(), errorCode, message, null,
                "BROKER", Instant.now(), null, null);

        GatewayErrorResponseUtils.writeErrorResponse(
                exchange, OBJECT_MAPPER, status, errorResponse).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(status);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }

    // ========== Property 2: Explicit-param body serialization round-trip ==========

    @Property(tries = 20)
    @Tag("Feature: gateway-error-response-utils, Property 2: Gateway explicit-param body serialization round-trip")
    void explicitParamOverload_bodyDeserializesWithCorrectFields(
            @ForAll("httpStatuses") HttpStatus status,
            @ForAll("errorCodes") String errorCode,
            @ForAll("messages") String message,
            @ForAll("descriptions") String description) throws Exception {

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayErrorResponseUtils.writeErrorResponse(
                exchange, OBJECT_MAPPER, status, errorCode, message, description).block();

        byte[] body = exchange.getResponse().getBodyAsString()
                .map(s -> s.getBytes(StandardCharsets.UTF_8))
                .block();
        SifErrorResponse deserialized = OBJECT_MAPPER.readValue(body, SifErrorResponseEnvelope.class).error();

        assertThat(deserialized.code()).isEqualTo(status.value());
        assertThat(deserialized.subCode()).isEqualTo(errorCode);
        assertThat(deserialized.message()).isEqualTo(message);
        assertThat(deserialized.description()).isEqualTo(description);
        assertThat(deserialized.scope()).isEqualTo("BROKER");
        // timestamp is @JsonIgnore (internal, not part of SIF 3.7 errorCommon) — absent from the wire
        assertThat(deserialized.timestamp()).isNull();
    }

    // ========== Property 3: Pre-built body serialization round-trip ==========

    @Property(tries = 20)
    @Tag("Feature: gateway-error-response-utils, Property 3: Gateway pre-built body serialization round-trip")
    void preBuiltOverload_bodyDeserializesEquivalently(
            @ForAll("httpStatuses") HttpStatus status,
            @ForAll("errorCodes") String errorCode,
            @ForAll("messages") String message,
            @ForAll("descriptions") String description) throws Exception {

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        SifErrorResponse original = new SifErrorResponse(
                UUID.randomUUID().toString(), status.value(), errorCode, message, description,
                "BROKER", Instant.now(), "testField", null);

        GatewayErrorResponseUtils.writeErrorResponse(
                exchange, OBJECT_MAPPER, status, original).block();

        byte[] body = exchange.getResponse().getBodyAsString()
                .map(s -> s.getBytes(StandardCharsets.UTF_8))
                .block();
        SifErrorResponse deserialized = OBJECT_MAPPER.readValue(body, SifErrorResponseEnvelope.class).error();

        assertThat(deserialized.code()).isEqualTo(original.code());
        assertThat(deserialized.subCode()).isEqualTo(original.subCode());
        assertThat(deserialized.message()).isEqualTo(original.message());
        assertThat(deserialized.description()).isEqualTo(original.description());
        assertThat(deserialized.scope()).isEqualTo(original.scope());
        // 'field' is @JsonIgnore — not serialized (not part of SIF 3.7 errorCommon)
        assertThat(deserialized.field()).isNull();
    }

    // ========== Property 4a: Correlation ID propagated when present ==========

    @Property(tries = 20)
    @Tag("Feature: gateway-error-response-utils, Property 4: Correlation ID propagation and omission")
    void correlationId_propagatedWhenPresent(
            @ForAll("httpStatuses") HttpStatus status,
            @ForAll("errorCodes") String errorCode,
            @ForAll("messages") String message,
            @ForAll("correlationIds") String correlationId) {

        MockServerHttpRequest request = MockServerHttpRequest.get("/test")
                .header(SifHeaders.X_CORRELATION_ID, correlationId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayErrorResponseUtils.writeErrorResponse(
                exchange, OBJECT_MAPPER, status, errorCode, message, null).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
                .isEqualTo(correlationId);
    }

    // ========== Property 4b: Correlation ID omitted when absent/blank ==========

    @Property(tries = 20)
    @Tag("Feature: gateway-error-response-utils, Property 4: Correlation ID propagation and omission")
    void correlationId_omittedWhenAbsent(
            @ForAll("httpStatuses") HttpStatus status,
            @ForAll("errorCodes") String errorCode,
            @ForAll("messages") String message) {

        // No correlation ID header on request
        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayErrorResponseUtils.writeErrorResponse(
                exchange, OBJECT_MAPPER, status, errorCode, message, null).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
                .isNull();
    }

    // ========== Property 5: Pre-existing headers preserved ==========

    @Property(tries = 20)
    @Tag("Feature: gateway-error-response-utils, Property 5: Header preservation (non-destructive writes)")
    void preExistingHeaders_preservedAfterUtilityCall(
            @ForAll("httpStatuses") HttpStatus status,
            @ForAll("errorCodes") String errorCode,
            @ForAll("messages") String message) {

        MockServerHttpRequest request = MockServerHttpRequest.get("/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set pre-existing headers on the response BEFORE calling utility
        exchange.getResponse().getHeaders().set("Retry-After", "60");
        exchange.getResponse().getHeaders().set("X-Custom-Header", "test-value");

        GatewayErrorResponseUtils.writeErrorResponse(
                exchange, OBJECT_MAPPER, status, errorCode, message, null).block();

        // Pre-existing headers should still be present
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Custom-Header")).isEqualTo("test-value");
    }
}
