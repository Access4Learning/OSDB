// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.environment.util.ServletErrorResponseUtils;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for {@link ServletErrorResponseUtils}.
 *
 * <p>Validates correctness properties of the shared servlet error response utility:
 * metadata correctness and body serialization round-trip.</p>
 */
class ServletErrorResponseUtilsPropertyTest {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<Integer> httpStatusCodes() {
        return Arbitraries.of(400, 401, 403, 404, 429, 500, 503);
    }

    @Provide
    Arbitrary<String> errorCodes() {
        return Arbitraries.of(
                "AUTH_001", "AUTH_002", "AUTH_003", "AUTHZ_001"
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

    // ========== Property 6a: Explicit-param overload sets status, content-type, and body round-trips ==========

    @Property(tries = 20)
    @Tag("Feature: gateway-error-response-utils, Property 6: Servlet response metadata and body round-trip")
    void explicitParamOverload_setsMetadataAndBodyRoundTrips(
            @ForAll("httpStatusCodes") int statusCode,
            @ForAll("errorCodes") String errorCode,
            @ForAll("messages") String message,
            @ForAll("descriptions") String description) throws Exception {

        MockHttpServletResponse response = new MockHttpServletResponse();

        ServletErrorResponseUtils.writeErrorResponse(
                response, statusCode, errorCode, message, description);

        // Verify metadata
        assertThat(response.getStatus()).isEqualTo(statusCode);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);

        // Verify body round-trip (SIF 3.7 errorCommon: wrapped under "error", code is int HTTP
        // status, internal error code is in subCode)
        SifErrorResponse deserialized = OBJECT_MAPPER.readValue(
                response.getContentAsString(), SifErrorResponseEnvelope.class).error();
        assertThat(deserialized.code()).isEqualTo(statusCode);
        assertThat(deserialized.subCode()).isEqualTo(errorCode);
        assertThat(deserialized.message()).isEqualTo(message);
        assertThat(deserialized.description()).isEqualTo(description);
        assertThat(deserialized.scope()).isEqualTo("BROKER");
        // timestamp is @JsonIgnore (internal, not part of SIF 3.7 errorCommon) — absent from the wire
        assertThat(deserialized.timestamp()).isNull();
    }

    // ========== Property 6b: Pre-built overload sets metadata and body round-trips ==========

    @Property(tries = 20)
    @Tag("Feature: gateway-error-response-utils, Property 6: Servlet response metadata and body round-trip")
    void preBuiltOverload_setsMetadataAndBodyRoundTrips(
            @ForAll("httpStatusCodes") int statusCode,
            @ForAll("errorCodes") String errorCode,
            @ForAll("messages") String message,
            @ForAll("descriptions") String description) throws Exception {

        MockHttpServletResponse response = new MockHttpServletResponse();

        SifErrorResponse original = new SifErrorResponse(
                UUID.randomUUID().toString(), statusCode, errorCode, message, description,
                "CUSTOM_SCOPE", Instant.now(), "fieldName", null);

        ServletErrorResponseUtils.writeErrorResponse(response, statusCode, original);

        // Verify metadata
        assertThat(response.getStatus()).isEqualTo(statusCode);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);

        // Verify body round-trip preserves original values (unwrapping the "error" envelope)
        SifErrorResponse deserialized = OBJECT_MAPPER.readValue(
                response.getContentAsString(), SifErrorResponseEnvelope.class).error();
        assertThat(deserialized.code()).isEqualTo(original.code());
        assertThat(deserialized.subCode()).isEqualTo(original.subCode());
        assertThat(deserialized.message()).isEqualTo(original.message());
        assertThat(deserialized.description()).isEqualTo(original.description());
        assertThat(deserialized.scope()).isEqualTo(original.scope());
        // 'field' is @JsonIgnore — not serialized (not part of SIF 3.7 errorCommon)
        assertThat(deserialized.field()).isNull();
    }
}
