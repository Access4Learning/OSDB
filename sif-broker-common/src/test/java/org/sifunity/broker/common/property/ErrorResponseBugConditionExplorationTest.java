// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition Exploration Test for Error Response SIF 3.7 Compliance.
 *
 * <p><strong>CRITICAL: This test MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>Bug: {@code SifErrorResponse} serializes as a flat JSON object without the required
 * {@code "error"} wrapper key. The {@code code} field is a String (internal error code) instead
 * of an integer HTTP status code. Fields {@code subCode} and {@code id} are missing entirely.</p>
 *
 * <p>Bug Condition: Error responses do not conform to SIF 3.7 {@code errorCommon} schema.</p>
 *
 * <p>On UNFIXED code: test FAILS because JSON is flat ({@code {"code":"VAL_001","message":"..."}}
 * instead of {@code {"error":{"id":"<uuid>","code":400,"subCode":"VAL_001","message":"..."}}}).</p>
 * <p>On FIXED code: test PASSES because error response wraps under "error" key with correct types.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4</strong></p>
 */
class ErrorResponseBugConditionExplorationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Property: SifErrorResponse.fromException() serialized output SHALL have a top-level "error"
     * key wrapping the error object, with code as integer HTTP status, subCode as the internal
     * error code string, and id as a UUID.
     *
     * <p>On UNFIXED code: serialization produces flat JSON without "error" wrapper — test FAILS.</p>
     * <p>On FIXED code: serialization produces wrapped JSON conforming to SIF 3.7 — test PASSES.</p>
     */
    @Property(tries = 20)
    @Label("Error response SHALL conform to SIF 3.7 errorCommon: wrapped under 'error' key with integer code, subCode, and UUID id")
    void errorResponseShallConformToSif37ErrorCommon(
            @ForAll("httpErrorStatuses") HttpStatus httpStatus,
            @ForAll("errorCodes") String errorCode,
            @ForAll("errorMessages") String message) throws Exception {

        // ARRANGE: Create exception and produce error response via envelope (actual code path)
        SifBrokerException exception = new SifBrokerException(errorCode, httpStatus, message);
        SifErrorResponseEnvelope envelope = SifErrorResponseEnvelope.fromException(exception);

        // ACT: Serialize to JSON
        String json = objectMapper.writeValueAsString(envelope);
        JsonNode root = objectMapper.readTree(json);

        // ASSERT 1: Top-level key SHALL be "error" (will fail - currently flat)
        assertThat(root.has("error"))
                .as("Response JSON shall have top-level 'error' key. Actual JSON: %s", json)
                .isTrue();

        JsonNode errorNode = root.get("error");

        // ASSERT 2: error.code SHALL be integer equal to HTTP status code (will fail - currently string)
        assertThat(errorNode.has("code"))
                .as("error object shall have 'code' field")
                .isTrue();
        assertThat(errorNode.get("code").isInt())
                .as("error.code shall be an integer, not a string. Actual: %s", errorNode.get("code"))
                .isTrue();
        assertThat(errorNode.get("code").intValue())
                .as("error.code shall equal HTTP status code %d", httpStatus.value())
                .isEqualTo(httpStatus.value());

        // ASSERT 3: error.subCode SHALL be present and equal internal error code (will fail - field missing)
        assertThat(errorNode.has("subCode"))
                .as("error object shall have 'subCode' field. Actual JSON: %s", json)
                .isTrue();
        assertThat(errorNode.get("subCode").asText())
                .as("error.subCode shall equal the internal error code string")
                .isEqualTo(errorCode);

        // ASSERT 4: error.id SHALL be present and match UUID pattern (will fail - field missing)
        assertThat(errorNode.has("id"))
                .as("error object shall have 'id' field (UUID). Actual JSON: %s", json)
                .isTrue();
        assertThat(errorNode.get("id").asText())
                .as("error.id shall be a valid UUID")
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

        // ASSERT 5: error.message SHALL be present and equal exception message
        assertThat(errorNode.has("message"))
                .as("error object shall have 'message' field")
                .isTrue();
        assertThat(errorNode.get("message").asText())
                .as("error.message shall equal exception message")
                .isEqualTo(message);

        // ASSERT 6: error.scope SHALL be present
        assertThat(errorNode.has("scope"))
                .as("error object shall have 'scope' field")
                .isTrue();
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<HttpStatus> httpErrorStatuses() {
        return Arbitraries.of(
                HttpStatus.BAD_REQUEST,           // 400
                HttpStatus.UNAUTHORIZED,          // 401
                HttpStatus.FORBIDDEN,             // 403
                HttpStatus.NOT_FOUND,             // 404
                HttpStatus.METHOD_NOT_ALLOWED,    // 405
                HttpStatus.CONFLICT,              // 409
                HttpStatus.UNPROCESSABLE_ENTITY,  // 422
                HttpStatus.TOO_MANY_REQUESTS,     // 429
                HttpStatus.INTERNAL_SERVER_ERROR,  // 500
                HttpStatus.BAD_GATEWAY,           // 502
                HttpStatus.SERVICE_UNAVAILABLE    // 503
        );
    }

    @Provide
    Arbitrary<String> errorCodes() {
        return Arbitraries.of(
                "VAL_001", "VAL_002", "AUTH_001", "AUTH_002",
                "ZONE_001", "ENV_001", "ENV_002", "QUEUE_001",
                "SUB_001", "ARG_001", "PERM_001", "RATE_001"
        );
    }

    @Provide
    Arbitrary<String> errorMessages() {
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
}
