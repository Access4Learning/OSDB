// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.common.exception.*;
import org.springframework.http.HttpStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for SifErrorResponse.
 * Tests universal properties like exception to error response conversion.
 */
class SifErrorResponseProperties {

    private static final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<String> errorMessages() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars(' ', '.', ',', '-', '_')
            .ofMinLength(1)
            .ofMaxLength(200);
    }

    @Provide
    Arbitrary<String> errorCodes() {
        return Arbitraries.of(
            "AUTH_001", "AUTH_002", "AUTH_003",
            "AUTHZ_001", "AUTHZ_002",
            "ROUTE_001", "ROUTE_002", "ROUTE_003",
            "VAL_001", "VAL_002", "VAL_003",
            "STORAGE_001", "STORAGE_002",
            "SERVICE_001", "SERVICE_002"
        );
    }

    @Provide
    Arbitrary<HttpStatus> httpStatuses() {
        return Arbitraries.of(
            HttpStatus.UNAUTHORIZED,
            HttpStatus.FORBIDDEN,
            HttpStatus.NOT_FOUND,
            HttpStatus.BAD_REQUEST,
            HttpStatus.INTERNAL_SERVER_ERROR,
            HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @Provide
    Arbitrary<SifBrokerException> authenticationExceptions() {
        return Combinators.combine(
            Arbitraries.of(
                "InvalidTokenException",
                "InvalidTimestampException",
                "MalformedAuthHeaderException"
            ),
            errorMessages()
        ).as((type, message) -> switch (type) {
            case "InvalidTokenException" -> new InvalidTokenException(message);
            case "InvalidTimestampException" -> new InvalidTimestampException(message);
            case "MalformedAuthHeaderException" -> new MalformedAuthHeaderException(message);
            default -> throw new IllegalStateException("Unexpected type: " + type);
        });
    }

    @Provide
    Arbitrary<SifBrokerException> authorizationExceptions() {
        return Combinators.combine(
            Arbitraries.of(
                "InsufficientPermissionsException",
                "ZoneAccessDeniedException"
            ),
            errorMessages()
        ).as((type, message) -> switch (type) {
            case "InsufficientPermissionsException" -> new InsufficientPermissionsException(message);
            case "ZoneAccessDeniedException" -> new ZoneAccessDeniedException(message);
            default -> throw new IllegalStateException("Unexpected type: " + type);
        });
    }

    @Provide
    Arbitrary<SifBrokerException> routingExceptions() {
        return Combinators.combine(
            Arbitraries.of(
                "ProviderNotFoundException",
                "QueueNotFoundException",
                "SubscriptionNotFoundException"
            ),
            errorMessages()
        ).as((type, message) -> switch (type) {
            case "ProviderNotFoundException" -> new ProviderNotFoundException(message);
            case "QueueNotFoundException" -> new QueueNotFoundException(message);
            case "SubscriptionNotFoundException" -> new SubscriptionNotFoundException(message);
            default -> throw new IllegalStateException("Unexpected type: " + type);
        });
    }

    @Provide
    Arbitrary<SifBrokerException> validationExceptions() {
        return Combinators.combine(
            Arbitraries.of(
                "InvalidRequestException",
                "MissingRequiredFieldException",
                "InvalidParameterException"
            ),
            errorMessages()
        ).as((type, message) -> switch (type) {
            case "InvalidRequestException" -> new InvalidRequestException(message);
            case "MissingRequiredFieldException" -> new MissingRequiredFieldException(message);
            case "InvalidParameterException" -> new InvalidParameterException("param", message);
            default -> throw new IllegalStateException("Unexpected type: " + type);
        });
    }

    @Provide
    Arbitrary<SifBrokerException> storageExceptions() {
        return Combinators.combine(
            Arbitraries.of(
                "StorageException",
                "StorageUnavailableException"
            ),
            errorMessages()
        ).as((type, message) -> switch (type) {
            case "StorageException" -> new StorageException(message, null);
            case "StorageUnavailableException" -> new StorageUnavailableException(message);
            default -> throw new IllegalStateException("Unexpected type: " + type);
        });
    }

    @Provide
    Arbitrary<SifBrokerException> serviceExceptions() {
        return Combinators.combine(
            Arbitraries.of(
                "ProviderUnavailableException",
                "ServiceUnavailableException"
            ),
            errorMessages()
        ).as((type, message) -> switch (type) {
            case "ProviderUnavailableException" -> new ProviderUnavailableException(message);
            case "ServiceUnavailableException" -> new ServiceUnavailableException("TestService", message);
            default -> throw new IllegalStateException("Unexpected type: " + type);
        });
    }

    @Provide
    Arbitrary<SifBrokerException> allExceptions() {
        return Arbitraries.oneOf(
            authenticationExceptions(),
            authorizationExceptions(),
            routingExceptions(),
            validationExceptions(),
            storageExceptions(),
            serviceExceptions()
        );
    }

    @Provide
    Arbitrary<SifBrokerException> genericExceptions() {
        return Combinators.combine(
            errorCodes(),
            httpStatuses(),
            errorMessages()
        ).as(SifBrokerException::new);
    }

    // ========== Property 5: Exception to Error Response Conversion ==========
    // **Validates: Requirements 4.5, 4.6**

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_producesValidErrorResponse(@ForAll("allExceptions") SifBrokerException exception) {
        // For any SifBrokerException, fromException should produce a valid error response
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.subCode()).isNotNull();
        assertThat(response.message()).isNotNull();
        assertThat(response.scope()).isNotNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_preservesErrorCode(@ForAll("allExceptions") SifBrokerException exception) {
        // The error code from the exception should be preserved in the response
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo(exception.getErrorCode());
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_preservesMessage(@ForAll("allExceptions") SifBrokerException exception) {
        // The message from the exception should be preserved in the response
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.message()).isEqualTo(exception.getMessage());
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_setsScope(@ForAll("allExceptions") SifBrokerException exception) {
        // The scope should always be set to "BROKER"
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.scope()).isEqualTo("BROKER");
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_setsTimestamp(@ForAll("allExceptions") SifBrokerException exception) {
        // The timestamp should be set to a recent time
        Instant before = Instant.now();
        SifErrorResponse response = SifErrorResponse.fromException(exception);
        Instant after = Instant.now();

        assertThat(response.timestamp()).isBetween(before, after);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_authenticationExceptions(@ForAll("authenticationExceptions") SifBrokerException exception) {
        // Authentication exceptions should produce error responses with AUTH_xxx codes
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).startsWith("AUTH_");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_authorizationExceptions(@ForAll("authorizationExceptions") SifBrokerException exception) {
        // Authorization exceptions should produce error responses with AUTHZ_xxx codes
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).startsWith("AUTHZ_");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_routingExceptions(@ForAll("routingExceptions") SifBrokerException exception) {
        // Routing exceptions should produce error responses with ROUTE_xxx codes
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).startsWith("ROUTE_");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_validationExceptions(@ForAll("validationExceptions") SifBrokerException exception) {
        // Validation exceptions should produce error responses with VAL_xxx codes
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).startsWith("VAL_");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_storageExceptions(@ForAll("storageExceptions") SifBrokerException exception) {
        // Storage exceptions should produce error responses with STORAGE_xxx codes
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).startsWith("STORAGE_");
        assertThat(exception.getHttpStatus()).isIn(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_serviceExceptions(@ForAll("serviceExceptions") SifBrokerException exception) {
        // Service exceptions should produce error responses with SERVICE_xxx codes
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).startsWith("SERVICE_");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_isIdempotent(@ForAll("allExceptions") SifBrokerException exception) {
        // Converting the same exception multiple times should produce equivalent responses
        // (except for timestamp which will be different)
        SifErrorResponse response1 = SifErrorResponse.fromException(exception);
        SifErrorResponse response2 = SifErrorResponse.fromException(exception);

        assertThat(response1.code()).isEqualTo(response2.code());
        assertThat(response1.message()).isEqualTo(response2.message());
        assertThat(response1.scope()).isEqualTo(response2.scope());
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_resultIsSerializable(@ForAll("allExceptions") SifBrokerException exception) throws Exception {
        // The error response produced by fromException should be serializable to JSON
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        String json = objectMapper.writeValueAsString(response);
        
        assertThat(json).isNotNull();
        assertThat(json).contains("\"code\"");
        assertThat(json).contains("\"message\"");
        assertThat(json).contains("\"scope\"");
        // timestamp is @JsonIgnore — not serialized to JSON
        assertThat(json).doesNotContain("\"timestamp\"");
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_resultIsDeserializable(@ForAll("allExceptions") SifBrokerException exception) throws Exception {
        // The error response should be deserializable from JSON
        SifErrorResponse original = SifErrorResponse.fromException(exception);
        String json = objectMapper.writeValueAsString(original);
        SifErrorResponse deserialized = objectMapper.readValue(json, SifErrorResponse.class);

        assertThat(deserialized.code()).isEqualTo(original.code());
        assertThat(deserialized.message()).isEqualTo(original.message());
        assertThat(deserialized.scope()).isEqualTo(original.scope());
        // timestamp is @JsonIgnore — not preserved across a round-trip
        assertThat(deserialized.timestamp()).isNull();
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_genericExceptions(@ForAll("genericExceptions") SifBrokerException exception) {
        // Generic SifBrokerException instances should also convert correctly
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.subCode()).isEqualTo(exception.getErrorCode());
        assertThat(response.message()).isEqualTo(exception.getMessage());
        assertThat(response.scope()).isEqualTo("BROKER");
        assertThat(response.timestamp()).isNotNull();
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_doesNotIncludeOptionalFieldsByDefault(@ForAll("allExceptions") SifBrokerException exception) {
        // By default, fromException should not populate optional fields
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.description()).isNull();
        assertThat(response.field()).isNull();
        assertThat(response.stackTrace()).isNull();
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_handlesLongMessages(@ForAll("errorCodes") String errorCode,
                                           @ForAll("httpStatuses") HttpStatus httpStatus) {
        // fromException should handle exceptions with very long messages
        String longMessage = "A".repeat(1000);
        SifBrokerException exception = new SifBrokerException(errorCode, httpStatus, longMessage);
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.message()).isEqualTo(longMessage);
        assertThat(response.message()).hasSize(1000);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_handlesSpecialCharacters(@ForAll("errorCodes") String errorCode,
                                                @ForAll("httpStatuses") HttpStatus httpStatus) {
        // fromException should handle messages with special characters
        String messageWithSpecialChars = "Error: \"quoted\", 'apostrophe', <tag>, & ampersand, \n newline";
        SifBrokerException exception = new SifBrokerException(errorCode, httpStatus, messageWithSpecialChars);
        
        SifErrorResponse response = SifErrorResponse.fromException(exception);

        assertThat(response.message()).isEqualTo(messageWithSpecialChars);
    }

    @Property
    @Tag("Feature-sif-broker-common-Property-5-Exception-to-Error-Response-Conversion")
    void fromException_jsonSerializationMatchesSif37Format(@ForAll("allExceptions") SifBrokerException exception) throws Exception {
        // The JSON output should match SIF 3.7 format (camelCase, ISO-8601 timestamps)
        SifErrorResponse response = SifErrorResponse.fromException(exception);
        String json = objectMapper.writeValueAsString(response);

        // Verify camelCase naming
        assertThat(json).contains("\"code\"");
        assertThat(json).contains("\"message\"");
        assertThat(json).contains("\"scope\"");
        // timestamp is @JsonIgnore — not part of SIF 3.7 wire format
        assertThat(json).doesNotContain("\"timestamp\"");
    }
}

