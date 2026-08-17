// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation Property Tests for Error Response Serialization.
 *
 * <p>These tests capture baseline behavior that MUST be preserved when implementing
 * the SIF 3.7 error envelope fix. They verify that field values are correctly carried
 * through serialization and that the current shape is as expected on UNFIXED code.</p>
 *
 * <p><strong>IMPORTANT:</strong> These tests run on UNFIXED code BEFORE implementing the fix.
 * They should PASS now, confirming baseline behavior to preserve. After the fix, they must
 * STILL PASS (confirming no regressions in field value preservation).</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.5, 3.6</strong></p>
 */
class ErrorResponsePreservationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Property 2.1: For all valid SifErrorResponse instances, JSON serialization produces
     * no "error" top-level key on UNFIXED code.
     *
     * <p>This confirms the current flat serialization shape. After the fix adds the envelope
     * wrapper, this test will be updated to verify field values inside the envelope instead.</p>
     *
     * <p>On UNFIXED code: SifErrorResponse serializes as a flat object — no "error" key present.</p>
     *
     * <p><strong>Validates: Requirement 3.1</strong></p>
     */
    @Property(tries = 20)
    @Label("SifErrorResponse serialization produces no 'error' top-level key (inner object is flat)")
    void successfulResponseDtoSerializationHasNoErrorWrapper(
            @ForAll("errorCodes") String subCode,
            @ForAll("messages") String message,
            @ForAll("scopes") String scope) throws Exception {

        // ARRANGE: Create a SifErrorResponse (inner object — should NOT have "error" wrapper by itself)
        SifErrorResponse response = new SifErrorResponse(
                UUID.randomUUID().toString(), 400, subCode, message, null, scope, Instant.now(), null, null);

        // ACT: Serialize the INNER object directly (not wrapped)
        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        // ASSERT: The inner SifErrorResponse itself does not have an "error" wrapper
        assertThat(root.has("error"))
                .as("SifErrorResponse (inner) serializes flat (no 'error' wrapper). JSON: %s", json)
                .isFalse();
    }

    /**
     * Property 2.2: For all SifErrorResponse instances, message field value equals input
     * message and scope field equals input scope (field value preservation).
     *
     * <p>This verifies that the core field values survive serialization/deserialization
     * correctly. After the fix restructures the JSON, these values must still be correct.</p>
     *
     * <p><strong>Validates: Requirements 3.2, 3.3</strong></p>
     */
    @Property(tries = 20)
    @Label("SifErrorResponse preserves message and scope field values through serialization")
    void messageAndScopeFieldValuesPreservedThroughSerialization(
            @ForAll("errorCodes") String subCode,
            @ForAll("messages") String message,
            @ForAll("scopes") String scope) throws Exception {

        // ARRANGE: Create SifErrorResponse with known values
        SifErrorResponse response = new SifErrorResponse(
                UUID.randomUUID().toString(), 400, subCode, message, null, scope, Instant.now(), null, null);

        // ACT: Serialize to JSON and parse back
        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        // ASSERT: message field carries the expected value
        assertThat(root.has("message"))
                .as("Serialized JSON shall include 'message' field")
                .isTrue();
        assertThat(root.get("message").asText())
                .as("message field value shall equal input message")
                .isEqualTo(message);

        // ASSERT: scope field carries the expected value
        assertThat(root.has("scope"))
                .as("Serialized JSON shall include 'scope' field")
                .isTrue();
        assertThat(root.get("scope").asText())
                .as("scope field value shall equal input scope")
                .isEqualTo(scope);
    }

    /**
     * Property 2.3: For all SifErrorResponse instances with non-null description,
     * serialized JSON includes description with correct value.
     *
     * <p>The description field is optional (@JsonInclude NON_NULL). When present, it must
     * carry the correct value through serialization.</p>
     *
     * <p><strong>Validates: Requirement 3.5</strong></p>
     */
    @Property(tries = 20)
    @Label("SifErrorResponse preserves non-null description field value through serialization")
    void descriptionFieldPreservedWhenNonNull(
            @ForAll("errorCodes") String subCode,
            @ForAll("messages") String message,
            @ForAll("descriptions") String description) throws Exception {

        // ARRANGE: Create SifErrorResponse with non-null description
        SifErrorResponse response = new SifErrorResponse(
                UUID.randomUUID().toString(), 400, subCode, message, description, "BROKER", Instant.now(), null, null);

        // ACT: Serialize to JSON and parse back
        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        // ASSERT: description field is present and carries expected value
        assertThat(root.has("description"))
                .as("Serialized JSON shall include 'description' field when non-null")
                .isTrue();
        assertThat(root.get("description").asText())
                .as("description field value shall equal input description")
                .isEqualTo(description);
    }

    /**
     * Property 2.4: For all SifErrorResponse instances, timestamp field is NOT present
     * in serialized output (not part of SIF 3.7 errorCommon schema).
     *
     * <p>The timestamp is retained internally for logging/diagnostics but suppressed
     * from the wire format via {@code @JsonIgnore}.</p>
     *
     * <p><strong>Validates: SIF 3.7 errorCommon conformance</strong></p>
     */
    @Property(tries = 20)
    @Label("SifErrorResponse excludes timestamp field from serialized output")
    void timestampFieldAbsentFromSerializedOutput(
            @ForAll("errorCodes") String subCode,
            @ForAll("messages") String message,
            @ForAll("scopes") String scope) throws Exception {

        // ARRANGE: Create SifErrorResponse with non-null timestamp
        Instant timestamp = Instant.now();
        SifErrorResponse response = new SifErrorResponse(
                UUID.randomUUID().toString(), 400, subCode, message, null, scope, timestamp, null, null);

        // ACT: Serialize to JSON and parse back
        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        // ASSERT: timestamp field is NOT present in serialized output
        assertThat(root.has("timestamp"))
                .as("Serialized JSON shall NOT include 'timestamp' field (not in SIF 3.7 errorCommon)")
                .isFalse();

        // ASSERT: timestamp is still accessible on the record internally
        assertThat(response.timestamp())
                .as("timestamp is still available internally on the record")
                .isEqualTo(timestamp);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> errorCodes() {
        return Arbitraries.of(
                "VAL_001", "VAL_002", "AUTH_001", "AUTH_002",
                "ZONE_001", "ENV_001", "ENV_002", "QUEUE_001",
                "SUB_001", "ARG_001", "PERM_001", "RATE_001"
        );
    }

    @Provide
    Arbitrary<String> messages() {
        return Arbitraries.of(
                "Validation failed",
                "Invalid request format",
                "Zone not found",
                "Environment not found",
                "Unauthorized access",
                "Rate limit exceeded",
                "Duplicate resource",
                "Invalid credentials",
                "Permission denied",
                "Internal server error"
        );
    }

    @Provide
    Arbitrary<String> scopes() {
        return Arbitraries.of("BROKER", "PROVIDER", "CONSUMER");
    }

    @Provide
    Arbitrary<String> descriptions() {
        return Arbitraries.of(
                "The request body must be valid JSON",
                "Zone with the specified ID does not exist in this environment",
                "Environment registration requires a valid solution ID",
                "Rate limit of 100 requests per minute exceeded",
                "ACL check failed for service 'StudentPersonals' in zone 'DEFAULT'"
        );
    }
}
