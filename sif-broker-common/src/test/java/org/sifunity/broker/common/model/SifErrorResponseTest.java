// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.exception.*;
import org.springframework.http.HttpStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SifErrorResponse.
 * Tests error response creation, fromException() conversion, and JSON serialization.
 */
class SifErrorResponseTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ========== Unit Tests for Error Response Creation ==========

    @Test
    void testErrorResponseCreation_withAllRequiredFields() {
        SifErrorResponse response = new SifErrorResponse(
            "uuid-123", 400, "TEST_001",
            "Test error message",
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("uuid-123");
        assertThat(response.code()).isEqualTo(400);
        assertThat(response.subCode()).isEqualTo("TEST_001");
        assertThat(response.message()).isEqualTo("Test error message");
        assertThat(response.scope()).isEqualTo("BROKER");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void testErrorResponseCreation_withOptionalFields() {
        Instant timestamp = Instant.parse("2024-01-01T00:00:00Z");
        
        SifErrorResponse response = new SifErrorResponse(
            "uuid-456", 400, "VAL_002",
            "Required field missing",
            "The 'email' field is required but was not provided",
            "BROKER",
            timestamp,
            "email",
            "java.lang.NullPointerException at..."
        );

        assertThat(response.id()).isEqualTo("uuid-456");
        assertThat(response.code()).isEqualTo(400);
        assertThat(response.subCode()).isEqualTo("VAL_002");
        assertThat(response.message()).isEqualTo("Required field missing");
        assertThat(response.description()).isEqualTo("The 'email' field is required but was not provided");
        assertThat(response.scope()).isEqualTo("BROKER");
        assertThat(response.timestamp()).isEqualTo(timestamp);
        assertThat(response.field()).isEqualTo("email");
        assertThat(response.stackTrace()).isNotNull();
    }

    @Test
    void testErrorResponseCreation_withNullOptionalFields() {
        SifErrorResponse response = new SifErrorResponse(
            "uuid-789", 500, "TEST_002",
            "Test error",
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );

        assertThat(response.description()).isNull();
        assertThat(response.field()).isNull();
        assertThat(response.stackTrace()).isNull();
    }

    // ========== Unit Tests for fromException() Conversion ==========

    @Test
    void testFromException_withInvalidTokenException() {
        InvalidTokenException exception = new InvalidTokenException("Invalid session token");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("AUTH_001");
        assertThat(response.code()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.message()).isEqualTo("Invalid session token");
        assertThat(response.scope()).isEqualTo("BROKER");
        assertThat(response.timestamp()).isNotNull();
        assertThat(response.id()).isNotBlank();
    }

    @Test
    void testFromException_withInvalidTimestampException() {
        InvalidTimestampException exception = new InvalidTimestampException("Timestamp is outside tolerance window");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("AUTH_002");
        assertThat(response.message()).isEqualTo("Timestamp is outside tolerance window");
        assertThat(response.scope()).isEqualTo("BROKER");
    }

    @Test
    void testFromException_withMalformedAuthHeaderException() {
        MalformedAuthHeaderException exception = new MalformedAuthHeaderException("Invalid authorization header format");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("AUTH_003");
        assertThat(response.message()).isEqualTo("Invalid authorization header format");
    }

    @Test
    void testFromException_withInsufficientPermissionsException() {
        InsufficientPermissionsException exception = new InsufficientPermissionsException("User lacks required permissions");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("AUTHZ_001");
        assertThat(response.message()).isEqualTo("User lacks required permissions");
        assertThat(response.scope()).isEqualTo("BROKER");
    }

    @Test
    void testFromException_withZoneAccessDeniedException() {
        ZoneAccessDeniedException exception = new ZoneAccessDeniedException("Access to zone 'zone1' denied");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("AUTHZ_002");
        assertThat(response.message()).isEqualTo("Access to zone 'zone1' denied");
    }

    @Test
    void testFromException_withProviderNotFoundException() {
        ProviderNotFoundException exception = new ProviderNotFoundException("Provider for service 'StudentPersonal' not found");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("ROUTE_001");
        assertThat(response.message()).isEqualTo("Provider for service 'StudentPersonal' not found");
    }

    @Test
    void testFromException_withQueueNotFoundException() {
        QueueNotFoundException exception = new QueueNotFoundException("Queue with ID 'queue-123' not found");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("ROUTE_002");
        assertThat(response.message()).isEqualTo("Queue with ID 'queue-123' not found");
    }

    @Test
    void testFromException_withSubscriptionNotFoundException() {
        SubscriptionNotFoundException exception = new SubscriptionNotFoundException("Subscription not found");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("ROUTE_003");
        assertThat(response.message()).isEqualTo("Subscription not found");
    }

    @Test
    void testFromException_withInvalidRequestException() {
        InvalidRequestException exception = new InvalidRequestException("Request body is malformed");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("VAL_001");
        assertThat(response.message()).isEqualTo("Request body is malformed");
    }

    @Test
    void testFromException_withMissingRequiredFieldException() {
        MissingRequiredFieldException exception = new MissingRequiredFieldException("email");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("VAL_002");
        assertThat(response.message()).isEqualTo("Required field missing: email");
    }

    @Test
    void testFromException_withInvalidParameterException() {
        InvalidParameterException exception = new InvalidParameterException("zoneId", "Invalid format");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("VAL_003");
        assertThat(response.message()).contains("zoneId");
        assertThat(response.message()).contains("Invalid format");
    }

    @Test
    void testFromException_withStorageException() {
        StorageException exception = new StorageException("Failed to write to storage", new RuntimeException("IO error"));
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("STORAGE_001");
        assertThat(response.message()).isEqualTo("Failed to write to storage");
    }

    @Test
    void testFromException_withStorageUnavailableException() {
        StorageUnavailableException exception = new StorageUnavailableException("Storage service is unavailable");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("STORAGE_002");
        assertThat(response.message()).isEqualTo("Storage service is unavailable");
    }

    @Test
    void testFromException_withProviderUnavailableException() {
        ProviderUnavailableException exception = new ProviderUnavailableException("Provider is temporarily unavailable");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("SERVICE_001");
        assertThat(response.message()).isEqualTo("Provider is temporarily unavailable");
    }

    @Test
    void testFromException_withServiceUnavailableException() {
        ServiceUnavailableException exception = new ServiceUnavailableException("QueueService", "Service is down for maintenance");
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo("SERVICE_002");
        assertThat(response.message()).contains("QueueService");
        assertThat(response.message()).contains("Service is down for maintenance");
    }

    @Test
    void testFromException_timestampIsRecent() {
        Instant before = Instant.now();
        
        SifBrokerException exception = new InvalidRequestException("Test error");
        SifErrorResponse response = SifErrorResponse.fromException(exception);
        
        Instant after = Instant.now();

        assertThat(response.timestamp()).isBetween(before, after);
    }

    @Test
    void testFromException_scopeIsAlwaysBroker() {
        SifBrokerException[] exceptions = {
            new InvalidTokenException("msg"),
            new InsufficientPermissionsException("msg"),
            new ProviderNotFoundException("msg"),
            new InvalidRequestException("msg"),
            new StorageException("msg", null),
            new ProviderUnavailableException("msg")
        };

        for (SifBrokerException exception : exceptions) {
            SifErrorResponse response = SifErrorResponse.fromException(exception);
            assertThat(response.scope()).isEqualTo("BROKER");
        }
    }

    // ========== Unit Tests for JSON Serialization ==========

    @Test
    void testJsonSerialization_withRequiredFields() throws Exception {
        SifErrorResponse response = new SifErrorResponse(
            "uuid-ser-1", 400, "TEST_001",
            "Test error message",
            null,
            "BROKER",
            Instant.parse("2024-01-01T00:00:00Z"),
            null,
            null
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"id\":\"uuid-ser-1\"");
        assertThat(json).contains("\"code\":400");
        assertThat(json).contains("\"subCode\":\"TEST_001\"");
        assertThat(json).contains("\"message\":\"Test error message\"");
        assertThat(json).contains("\"scope\":\"BROKER\"");
        // timestamp is @JsonIgnore — not serialized to JSON
        assertThat(json).doesNotContain("\"timestamp\"");
    }

    @Test
    void testJsonSerialization_withOptionalFields() throws Exception {
        SifErrorResponse response = new SifErrorResponse(
            "uuid-ser-2", 400, "VAL_002",
            "Required field missing",
            "The 'email' field is required",
            "BROKER",
            Instant.parse("2024-01-01T00:00:00Z"),
            "email",
            "java.lang.NullPointerException at..."
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"description\":\"The 'email' field is required\"");
        // 'field' is @JsonIgnore — internal detail, not part of the SIF 3.7 errorCommon schema
        assertThat(json).doesNotContain("\"field\"");
        assertThat(json).contains("\"stackTrace\":\"java.lang.NullPointerException at...\"");
    }

    @Test
    void testJsonSerialization_nullFieldsAreExcluded() throws Exception {
        SifErrorResponse response = new SifErrorResponse(
            "uuid-ser-3", 500, "TEST_002",
            "Test error",
            null,
            "BROKER",
            Instant.parse("2024-01-01T00:00:00Z"),
            null,
            null
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).doesNotContain("\"description\"");
        assertThat(json).doesNotContain("\"field\"");
        assertThat(json).doesNotContain("\"stackTrace\"");
        
        assertThat(json).contains("\"code\"");
        assertThat(json).contains("\"subCode\"");
        assertThat(json).contains("\"message\"");
        assertThat(json).contains("\"scope\"");
        // timestamp is @JsonIgnore — not serialized to JSON
        assertThat(json).doesNotContain("\"timestamp\"");
    }

    @Test
    void testJsonDeserialization_withRequiredFields() throws Exception {
        String json = """
            {
                "id": "uuid-deser-1",
                "code": 400,
                "subCode": "TEST_001",
                "message": "Test error message",
                "scope": "BROKER",
                "timestamp": "2024-01-01T00:00:00Z"
            }
            """;

        SifErrorResponse response = objectMapper.readValue(json, SifErrorResponse.class);

        assertThat(response.id()).isEqualTo("uuid-deser-1");
        assertThat(response.code()).isEqualTo(400);
        assertThat(response.subCode()).isEqualTo("TEST_001");
        assertThat(response.message()).isEqualTo("Test error message");
        assertThat(response.scope()).isEqualTo("BROKER");
        // timestamp is @JsonIgnore — not deserialized from JSON
        assertThat(response.timestamp()).isNull();
    }

    @Test
    void testJsonDeserialization_withOptionalFields() throws Exception {
        String json = """
            {
                "id": "uuid-deser-2",
                "code": 400,
                "subCode": "VAL_002",
                "message": "Required field missing",
                "description": "The 'email' field is required",
                "scope": "BROKER",
                "timestamp": "2024-01-01T00:00:00Z",
                "field": "email",
                "stackTrace": "java.lang.NullPointerException at..."
            }
            """;

        SifErrorResponse response = objectMapper.readValue(json, SifErrorResponse.class);

        assertThat(response.id()).isEqualTo("uuid-deser-2");
        assertThat(response.code()).isEqualTo(400);
        assertThat(response.subCode()).isEqualTo("VAL_002");
        assertThat(response.message()).isEqualTo("Required field missing");
        assertThat(response.description()).isEqualTo("The 'email' field is required");
        assertThat(response.scope()).isEqualTo("BROKER");
        // timestamp is @JsonIgnore — not deserialized from JSON
        assertThat(response.timestamp()).isNull();
        // 'field' is @JsonIgnore — not read from JSON (not part of SIF 3.7 errorCommon)
        assertThat(response.field()).isNull();
        assertThat(response.stackTrace()).isNotNull();
    }

    @Test
    void testJsonRoundTrip_preservesAllFields() throws Exception {
        SifErrorResponse original = new SifErrorResponse(
            "uuid-rt-1", 422, "TEST_003",
            "Test error message",
            "Detailed description",
            "BROKER",
            Instant.parse("2024-01-01T00:00:00Z"),
            "testField",
            "stack trace here"
        );

        String json = objectMapper.writeValueAsString(original);
        SifErrorResponse deserialized = objectMapper.readValue(json, SifErrorResponse.class);

        assertThat(deserialized.id()).isEqualTo(original.id());
        assertThat(deserialized.code()).isEqualTo(original.code());
        assertThat(deserialized.subCode()).isEqualTo(original.subCode());
        assertThat(deserialized.message()).isEqualTo(original.message());
        assertThat(deserialized.description()).isEqualTo(original.description());
        assertThat(deserialized.scope()).isEqualTo(original.scope());
        // timestamp is @JsonIgnore — not preserved across a round-trip
        assertThat(deserialized.timestamp()).isNull();
        // 'field' is @JsonIgnore — not serialized, so it is not preserved across a round-trip
        assertThat(deserialized.field()).isNull();
        assertThat(deserialized.stackTrace()).isEqualTo(original.stackTrace());
    }

    @Test
    void testJsonRoundTrip_withNullOptionalFields() throws Exception {
        SifErrorResponse original = new SifErrorResponse(
            "uuid-rt-2", 500, "TEST_004",
            "Test error",
            null,
            "BROKER",
            Instant.parse("2024-01-01T00:00:00Z"),
            null,
            null
        );

        String json = objectMapper.writeValueAsString(original);
        SifErrorResponse deserialized = objectMapper.readValue(json, SifErrorResponse.class);

        assertThat(deserialized.id()).isEqualTo(original.id());
        assertThat(deserialized.code()).isEqualTo(original.code());
        assertThat(deserialized.subCode()).isEqualTo(original.subCode());
        assertThat(deserialized.message()).isEqualTo(original.message());
        assertThat(deserialized.scope()).isEqualTo(original.scope());
        // timestamp is @JsonIgnore — not preserved across a round-trip
        assertThat(deserialized.timestamp()).isNull();
        assertThat(deserialized.description()).isNull();
        assertThat(deserialized.field()).isNull();
        assertThat(deserialized.stackTrace()).isNull();
    }

    @Test
    void testJsonFormat_matchesSif37Specification() throws Exception {
        SifErrorResponse response = new SifErrorResponse(
            "uuid-fmt-1", 401, "AUTH_001",
            "Invalid token",
            null,
            "BROKER",
            Instant.parse("2024-01-01T00:00:00Z"),
            null,
            null
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"id\"");
        assertThat(json).contains("\"code\"");
        assertThat(json).contains("\"subCode\"");
        assertThat(json).contains("\"message\"");
        assertThat(json).contains("\"scope\"");
        // timestamp is @JsonIgnore — not part of SIF 3.7 wire format
        assertThat(json).doesNotContain("\"timestamp\"");
    }

    @Test
    void testBuilder_allowsFluentConstruction() {
        SifErrorResponse response = new SifErrorResponse(
            "uuid-build-1", 400, "TEST_005",
            "Test message",
            "Test description",
            "BROKER",
            Instant.now(),
            "testField",
            "test stack trace"
        );

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("uuid-build-1");
        assertThat(response.code()).isEqualTo(400);
        assertThat(response.subCode()).isEqualTo("TEST_005");
        assertThat(response.message()).isEqualTo("Test message");
        assertThat(response.description()).isEqualTo("Test description");
        assertThat(response.field()).isEqualTo("testField");
        assertThat(response.stackTrace()).isEqualTo("test stack trace");
    }
}
