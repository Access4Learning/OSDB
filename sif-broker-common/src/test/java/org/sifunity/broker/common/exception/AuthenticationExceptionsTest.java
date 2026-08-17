// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for authentication exception classes.
 * Tests InvalidTokenException, InvalidTimestampException, and MalformedAuthHeaderException.
 */
class AuthenticationExceptionsTest {

    // InvalidTokenException Tests

    @Test
    void invalidTokenException_hasCorrectErrorCode() {
        InvalidTokenException exception = new InvalidTokenException("Invalid session token");

        assertThat(exception.getErrorCode()).isEqualTo("AUTH_001");
    }

    @Test
    void invalidTokenException_hasUnauthorizedStatus() {
        InvalidTokenException exception = new InvalidTokenException("Token expired");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidTokenException_preservesMessage() {
        String message = "Session token does not exist in the system";
        InvalidTokenException exception = new InvalidTokenException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void invalidTokenException_extendsSifBrokerException() {
        InvalidTokenException exception = new InvalidTokenException("Test message");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void invalidTokenException_withHmacValidationFailure() {
        String message = "HMAC signature validation failed";
        InvalidTokenException exception = new InvalidTokenException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("AUTH_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // InvalidTimestampException Tests

    @Test
    void invalidTimestampException_hasCorrectErrorCode() {
        InvalidTimestampException exception = new InvalidTimestampException("Timestamp too old");

        assertThat(exception.getErrorCode()).isEqualTo("AUTH_002");
    }

    @Test
    void invalidTimestampException_hasUnauthorizedStatus() {
        InvalidTimestampException exception = new InvalidTimestampException("Timestamp in future");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidTimestampException_preservesMessage() {
        String message = "Timestamp is outside tolerance window (5 minutes)";
        InvalidTimestampException exception = new InvalidTimestampException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void invalidTimestampException_extendsSifBrokerException() {
        InvalidTimestampException exception = new InvalidTimestampException("Test message");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void invalidTimestampException_withInvalidFormat() {
        String message = "Invalid timestamp format: not-a-date";
        InvalidTimestampException exception = new InvalidTimestampException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("AUTH_002");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidTimestampException_withToleranceWindow() {
        String message = "Timestamp is outside tolerance window (5 minutes)";
        InvalidTimestampException exception = new InvalidTimestampException(message);

        assertThat(exception.getMessage()).contains("tolerance window");
        assertThat(exception.getErrorCode()).isEqualTo("AUTH_002");
    }

    // MalformedAuthHeaderException Tests

    @Test
    void malformedAuthHeaderException_hasCorrectErrorCode() {
        MalformedAuthHeaderException exception = new MalformedAuthHeaderException("Missing header");

        assertThat(exception.getErrorCode()).isEqualTo("AUTH_003");
    }

    @Test
    void malformedAuthHeaderException_hasUnauthorizedStatus() {
        MalformedAuthHeaderException exception = new MalformedAuthHeaderException("Invalid format");

        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void malformedAuthHeaderException_preservesMessage() {
        String message = "Authorization header must contain token:timestamp:hash";
        MalformedAuthHeaderException exception = new MalformedAuthHeaderException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void malformedAuthHeaderException_extendsSifBrokerException() {
        MalformedAuthHeaderException exception = new MalformedAuthHeaderException("Test message");

        assertThat(exception).isInstanceOf(SifBrokerException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void malformedAuthHeaderException_withMissingPrefix() {
        String message = "Invalid authorization header format";
        MalformedAuthHeaderException exception = new MalformedAuthHeaderException(message);

        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("AUTH_003");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void malformedAuthHeaderException_withIncorrectParts() {
        String message = "Authorization header must contain three colon-separated parts";
        MalformedAuthHeaderException exception = new MalformedAuthHeaderException(message);

        assertThat(exception.getMessage()).contains("three colon-separated parts");
        assertThat(exception.getErrorCode()).isEqualTo("AUTH_003");
    }

    // Cross-Exception Tests

    @Test
    void allAuthenticationExceptions_haveUnauthorizedStatus() {
        InvalidTokenException tokenException = new InvalidTokenException("msg");
        InvalidTimestampException timestampException = new InvalidTimestampException("msg");
        MalformedAuthHeaderException headerException = new MalformedAuthHeaderException("msg");

        assertThat(tokenException.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(timestampException.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(headerException.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void allAuthenticationExceptions_haveUniqueErrorCodes() {
        InvalidTokenException tokenException = new InvalidTokenException("msg");
        InvalidTimestampException timestampException = new InvalidTimestampException("msg");
        MalformedAuthHeaderException headerException = new MalformedAuthHeaderException("msg");

        assertThat(tokenException.getErrorCode()).isEqualTo("AUTH_001");
        assertThat(timestampException.getErrorCode()).isEqualTo("AUTH_002");
        assertThat(headerException.getErrorCode()).isEqualTo("AUTH_003");

        // Verify all codes are unique
        assertThat(tokenException.getErrorCode()).isNotEqualTo(timestampException.getErrorCode());
        assertThat(tokenException.getErrorCode()).isNotEqualTo(headerException.getErrorCode());
        assertThat(timestampException.getErrorCode()).isNotEqualTo(headerException.getErrorCode());
    }

    @Test
    void allAuthenticationExceptions_extendSifBrokerException() {
        InvalidTokenException tokenException = new InvalidTokenException("msg");
        InvalidTimestampException timestampException = new InvalidTimestampException("msg");
        MalformedAuthHeaderException headerException = new MalformedAuthHeaderException("msg");

        assertThat(tokenException).isInstanceOf(SifBrokerException.class);
        assertThat(timestampException).isInstanceOf(SifBrokerException.class);
        assertThat(headerException).isInstanceOf(SifBrokerException.class);
    }

    @Test
    void allAuthenticationExceptions_areRuntimeExceptions() {
        InvalidTokenException tokenException = new InvalidTokenException("msg");
        InvalidTimestampException timestampException = new InvalidTimestampException("msg");
        MalformedAuthHeaderException headerException = new MalformedAuthHeaderException("msg");

        assertThat(tokenException).isInstanceOf(RuntimeException.class);
        assertThat(timestampException).isInstanceOf(RuntimeException.class);
        assertThat(headerException).isInstanceOf(RuntimeException.class);
    }

    @Test
    void authenticationExceptions_canBeDistinguishedByErrorCode() {
        // Simulate catching a generic SifBrokerException and identifying the specific type
        SifBrokerException tokenException = new InvalidTokenException("Invalid token");
        SifBrokerException timestampException = new InvalidTimestampException("Invalid timestamp");
        SifBrokerException headerException = new MalformedAuthHeaderException("Malformed header");

        // Can identify exception type by error code
        assertThat(tokenException.getErrorCode()).startsWith("AUTH_");
        assertThat(timestampException.getErrorCode()).startsWith("AUTH_");
        assertThat(headerException.getErrorCode()).startsWith("AUTH_");

        // Can distinguish between specific authentication errors
        if (tokenException.getErrorCode().equals("AUTH_001")) {
            assertThat(tokenException).isInstanceOf(InvalidTokenException.class);
        }
        if (timestampException.getErrorCode().equals("AUTH_002")) {
            assertThat(timestampException).isInstanceOf(InvalidTimestampException.class);
        }
        if (headerException.getErrorCode().equals("AUTH_003")) {
            assertThat(headerException).isInstanceOf(MalformedAuthHeaderException.class);
        }
    }

    @Test
    void authenticationExceptions_supportDetailedMessages() {
        String detailedTokenMessage = "Session token 'abc123' does not exist in environment 'env-456'";
        String detailedTimestampMessage = "Timestamp '2024-01-01T00:00:00Z' is 10 minutes old, exceeds 5 minute tolerance";
        String detailedHeaderMessage = "Authorization header 'Bearer token' does not start with 'SIF_HMACSHA256'";

        InvalidTokenException tokenException = new InvalidTokenException(detailedTokenMessage);
        InvalidTimestampException timestampException = new InvalidTimestampException(detailedTimestampMessage);
        MalformedAuthHeaderException headerException = new MalformedAuthHeaderException(detailedHeaderMessage);

        assertThat(tokenException.getMessage()).contains("abc123");
        assertThat(timestampException.getMessage()).contains("10 minutes");
        assertThat(headerException.getMessage()).contains("SIF_HMACSHA256");
    }
}
