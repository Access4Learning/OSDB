// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.sifunity.broker.common.exception.MalformedAuthHeaderException;
import org.sifunity.integration.generator.EnvironmentGenerator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition Exploration Test — Auth Header Format Incompatibility.
 *
 * <p>This test is EXPECTED TO FAIL on unfixed code. Failure confirms the bug exists:
 * <ul>
 *   <li>{@code EnvironmentGenerator.generateAuthHeader()} produces plain-text
 *       {@code SIF_HMACSHA256 sessionToken:timestamp:hmac} which is not valid Base64</li>
 *   <li>{@code SifAuthenticationUtils.parseAuthHeader()} expects
 *       {@code SIF_HMACSHA256 Base64(sessionToken:hash)}</li>
 *   <li>Additionally, EnvironmentGenerator Base64-decodes the secret while the server
 *       uses raw UTF-8 bytes for HMAC key derivation</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 1.1, 1.4, 2.1, 2.4</b></p>
 */
class AuthHeaderBugConditionExplorationTest {

    private final EnvironmentGenerator generator = new EnvironmentGenerator("http://localhost:0");

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
        // Valid Base64-encoded secrets (EnvironmentGenerator expects Base64-decodable secret)
        // Generate raw bytes then Base64-encode them so EnvironmentGenerator won't crash
        return Arbitraries.bytes().array(byte[].class)
                .ofMinSize(16)
                .ofMaxSize(64)
                .map(bytes -> Base64.getEncoder().encodeToString(bytes));
    }

    @Provide
    Arbitrary<Instant> timestamps() {
        // Recent timestamps within ±5 minutes of now
        long now = Instant.now().getEpochSecond();
        return Arbitraries.longs()
                .between(now - 300, now + 300)
                .map(Instant::ofEpochSecond);
    }

    // ── Property 1: EnvironmentGenerator round-trip ──────────────────────

    /**
     * For any valid sessionToken/sharedSecret/timestamp, the auth header produced by
     * {@code EnvironmentGenerator.generateAuthHeader()} should be parseable by
     * {@code SifAuthenticationUtils.parseAuthHeader()} and the extracted sessionToken
     * should match the original, with the hash matching {@code computeHmac()}.
     *
     * <p><b>Validates: Requirements 1.1, 1.4, 2.1, 2.4</b></p>
     *
     * <p>EXPECTED TO FAIL on unfixed code — confirms bug condition.</p>
     */
    @Property(tries = 20)
    void environmentGeneratorAuthHeaderRoundTrip(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("sharedSecrets") String sharedSecret,
            @ForAll("timestamps") Instant timestamp) {

        // Generate header using current (buggy) EnvironmentGenerator
        String header = generator.generateAuthHeader(sessionToken, sharedSecret, timestamp);

        // Attempt to parse with canonical server-side parser
        SifAuthenticationUtils.AuthHeader parsed = SifAuthenticationUtils.parseAuthHeader(header);

        // Verify round-trip: parsed sessionToken matches original
        assertThat(parsed.sessionToken()).isEqualTo(sessionToken);

        // Verify HMAC matches canonical computation
        String expectedHmac = SifAuthenticationUtils.computeHmac(
                sessionToken, timestamp.toString(), sharedSecret);
        assertThat(parsed.hash()).isEqualTo(expectedHmac);
    }

    // ── Property 2: IntegrationTestBase round-trip ───────────────────────

    /**
     * For any valid sessionToken/sharedSecret/timestamp, the auth header produced by
     * the same logic as {@code IntegrationTestBase.generateAuthHeader()} should be
     * parseable by {@code SifAuthenticationUtils.parseAuthHeader()} and the extracted
     * sessionToken should match the original, with the hash matching {@code computeHmac()}.
     *
     * <p><b>Validates: Requirements 1.1, 2.1, 2.4</b></p>
     *
     * <p>EXPECTED TO FAIL on unfixed code — confirms bug condition.</p>
     */
    @Property(tries = 20)
    void integrationTestBaseAuthHeaderRoundTrip(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("sharedSecrets") String sharedSecret,
            @ForAll("timestamps") Instant timestamp) {

        // Replicate IntegrationTestBase.generateAuthHeader() logic (protected, can't call directly)
        String header = replicateIntegrationTestBaseGenerateAuthHeader(
                sessionToken, sharedSecret, timestamp.toString());

        // Attempt to parse with canonical server-side parser
        SifAuthenticationUtils.AuthHeader parsed = SifAuthenticationUtils.parseAuthHeader(header);

        // Verify round-trip: parsed sessionToken matches original
        assertThat(parsed.sessionToken()).isEqualTo(sessionToken);

        // Verify HMAC matches canonical computation
        String expectedHmac = SifAuthenticationUtils.computeHmac(
                sessionToken, timestamp.toString(), sharedSecret);
        assertThat(parsed.hash()).isEqualTo(expectedHmac);
    }

    // ── Helper: replicates IntegrationTestBase.generateAuthHeader() ──────

    /**
     * Exact replica of {@code IntegrationTestBase.generateAuthHeader()} logic.
     * Uses {@code sharedSecret.getBytes(StandardCharsets.UTF_8)} for HMAC key — matches SifAuthenticationUtils.
     */
    private String replicateIntegrationTestBaseGenerateAuthHeader(
            String sessionToken, String sharedSecret, String timestamp) {
        try {
            String toSign = sessionToken + ":" + timestamp;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8));
            String hmacBase64 = Base64.getEncoder().encodeToString(hmacBytes);
            return "SIF_HMACSHA256 " + Base64.getEncoder().encodeToString((sessionToken + ":" + hmacBase64).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate auth header", e);
        }
    }
}
