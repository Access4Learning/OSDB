// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.SifAuthenticationUtils.AuthHeader;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation Property Tests — Non-Auth Path Behavior Unchanged.
 *
 * <p>These tests exercise {@link SifAuthenticationUtils} (the canonical server-side code
 * that must NOT change) on UNFIXED code. They confirm baseline behavior that the fix
 * must preserve:
 * <ol>
 *   <li>{@code computeHmac} is deterministic</li>
 *   <li>{@code parseAuthHeader} correctly extracts sessionToken and hash from
 *       properly-formatted {@code SIF_HMACSHA256 Base64(sessionToken:hash)} headers</li>
 *   <li>Round-trip: build header with {@code computeHmac} output → parse with
 *       {@code parseAuthHeader} → extracted sessionToken matches original</li>
 * </ol>
 *
 * <p>EXPECTED OUTCOME: All tests PASS on unfixed code (confirms baseline to preserve).</p>
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 3.3, 3.4</b></p>
 */
class AuthHeaderPreservationTest {

    // ── Arbitraries ──────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> sessionTokens() {
        // Alphanumeric tokens 8-40 chars — representative of real session tokens
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(8)
                .ofMaxLength(40);
    }

    @Provide
    Arbitrary<String> sharedSecrets() {
        // Printable ASCII secrets 8-64 chars — raw UTF-8 strings as used by server
        return Arbitraries.strings()
                .withCharRange('!', '~')
                .ofMinLength(8)
                .ofMaxLength(64);
    }

    @Provide
    Arbitrary<String> isoTimestamps() {
        // Recent ISO-8601 timestamps within ±5 minutes of now
        long now = Instant.now().getEpochSecond();
        return Arbitraries.longs()
                .between(now - 300, now + 300)
                .map(epoch -> Instant.ofEpochSecond(epoch).toString());
    }

    // ── Property 1: computeHmac is deterministic ─────────────────────────

    /**
     * For any valid sessionToken, timestamp, and sharedSecret: calling
     * {@code computeHmac} twice with the same inputs yields the same result.
     *
     * <p><b>Validates: Requirements 3.1, 3.2, 3.3, 3.4</b></p>
     */
    @Property(tries = 20)
    void computeHmacIsDeterministic(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("isoTimestamps") String timestamp,
            @ForAll("sharedSecrets") String sharedSecret) {

        String hmac1 = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
        String hmac2 = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        assertThat(hmac1)
                .as("computeHmac must be deterministic for same inputs")
                .isEqualTo(hmac2);

        // Also verify output is valid Base64
        assertThat(hmac1).isNotBlank();
        byte[] decoded = Base64.getDecoder().decode(hmac1);
        assertThat(decoded).hasSize(32); // HMAC-SHA256 produces 32 bytes
    }

    // ── Property 2: parseAuthHeader extracts correct components ──────────

    /**
     * For any properly-formatted auth header {@code SIF_HMACSHA256 Base64(sessionToken:hash)}:
     * {@code parseAuthHeader} extracts the correct sessionToken and hash.
     *
     * <p><b>Validates: Requirements 3.1, 3.2, 3.3, 3.4</b></p>
     */
    @Property(tries = 20)
    void parseAuthHeaderExtractsCorrectComponents(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("sessionTokens") String hash) {

        // Build a properly-formatted header: SIF_HMACSHA256 Base64(sessionToken:hash)
        String credentials = sessionToken + ":" + hash;
        String base64Credentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String header = "SIF_HMACSHA256 " + base64Credentials;

        // Parse it
        AuthHeader parsed = SifAuthenticationUtils.parseAuthHeader(header);

        // Verify extracted components match originals
        assertThat(parsed.sessionToken())
                .as("parseAuthHeader must extract correct sessionToken")
                .isEqualTo(sessionToken);
        assertThat(parsed.hash())
                .as("parseAuthHeader must extract correct hash")
                .isEqualTo(hash);
    }

    // ── Property 3: Round-trip (computeHmac → build header → parseAuthHeader) ──

    /**
     * Round-trip: build header with {@code computeHmac} output → parse with
     * {@code parseAuthHeader} → extracted sessionToken matches original and
     * extracted hash matches the computed HMAC.
     *
     * <p><b>Validates: Requirements 3.1, 3.2, 3.3, 3.4</b></p>
     */
    @Property(tries = 20)
    void roundTripBuildAndParseAuthHeader(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("isoTimestamps") String timestamp,
            @ForAll("sharedSecrets") String sharedSecret) {

        // Step 1: Compute HMAC using canonical server-side method
        String hmac = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        // Step 2: Build header in canonical format: SIF_HMACSHA256 Base64(sessionToken:hash)
        String credentials = sessionToken + ":" + hmac;
        String base64Credentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String header = "SIF_HMACSHA256 " + base64Credentials;

        // Step 3: Parse with canonical server-side parser
        AuthHeader parsed = SifAuthenticationUtils.parseAuthHeader(header);

        // Step 4: Verify round-trip
        assertThat(parsed.sessionToken())
                .as("Round-trip: extracted sessionToken must match original")
                .isEqualTo(sessionToken);
        assertThat(parsed.hash())
                .as("Round-trip: extracted hash must match computeHmac output")
                .isEqualTo(hmac);
    }
}
