// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.DefaultValues;
import org.sifunity.broker.common.exception.InvalidTimestampException;
import org.sifunity.broker.common.exception.MalformedAuthHeaderException;
import org.sifunity.broker.common.util.SifAuthenticationUtils.AuthHeader;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SifAuthenticationUtils.
 * Tests HMAC computation, authorization header parsing, and timestamp validation.
 */
class SifAuthenticationUtilsTest {

    // ========== computeHmac() Tests ==========

    @Test
    void computeHmac_withValidInputs_returnsBase64EncodedHash() {
        String sessionToken = "test-token-123";
        String timestamp = "2024-01-15T10:30:00Z";
        String sharedSecret = "my-secret-key";

        String result = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        // Base64 encoded strings should only contain valid Base64 characters
        assertThat(result).matches("^[A-Za-z0-9+/]+=*$");
    }

    @Test
    void computeHmac_withSameInputs_returnsSameHash() {
        String sessionToken = "test-token-123";
        String timestamp = "2024-01-15T10:30:00Z";
        String sharedSecret = "my-secret-key";

        String hash1 = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
        String hash2 = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void computeHmac_withDifferentTokens_returnsDifferentHashes() {
        String timestamp = "2024-01-15T10:30:00Z";
        String sharedSecret = "my-secret-key";

        String hash1 = SifAuthenticationUtils.computeHmac("token-1", timestamp, sharedSecret);
        String hash2 = SifAuthenticationUtils.computeHmac("token-2", timestamp, sharedSecret);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void computeHmac_withDifferentTimestamps_returnsDifferentHashes() {
        String sessionToken = "test-token-123";
        String sharedSecret = "my-secret-key";

        String hash1 = SifAuthenticationUtils.computeHmac(sessionToken, "2024-01-15T10:30:00Z", sharedSecret);
        String hash2 = SifAuthenticationUtils.computeHmac(sessionToken, "2024-01-15T10:31:00Z", sharedSecret);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void computeHmac_withDifferentSecrets_returnsDifferentHashes() {
        String sessionToken = "test-token-123";
        String timestamp = "2024-01-15T10:30:00Z";

        String hash1 = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, "secret-1");
        String hash2 = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, "secret-2");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void computeHmac_withEmptyToken_returnsValidHash() {
        String sessionToken = "";
        String timestamp = "2024-01-15T10:30:00Z";
        String sharedSecret = "my-secret-key";

        String result = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
    }

    @Test
    void computeHmac_withEmptyTimestamp_returnsValidHash() {
        String sessionToken = "test-token-123";
        String timestamp = "";
        String sharedSecret = "my-secret-key";

        String result = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
    }

    @Test
    void computeHmac_withEmptySecret_throwsException() {
        // Empty secrets are not allowed by Java's HMAC implementation
        String sessionToken = "test-token-123";
        String timestamp = "2024-01-15T10:30:00Z";
        String sharedSecret = "";

        assertThatThrownBy(() -> SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to compute HMAC");
    }

    @Test
    void computeHmac_withSpecialCharacters_returnsValidHash() {
        String sessionToken = "token-with-special-chars-!@#$%";
        String timestamp = "2024-01-15T10:30:00Z";
        String sharedSecret = "secret-with-special-chars-^&*()";

        String result = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        assertThat(result).matches("^[A-Za-z0-9+/]+=*$");
    }

    @Test
    void computeHmac_withUnicodeCharacters_returnsValidHash() {
        String sessionToken = "token-with-unicode-日本語";
        String timestamp = "2024-01-15T10:30:00Z";
        String sharedSecret = "secret-with-unicode-中文";

        String result = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        assertThat(result).matches("^[A-Za-z0-9+/]+=*$");
    }

    @Test
    void computeHmac_withLongInputs_returnsValidHash() {
        String sessionToken = "a".repeat(1000);
        String timestamp = "2024-01-15T10:30:00Z";
        String sharedSecret = "b".repeat(1000);

        String result = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
    }

    // ========== parseAuthHeader() Tests ==========

    @Test
    void parseAuthHeader_withValidHeader_returnsAuthHeader() {
        String sessionToken = "my-token";
        String hash = "abc123hash";
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;

        AuthHeader result = SifAuthenticationUtils.parseAuthHeader(authHeader);

        assertThat(result).isNotNull();
        assertThat(result.sessionToken()).isEqualTo("my-token");
        assertThat(result.hash()).isEqualTo("abc123hash");
    }

    @Test
    void parseAuthHeader_withComplexToken_returnsAuthHeader() {
        String sessionToken = "550e8400-e29b-41d4-a716-446655440000";
        String hash = "dGVzdGhhc2g=";
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;

        AuthHeader result = SifAuthenticationUtils.parseAuthHeader(authHeader);

        assertThat(result).isNotNull();
        assertThat(result.sessionToken()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(result.hash()).isEqualTo("dGVzdGhhc2g=");
    }

    @Test
    void parseAuthHeader_withBase64Hash_returnsAuthHeader() {
        String sessionToken = "token";
        String hash = "SGVsbG8gV29ybGQh";
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;

        AuthHeader result = SifAuthenticationUtils.parseAuthHeader(authHeader);

        assertThat(result).isNotNull();
        assertThat(result.hash()).isEqualTo("SGVsbG8gV29ybGQh");
    }

    @Test
    void parseAuthHeader_withNullHeader_throwsException() {
        assertThatThrownBy(() -> SifAuthenticationUtils.parseAuthHeader(null))
            .isInstanceOf(MalformedAuthHeaderException.class)
            .hasMessageContaining("Invalid authorization header format");
    }

    @Test
    void parseAuthHeader_withEmptyString_throwsException() {
        assertThatThrownBy(() -> SifAuthenticationUtils.parseAuthHeader(""))
            .isInstanceOf(MalformedAuthHeaderException.class)
            .hasMessageContaining("Invalid authorization header format");
    }

    @Test
    void parseAuthHeader_withoutPrefix_throwsException() {
        String credentials = "token:hash";
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        
        assertThatThrownBy(() -> SifAuthenticationUtils.parseAuthHeader(base64Credentials))
            .isInstanceOf(MalformedAuthHeaderException.class)
            .hasMessageContaining("Invalid authorization header format");
    }

    @Test
    void parseAuthHeader_withWrongPrefix_throwsException() {
        String credentials = "token:hash";
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        
        assertThatThrownBy(() -> SifAuthenticationUtils.parseAuthHeader("BASIC " + base64Credentials))
            .isInstanceOf(MalformedAuthHeaderException.class)
            .hasMessageContaining("Invalid authorization header format");
    }

    @Test
    void parseAuthHeader_withIncorrectPrefix_throwsException() {
        String credentials = "token:hash";
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        
        assertThatThrownBy(() -> SifAuthenticationUtils.parseAuthHeader("SIF_HMACSHA512 " + base64Credentials))
            .isInstanceOf(MalformedAuthHeaderException.class)
            .hasMessageContaining("Invalid authorization header format");
    }

    @Test
    void parseAuthHeader_withMissingParts_throwsException() {
        String credentials = "token-only";
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        
        assertThatThrownBy(() -> SifAuthenticationUtils.parseAuthHeader("SIF_HMACSHA256 " + base64Credentials))
            .isInstanceOf(MalformedAuthHeaderException.class)
            .hasMessageContaining("Authorization header must contain sessionToken:hash");
    }

    @Test
    void parseAuthHeader_withInvalidBase64_throwsException() {
        assertThatThrownBy(() -> SifAuthenticationUtils.parseAuthHeader("SIF_HMACSHA256 not-valid-base64!!!"))
            .isInstanceOf(MalformedAuthHeaderException.class)
            .hasMessageContaining("Invalid Base64 encoding");
    }

    @Test
    void parseAuthHeader_withOnlyPrefix_throwsException() {
        // Empty value after prefix results in empty decoded string, which fails the colon check
        assertThatThrownBy(() -> SifAuthenticationUtils.parseAuthHeader("SIF_HMACSHA256 "))
            .isInstanceOf(MalformedAuthHeaderException.class)
            .hasMessageContaining("Authorization header must contain sessionToken:hash");
    }

    @Test
    void parseAuthHeader_withEmptyToken_throwsException() {
        String credentials = ":hash";
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;

        assertThatThrownBy(() -> SifAuthenticationUtils.parseAuthHeader(authHeader))
            .isInstanceOf(MalformedAuthHeaderException.class)
            .hasMessageContaining("Authorization header components cannot be empty");
    }

    @Test
    void parseAuthHeader_withEmptyHash_throwsException() {
        String credentials = "token:";
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;

        assertThatThrownBy(() -> SifAuthenticationUtils.parseAuthHeader(authHeader))
            .isInstanceOf(MalformedAuthHeaderException.class)
            .hasMessageContaining("Authorization header components cannot be empty");
    }

    @Test
    void parseAuthHeader_withColonsInHash_parsesCorrectly() {
        // Hash might contain colons - should be handled correctly since we only split on first colon
        String sessionToken = "token";
        String hash = "hash:with:colons";
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;

        AuthHeader result = SifAuthenticationUtils.parseAuthHeader(authHeader);

        assertThat(result).isNotNull();
        assertThat(result.sessionToken()).isEqualTo("token");
        assertThat(result.hash()).isEqualTo("hash:with:colons");
    }

    // ========== validateTimestamp() Tests ==========

    @Test
    void validateTimestamp_withCurrentTime_succeeds() {
        String timestamp = Instant.now().toString();

        // Should not throw exception
        SifAuthenticationUtils.validateTimestamp(timestamp);
    }

    @Test
    void validateTimestamp_withTimeWithinTolerance_succeeds() {
        // 2 minutes ago (within default 5 minute tolerance)
        String timestamp = Instant.now().minus(2, ChronoUnit.MINUTES).toString();

        // Should not throw exception
        SifAuthenticationUtils.validateTimestamp(timestamp);
    }

    @Test
    void validateTimestamp_withFutureTimeWithinTolerance_succeeds() {
        // 2 minutes in the future (within default 5 minute tolerance)
        String timestamp = Instant.now().plus(2, ChronoUnit.MINUTES).toString();

        // Should not throw exception
        SifAuthenticationUtils.validateTimestamp(timestamp);
    }

    @Test
    void validateTimestamp_withTimeAtToleranceBoundary_succeeds() {
        // Exactly 5 minutes ago (at the boundary)
        String timestamp = Instant.now().minus(5, ChronoUnit.MINUTES).toString();

        // Should not throw exception
        SifAuthenticationUtils.validateTimestamp(timestamp);
    }

    @Test
    void validateTimestamp_withFutureTimeAtToleranceBoundary_succeeds() {
        // Exactly 5 minutes in the future (at the boundary)
        String timestamp = Instant.now().plus(5, ChronoUnit.MINUTES).toString();

        // Should not throw exception
        SifAuthenticationUtils.validateTimestamp(timestamp);
    }

    @Test
    void validateTimestamp_withTimeOutsideTolerance_throwsException() {
        // 10 minutes ago (outside default 5 minute tolerance)
        String timestamp = Instant.now().minus(10, ChronoUnit.MINUTES).toString();

        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp(timestamp))
            .isInstanceOf(InvalidTimestampException.class)
            .hasMessageContaining("Timestamp is outside tolerance window (5 minutes)");
    }

    @Test
    void validateTimestamp_withFutureTimeOutsideTolerance_throwsException() {
        // 10 minutes in the future (outside default 5 minute tolerance)
        String timestamp = Instant.now().plus(10, ChronoUnit.MINUTES).toString();

        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp(timestamp))
            .isInstanceOf(InvalidTimestampException.class)
            .hasMessageContaining("Timestamp is outside tolerance window (5 minutes)");
    }

    @Test
    void validateTimestamp_withInvalidFormat_throwsException() {
        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp("not-a-timestamp"))
            .isInstanceOf(InvalidTimestampException.class)
            .hasMessageContaining("Invalid timestamp format: not-a-timestamp");
    }

    @Test
    void validateTimestamp_withNullTimestamp_throwsException() {
        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp(null))
            .isInstanceOf(InvalidTimestampException.class)
            .hasMessageContaining("Invalid timestamp format: null");
    }

    @Test
    void validateTimestamp_withEmptyString_throwsException() {
        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp(""))
            .isInstanceOf(InvalidTimestampException.class)
            .hasMessageContaining("Invalid timestamp format: ");
    }

    @Test
    void validateTimestamp_withNonIso8601Format_throwsException() {
        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp("2024-01-15 10:30:00"))
            .isInstanceOf(InvalidTimestampException.class)
            .hasMessageContaining("Invalid timestamp format: 2024-01-15 10:30:00");
    }

    @Test
    void validateTimestamp_withCustomTolerance_succeeds() {
        // 8 minutes ago (outside default 5 minute tolerance, but within custom 10 minute tolerance)
        String timestamp = Instant.now().minus(8, ChronoUnit.MINUTES).toString();

        // Should not throw exception with custom tolerance
        SifAuthenticationUtils.validateTimestamp(timestamp, 10);
    }

    @Test
    void validateTimestamp_withCustomTolerance_throwsException() {
        // 8 minutes ago (within default 5 minute tolerance, but outside custom 2 minute tolerance)
        String timestamp = Instant.now().minus(8, ChronoUnit.MINUTES).toString();

        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp(timestamp, 2))
            .isInstanceOf(InvalidTimestampException.class)
            .hasMessageContaining("Timestamp is outside tolerance window (2 minutes)");
    }

    @Test
    void validateTimestamp_withZeroTolerance_onlyAcceptsExactTime() {
        // With zero tolerance, only timestamps within the same minute should pass
        // A timestamp 1 minute in the past should fail
        String timestamp = Instant.now().minus(1, ChronoUnit.MINUTES).toString();

        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp(timestamp, 0))
            .isInstanceOf(InvalidTimestampException.class)
            .hasMessageContaining("Timestamp is outside tolerance window (0 minutes)");
    }

    @Test
    void validateTimestamp_withLargeTolerance_acceptsOldTimestamp() {
        // 1 hour ago (within 120 minute tolerance)
        String timestamp = Instant.now().minus(60, ChronoUnit.MINUTES).toString();

        // Should not throw exception
        SifAuthenticationUtils.validateTimestamp(timestamp, 120);
    }

    @Test
    void validateTimestamp_usesDefaultTolerance() {
        // Verify that the default tolerance is used
        String timestamp = Instant.now().minus(DefaultValues.TIMESTAMP_TOLERANCE_MINUTES, ChronoUnit.MINUTES).toString();

        // Should not throw exception (at the boundary)
        SifAuthenticationUtils.validateTimestamp(timestamp);
    }

    // ========== Integration Tests ==========

    @Test
    void testCompleteAuthenticationFlow_withValidCredentials() {
        // Simulate a complete authentication flow
        String sessionToken = "550e8400-e29b-41d4-a716-446655440000";
        String timestamp = Instant.now().toString();
        String sharedSecret = "my-shared-secret-key";

        // Client computes HMAC
        String hash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        // Client sends authorization header (Base64 encoded)
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;

        // Server parses authorization header
        AuthHeader parsedHeader = SifAuthenticationUtils.parseAuthHeader(authHeader);

        // Server validates timestamp (sent in separate header)
        SifAuthenticationUtils.validateTimestamp(timestamp);

        // Server recomputes HMAC
        String expectedHash = SifAuthenticationUtils.computeHmac(
            parsedHeader.sessionToken(),
            timestamp,
            sharedSecret
        );

        // Server verifies signature
        assertThat(parsedHeader.hash()).isEqualTo(expectedHash);
    }

    @Test
    void testCompleteAuthenticationFlow_withInvalidSignature() {
        // Simulate authentication with wrong secret
        String sessionToken = "550e8400-e29b-41d4-a716-446655440000";
        String timestamp = Instant.now().toString();
        String clientSecret = "client-secret";
        String serverSecret = "server-secret";

        // Client computes HMAC with wrong secret
        String hash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, clientSecret);

        // Client sends authorization header (Base64 encoded)
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;

        // Server parses authorization header
        AuthHeader parsedHeader = SifAuthenticationUtils.parseAuthHeader(authHeader);

        // Server validates timestamp (should succeed)
        SifAuthenticationUtils.validateTimestamp(timestamp);

        // Server recomputes HMAC with correct secret
        String expectedHash = SifAuthenticationUtils.computeHmac(
            parsedHeader.sessionToken(),
            timestamp,
            serverSecret
        );

        // Server verifies signature (should fail)
        assertThat(parsedHeader.hash()).isNotEqualTo(expectedHash);
    }

    @Test
    void testCompleteAuthenticationFlow_withExpiredTimestamp() {
        // Simulate authentication with expired timestamp
        String sessionToken = "550e8400-e29b-41d4-a716-446655440000";
        String timestamp = Instant.now().minus(10, ChronoUnit.MINUTES).toString();
        String sharedSecret = "my-shared-secret-key";

        // Client computes HMAC
        String hash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        // Client sends authorization header (Base64 encoded)
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;

        // Server parses authorization header
        AuthHeader parsedHeader = SifAuthenticationUtils.parseAuthHeader(authHeader);

        // Server validates timestamp (should fail)
        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp(timestamp))
            .isInstanceOf(InvalidTimestampException.class)
            .hasMessageContaining("Timestamp is outside tolerance window");
    }

    @Test
    void testAuthHeader_isRecord() {
        // Verify that AuthHeader is a record with proper behavior
        AuthHeader header1 = new AuthHeader("token", "hash");
        AuthHeader header2 = new AuthHeader("token", "hash");
        AuthHeader header3 = new AuthHeader("different", "hash");

        // Records should have proper equals() and hashCode()
        assertThat(header1).isEqualTo(header2);
        assertThat(header1).isNotEqualTo(header3);
        assertThat(header1.hashCode()).isEqualTo(header2.hashCode());

        // Records should have proper toString()
        assertThat(header1.toString()).contains("token", "hash");
    }

    @Test
    void testUtilityClass_cannotBeInstantiated() {
        assertThatThrownBy(() -> {
            // Use reflection to try to instantiate the utility class
            var constructor = SifAuthenticationUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
        .hasCauseInstanceOf(UnsupportedOperationException.class)
        .getCause()
        .hasMessageContaining("Utility class");
    }
}
