// SPDX-License-Identifier: MIT
package org.sifunity.integration.security;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;
import org.sifunity.integration.config.AdminCredentialsConfig;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for authentication rejection with invalid credentials.
 *
 * <p><strong>Property 3: Authentication rejection for invalid credentials</strong></p>
 * <p>For any request sent to the Gateway with either a corrupted HMAC signature
 * or an expired timestamp, the Gateway SHALL return HTTP 401 Unauthorized.</p>
 *
 * <p>This is a lightweight integration-level property test that only hits the Gateway.
 * No environment setup is needed since we are testing rejection of invalid credentials.
 * Infrastructure (containers, services) is started once via lazy initialization.</p>
 *
 * <p><strong>Validates: Requirements 5.2, 5.3</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("security")
@Tag("Feature: cross-service-integration-tests, Property 3: Authentication rejection for invalid credentials")
public class AuthenticationRejectionPropertyTest extends ServiceEnabledIntegrationTestBase {

    private static final String TEST_ENDPOINT = "/environments";

    private boolean infrastructureReady = false;

    /**
     * Lazily starts infrastructure once before all property iterations.
     */
    private void ensureInfrastructure() {
        if (infrastructureReady) {
            return;
        }
        log.info("Setting up infrastructure for AuthenticationRejectionPropertyTest");
        setupInfrastructure();
        infrastructureReady = true;
    }

    // ── Corruption strategy enum ─────────────────────────────────────

    /**
     * Defines the ways valid credentials can be corrupted.
     */
    enum CorruptionStrategy {
        FLIP_BITS,
        TRUNCATE_HMAC,
        WRONG_SECRET,
        EXPIRED_TIMESTAMP
    }

    // ── Arbitraries ──────────────────────────────────────────────────

    /**
     * Generates a random corruption strategy.
     */
    @Provide
    Arbitrary<CorruptionStrategy> corruptionStrategies() {
        return Arbitraries.of(CorruptionStrategy.values());
    }

    /**
     * Generates random bit-flip positions (0-255) for HMAC corruption.
     */
    @Provide
    Arbitrary<Integer> bitFlipPositions() {
        return Arbitraries.integers().between(0, 255);
    }

    /**
     * Generates random truncation lengths (1 to 10 chars removed from HMAC).
     */
    @Provide
    Arbitrary<Integer> truncationLengths() {
        return Arbitraries.integers().between(1, 10);
    }

    /**
     * Generates random wrong secrets (alphanumeric strings that differ from real secret).
     */
    @Provide
    Arbitrary<String> wrongSecrets() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(8)
            .ofMaxLength(32)
            .map(s -> Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Generates random amounts of time in the past for expired timestamps (1-48 hours).
     */
    @Provide
    Arbitrary<Long> expiredHoursInPast() {
        return Arbitraries.longs().between(1, 48);
    }

    // ── Property ─────────────────────────────────────────────────────

    /**
     * <strong>Property 3: Authentication rejection for invalid credentials</strong>
     *
     * <p>For any corruption strategy applied to valid credentials, the Gateway
     * SHALL return HTTP 401 Unauthorized.</p>
     *
     * <p><strong>Validates: Requirements 5.2, 5.3</strong></p>
     */
    @Property(tries = 10)
    @Tag("Feature: cross-service-integration-tests, Property 3: Authentication rejection for invalid credentials")
    void authenticationRejectionForInvalidCredentials(
        @ForAll("corruptionStrategies") CorruptionStrategy strategy,
        @ForAll("bitFlipPositions") int bitFlipPosition,
        @ForAll("truncationLengths") int truncationLength,
        @ForAll("wrongSecrets") String wrongSecret,
        @ForAll("expiredHoursInPast") long hoursInPast
    ) {
        ensureInfrastructure();

        log.info("Property iteration: strategy={}, bitFlip={}, truncLen={}, hoursInPast={}",
            strategy, bitFlipPosition, truncationLength, hoursInPast);

        AdminCredentialsConfig adminConfig = new AdminCredentialsConfig();
        String sessionToken = adminConfig.getSessionToken();
        String sharedSecret = adminConfig.getSharedSecret();

        String authHeader;
        String timestamp;

        switch (strategy) {
            case FLIP_BITS -> {
                timestamp = Instant.now().toString();
                authHeader = buildFlippedBitsAuthHeader(sessionToken, sharedSecret, timestamp, bitFlipPosition);
            }
            case TRUNCATE_HMAC -> {
                timestamp = Instant.now().toString();
                authHeader = buildTruncatedHmacAuthHeader(sessionToken, sharedSecret, timestamp, truncationLength);
            }
            case WRONG_SECRET -> {
                timestamp = Instant.now().toString();
                authHeader = generateAuthHeader(sessionToken, wrongSecret, timestamp);
            }
            case EXPIRED_TIMESTAMP -> {
                timestamp = Instant.now().minus(hoursInPast, ChronoUnit.HOURS).toString();
                authHeader = generateAuthHeader(sessionToken, sharedSecret, timestamp);
            }
            default -> throw new IllegalStateException("Unknown strategy: " + strategy);
        }

        Response response = given()
            .contentType("application/json")
            .accept("application/json")
            .header("Authorization", authHeader)
            .header("timestamp", timestamp)
            .header("messageId", UUID.randomUUID().toString())
        .when()
            .get(gatewayPathPrefix() + TEST_ENDPOINT)
        .then()
            .extract().response();

        log.info("Response status (strategy={}): {}", strategy, response.getStatusCode());

        assertThat(response.getStatusCode())
            .as("Gateway should return 401 for corruption strategy '%s'", strategy)
            .isEqualTo(401);
    }

    // ── Corruption helpers ───────────────────────────────────────────

    /**
     * Builds an auth header with random bits flipped in the HMAC signature.
     */
    private String buildFlippedBitsAuthHeader(
        String sessionToken,
        String sharedSecret,
        String timestamp,
        int bitFlipPosition
    ) {
        try {
            String toSign = sessionToken + ":" + timestamp;

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8));

            // Flip a bit at the given position (mod byte array length)
            int byteIndex = bitFlipPosition % hmacBytes.length;
            int bitIndex = bitFlipPosition % 8;
            hmacBytes[byteIndex] ^= (1 << bitIndex);

            String corruptedHmac = Base64.getEncoder().encodeToString(hmacBytes);
            String credentials = sessionToken + ":" + corruptedHmac;
            String base64Credentials = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)
            );
            return "SIF_HMACSHA256 " + base64Credentials;

        } catch (Exception e) {
            throw new RuntimeException("Failed to build flipped-bits auth header", e);
        }
    }

    /**
     * Builds an auth header with the HMAC signature truncated.
     */
    private String buildTruncatedHmacAuthHeader(
        String sessionToken,
        String sharedSecret,
        String timestamp,
        int truncationLength
    ) {
        try {
            String toSign = sessionToken + ":" + timestamp;

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8));

            String hmacBase64 = Base64.getEncoder().encodeToString(hmacBytes);

            // Truncate the HMAC string by removing chars from the end
            int charsToRemove = Math.min(truncationLength, hmacBase64.length() - 1);
            String truncatedHmac = hmacBase64.substring(0, hmacBase64.length() - charsToRemove);

            String credentials = sessionToken + ":" + truncatedHmac;
            String base64Credentials = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)
            );
            return "SIF_HMACSHA256 " + base64Credentials;

        } catch (Exception e) {
            throw new RuntimeException("Failed to build truncated HMAC auth header", e);
        }
    }
}
