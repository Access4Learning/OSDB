// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for validation exception classes.
 * Tests InvalidRequestException, MissingRequiredFieldException, and InvalidParameterException.
 */
class ValidationExceptionsTest {

    // InvalidRequestException Tests

    @Test
    void invalidRequestException_hasCorrectErrorCode() {
        InvalidRequestException exception = new InvalidRequestException("Invalid request body");

        assertThat(exception.getErrorCode()).isEqualTo("VAL_001");
    }

    @Test
    void invalidRequestException_hasBadRequestStatus() {
        InvalidRequestException exception = new InvalidRequestException("Malformed JSON");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void invalidRequestException_preservesMessage() {
        String message = "Request body cannot be parsed as valid JSON";
        InvalidRequestException exception = new InvalidRequestException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void invalidRequestException_extendsSifBrokerException() {
        InvalidRequestException exception = new InvalidRequestException("Test message");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void invalidRequestException_withSemanticError() {
        String message = "Request violates business rule: cannot create queue with negative retry count";
        InvalidRequestException exception = new InvalidRequestException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("VAL_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // MissingRequiredFieldException Tests

    @Test
    void missingRequiredFieldException_hasCorrectErrorCode() {
        MissingRequiredFieldException exception = new MissingRequiredFieldException("serviceName");

        assertThat(exception.getErrorCode()).isEqualTo("VAL_002");
    }

    @Test
    void missingRequiredFieldException_hasBadRequestStatus() {
        MissingRequiredFieldException exception = new MissingRequiredFieldException("zoneId");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingRequiredFieldException_formatsMessageWithFieldName() {
        String fieldName = "Authorization";
        MissingRequiredFieldException exception = new MissingRequiredFieldException(fieldName);

        assertThat(exception.getMessage()).isEqualTo("Required field missing: Authorization");
    }

    @Test
    void missingRequiredFieldException_extendsSifBrokerException() {
        MissingRequiredFieldException exception = new MissingRequiredFieldException("testField");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void missingRequiredFieldException_withHeaderField() {
        MissingRequiredFieldException exception = new MissingRequiredFieldException("X-Environment-Id");

        assertThat(exception.getMessage()).contains("X-Environment-Id");
        assertThat(exception.getErrorCode()).isEqualTo("VAL_002");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingRequiredFieldException_withBodyField() {
        MissingRequiredFieldException exception = new MissingRequiredFieldException("queueName");

        assertThat(exception.getMessage()).contains("queueName");
        assertThat(exception.getMessage()).startsWith("Required field missing:");
    }

    // InvalidParameterException Tests

    @Test
    void invalidParameterException_hasCorrectErrorCode() {
        InvalidParameterException exception = new InvalidParameterException("queueId", "Invalid UUID format");

        assertThat(exception.getErrorCode()).isEqualTo("VAL_003");
    }

    @Test
    void invalidParameterException_hasBadRequestStatus() {
        InvalidParameterException exception = new InvalidParameterException("maxRetries", "Must be positive");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void invalidParameterException_formatsMessageWithParameterAndReason() {
        String parameter = "serviceName";
        String reason = "Must start with letter and contain only alphanumeric, underscore, or hyphen";
        InvalidParameterException exception = new InvalidParameterException(parameter, reason);

        assertThat(exception.getMessage()).isEqualTo("Invalid parameter 'serviceName': Must start with letter and contain only alphanumeric, underscore, or hyphen");
    }

    @Test
    void invalidParameterException_extendsSifBrokerException() {
        InvalidParameterException exception = new InvalidParameterException("param", "reason");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void invalidParameterException_withUuidValidation() {
        InvalidParameterException exception = new InvalidParameterException("environmentId", "Invalid UUID format");

        assertThat(exception.getMessage()).contains("environmentId");
        assertThat(exception.getMessage()).contains("Invalid UUID format");
        assertThat(exception.getErrorCode()).isEqualTo("VAL_003");
    }

    @Test
    void invalidParameterException_withRangeValidation() {
        InvalidParameterException exception = new InvalidParameterException("maxRetries", "Must be between 1 and 10");

        assertThat(exception.getMessage()).contains("maxRetries");
        assertThat(exception.getMessage()).contains("Must be between 1 and 10");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void invalidParameterException_withEnumValidation() {
        InvalidParameterException exception = new InvalidParameterException("messageExchangePattern", "Must be one of: IMMEDIATE, DELAYED, EVENT");

        assertThat(exception.getMessage()).contains("messageExchangePattern");
        assertThat(exception.getMessage()).contains("IMMEDIATE, DELAYED, EVENT");
    }

    // Cross-Exception Tests

    @Test
    void allValidationExceptions_haveBadRequestStatus() {
        InvalidRequestException requestException = new InvalidRequestException("msg");
        MissingRequiredFieldException fieldException = new MissingRequiredFieldException("field");
        InvalidParameterException paramException = new InvalidParameterException("param", "reason");

        assertThat(requestException.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldException.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(paramException.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void allValidationExceptions_haveUniqueErrorCodes() {
        InvalidRequestException requestException = new InvalidRequestException("msg");
        MissingRequiredFieldException fieldException = new MissingRequiredFieldException("field");
        InvalidParameterException paramException = new InvalidParameterException("param", "reason");

        assertThat(requestException.getErrorCode()).isEqualTo("VAL_001");
        assertThat(fieldException.getErrorCode()).isEqualTo("VAL_002");
        assertThat(paramException.getErrorCode()).isEqualTo("VAL_003");

        // Verify all codes are unique
        assertThat(requestException.getErrorCode()).isNotEqualTo(fieldException.getErrorCode());
        assertThat(requestException.getErrorCode()).isNotEqualTo(paramException.getErrorCode());
        assertThat(fieldException.getErrorCode()).isNotEqualTo(paramException.getErrorCode());
    }

    @Test
    void allValidationExceptions_extendSifBrokerException() {
        InvalidRequestException requestException = new InvalidRequestException("msg");
        MissingRequiredFieldException fieldException = new MissingRequiredFieldException("field");
        InvalidParameterException paramException = new InvalidParameterException("param", "reason");

        assertThat(requestException).isInstanceOf(SifBrokerException.class);
        assertThat(fieldException).isInstanceOf(SifBrokerException.class);
        assertThat(paramException).isInstanceOf(SifBrokerException.class);
    }

    @Test
    void allValidationExceptions_areRuntimeExceptions() {
        InvalidRequestException requestException = new InvalidRequestException("msg");
        MissingRequiredFieldException fieldException = new MissingRequiredFieldException("field");
        InvalidParameterException paramException = new InvalidParameterException("param", "reason");

        assertThat(requestException).isInstanceOf(RuntimeException.class);
        assertThat(fieldException).isInstanceOf(RuntimeException.class);
        assertThat(paramException).isInstanceOf(RuntimeException.class);
    }

    @Test
    void validationExceptions_canBeDistinguishedByErrorCode() {
        // Simulate catching a generic SifBrokerException and identifying the specific type
        SifBrokerException requestException = new InvalidRequestException("Invalid request");
        SifBrokerException fieldException = new MissingRequiredFieldException("field");
        SifBrokerException paramException = new InvalidParameterException("param", "reason");

        // Can identify exception type by error code
        assertThat(requestException.getErrorCode()).startsWith("VAL_");
        assertThat(fieldException.getErrorCode()).startsWith("VAL_");
        assertThat(paramException.getErrorCode()).startsWith("VAL_");

        // Can distinguish between specific validation errors
        if (requestException.getErrorCode().equals("VAL_001")) {
            assertThat(requestException).isInstanceOf(InvalidRequestException.class);
        }
        if (fieldException.getErrorCode().equals("VAL_002")) {
            assertThat(fieldException).isInstanceOf(MissingRequiredFieldException.class);
        }
        if (paramException.getErrorCode().equals("VAL_003")) {
            assertThat(paramException).isInstanceOf(InvalidParameterException.class);
        }
    }

    @Test
    void validationExceptions_supportDetailedMessages() {
        String detailedRequestMessage = "Request body contains invalid JSON: unexpected token at line 5, column 12";
        String detailedFieldMessage = "serviceName";
        String detailedParamMessage = "Must match pattern ^[a-zA-Z][a-zA-Z0-9_-]*$";

        InvalidRequestException requestException = new InvalidRequestException(detailedRequestMessage);
        MissingRequiredFieldException fieldException = new MissingRequiredFieldException(detailedFieldMessage);
        InvalidParameterException paramException = new InvalidParameterException("serviceName", detailedParamMessage);

        assertThat(requestException.getMessage()).contains("line 5, column 12");
        assertThat(fieldException.getMessage()).contains("serviceName");
        assertThat(paramException.getMessage()).contains("pattern");
    }

    @Test
    void validationExceptions_provideContextForDebugging() {
        // Test that exceptions provide enough context to understand what went wrong
        InvalidRequestException requestException = new InvalidRequestException(
            "Request violates constraint: queue name must be unique within environment"
        );
        MissingRequiredFieldException fieldException = new MissingRequiredFieldException("Authorization");
        InvalidParameterException paramException = new InvalidParameterException(
            "zoneId", 
            "Must start with alphanumeric and contain only alphanumeric, underscore, or hyphen"
        );

        // Request exception provides business rule context
        assertThat(requestException.getMessage()).contains("unique within environment");

        // Field exception identifies the missing field
        assertThat(fieldException.getMessage()).contains("Authorization");

        // Parameter exception provides validation rule
        assertThat(paramException.getMessage()).contains("alphanumeric");
        assertThat(paramException.getMessage()).contains("zoneId");
    }

    @Test
    void validationExceptions_distinguishBetweenMissingAndInvalid() {
        // Missing field: field is not present at all
        MissingRequiredFieldException missingException = new MissingRequiredFieldException("serviceName");

        // Invalid parameter: field is present but has wrong value
        InvalidParameterException invalidException = new InvalidParameterException("serviceName", "Invalid format");

        assertThat(missingException.getMessage()).contains("missing");
        assertThat(invalidException.getMessage()).contains("Invalid");

        // Different error codes help distinguish the cases
        assertThat(missingException.getErrorCode()).isEqualTo("VAL_002");
        assertThat(invalidException.getErrorCode()).isEqualTo("VAL_003");
    }
}
