// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.auth;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test: for any non-empty sessionToken and sharedSecret,
 * HmacAuthenticator.generateAuthHeaders produces a valid Authorization header
 * and ISO 8601 timestamp.
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 2.5
 * Feature: gatling-load-tests, Property 1: HMAC authentication round-trip
 */
class HmacAuthenticatorPropertyTest {

    /**
     * For any non-empty sessionToken and sharedSecret:
     * 1. Authorization header starts with "SIF_HMACSHA256 "
     * 2. Remainder is valid Base64
     * 3. Decoding Base64 yields "sessionToken:hash"
     * 4. hash is valid Base64-encoded HMAC-SHA256 of "sessionToken:timestamp" keyed with sharedSecret
     * 5. Timestamp is valid ISO 8601
     *
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.5**
     */
    @Property(tries = 100)
    @Tag("Feature: gatling-load-tests, Property 1: HMAC authentication round-trip")
    void hmacAuthRoundTrip(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("sharedSecrets") String sharedSecret
    ) {
        // Use explicit timestamp for deterministic verification
        String timestamp = Instant.now().toString();
        String[] headers = HmacAuthenticator.generateAuthHeaders(sessionToken, sharedSecret, timestamp);

        String authHeader = headers[0];
        String returnedTimestamp = headers[1];

        // 1. Authorization header starts with prefix
        assertTrue(authHeader.startsWith(HmacAuthenticator.AUTH_PREFIX),
                "Authorization header must start with 'SIF_HMACSHA256 '");

        // 2. Remainder is valid Base64
        String base64Part = authHeader.substring(HmacAuthenticator.AUTH_PREFIX.length());
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(base64Part);
        } catch (IllegalArgumentException e) {
            fail("Remainder after prefix is not valid Base64: " + base64Part);
            return;
        }

        // 3. Decoded Base64 yields "sessionToken:hash"
        String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
        assertTrue(decoded.startsWith(sessionToken + ":"),
                "Decoded payload must start with sessionToken + ':'");
        String extractedHash = decoded.substring(sessionToken.length() + 1);

        // 4. hash is valid Base64-encoded HMAC-SHA256 of "sessionToken:timestamp" keyed with sharedSecret
        // Verify by independently computing the expected hash
        String expectedHash = independentHmacSha256Base64(
                sessionToken + ":" + timestamp, sharedSecret);
        assertEquals(expectedHash, extractedHash,
                "Extracted hash must match independent HMAC-SHA256 computation");

        // Also verify the extracted hash is valid Base64
        assertDoesNotThrow(() -> Base64.getDecoder().decode(extractedHash),
                "Extracted hash must be valid Base64");

        // 5. Timestamp is valid ISO 8601
        assertEquals(timestamp, returnedTimestamp, "Returned timestamp must match input");
        assertDoesNotThrow(() -> Instant.parse(returnedTimestamp),
                "Timestamp must be valid ISO 8601");
    }

    @Provide
    Arbitrary<String> sessionTokens() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(64);
    }

    @Provide
    Arbitrary<String> sharedSecrets() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_', '!', '@', '#')
                .ofMinLength(1)
                .ofMaxLength(64);
    }

    /** Independent HMAC-SHA256 computation — does not use HmacAuthenticator. */
    private static String independentHmacSha256Base64(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Independent HMAC computation failed", e);
        }
    }
}
