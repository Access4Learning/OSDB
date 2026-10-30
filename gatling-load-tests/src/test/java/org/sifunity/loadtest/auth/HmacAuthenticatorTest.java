// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.auth;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HmacAuthenticator} with known-good pre-computed HMAC values.
 */
class HmacAuthenticatorTest {

    private static final String SESSION_TOKEN = "test-session-token-abc123";
    private static final String SHARED_SECRET = "test-shared-secret-xyz789";
    private static final String TIMESTAMP = "2026-01-21T06:30:00Z";

    /** Pre-compute expected values using raw JDK crypto (independent of HmacAuthenticator). */
    private static final String EXPECTED_HASH;
    private static final String EXPECTED_AUTH;

    static {
        try {
            // message = sessionToken:timestamp
            String message = SESSION_TOKEN + ":" + TIMESTAMP;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SHARED_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            EXPECTED_HASH = Base64.getEncoder().encodeToString(rawHmac);

            // tokenAndHash = sessionToken:hash, then Base64-encode entire string
            String tokenAndHash = SESSION_TOKEN + ":" + EXPECTED_HASH;
            String encoded = Base64.getEncoder().encodeToString(
                    tokenAndHash.getBytes(StandardCharsets.UTF_8));
            EXPECTED_AUTH = "SIF_HMACSHA256 " + encoded;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generateAuthHeadersProducesCorrectAuthorizationHeader() {
        String[] headers = HmacAuthenticator.generateAuthHeaders(SESSION_TOKEN, SHARED_SECRET, TIMESTAMP);
        assertEquals(EXPECTED_AUTH, headers[0]);
    }

    @Test
    void generateAuthHeadersReturnsProvidedTimestamp() {
        String[] headers = HmacAuthenticator.generateAuthHeaders(SESSION_TOKEN, SHARED_SECRET, TIMESTAMP);
        assertEquals(TIMESTAMP, headers[1]);
    }

    @Test
    void authorizationHeaderStartsWithPrefix() {
        String[] headers = HmacAuthenticator.generateAuthHeaders(SESSION_TOKEN, SHARED_SECRET, TIMESTAMP);
        assertTrue(headers[0].startsWith("SIF_HMACSHA256 "));
    }

    @Test
    void base64PayloadDecodesToSessionTokenColonHash() {
        String[] headers = HmacAuthenticator.generateAuthHeaders(SESSION_TOKEN, SHARED_SECRET, TIMESTAMP);
        String base64Part = headers[0].substring("SIF_HMACSHA256 ".length());
        String decoded = new String(Base64.getDecoder().decode(base64Part), StandardCharsets.UTF_8);

        assertTrue(decoded.startsWith(SESSION_TOKEN + ":"),
                "Decoded payload should start with sessionToken:");
        String hash = decoded.substring(SESSION_TOKEN.length() + 1);
        assertEquals(EXPECTED_HASH, hash);
    }

    @Test
    void hmacSha256Base64MatchesIndependentComputation() {
        String data = "hello:world";
        String secret = "mysecret";

        // Independent computation
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(rawHmac);

            assertEquals(expected, HmacAuthenticator.hmacSha256Base64(data, secret));
        } catch (Exception e) {
            fail("HMAC computation failed: " + e.getMessage());
        }
    }

    @Test
    void noTimestampOverloadProducesValidIso8601Timestamp() {
        String[] headers = HmacAuthenticator.generateAuthHeaders(SESSION_TOKEN, SHARED_SECRET);
        String timestamp = headers[1];
        // Should parse without exception — Instant.parse validates ISO 8601
        assertDoesNotThrow(() -> java.time.Instant.parse(timestamp));
    }

    @Test
    void resultArrayAlwaysHasTwoElements() {
        String[] headers = HmacAuthenticator.generateAuthHeaders(SESSION_TOKEN, SHARED_SECRET, TIMESTAMP);
        assertEquals(2, headers.length);
    }

    @Test
    void differentSecretsProduceDifferentHeaders() {
        String[] headers1 = HmacAuthenticator.generateAuthHeaders(SESSION_TOKEN, "secret-A", TIMESTAMP);
        String[] headers2 = HmacAuthenticator.generateAuthHeaders(SESSION_TOKEN, "secret-B", TIMESTAMP);
        assertNotEquals(headers1[0], headers2[0]);
    }

    @Test
    void differentTokensProduceDifferentHeaders() {
        String[] headers1 = HmacAuthenticator.generateAuthHeaders("token-A", SHARED_SECRET, TIMESTAMP);
        String[] headers2 = HmacAuthenticator.generateAuthHeaders("token-B", SHARED_SECRET, TIMESTAMP);
        assertNotEquals(headers1[0], headers2[0]);
    }
}
