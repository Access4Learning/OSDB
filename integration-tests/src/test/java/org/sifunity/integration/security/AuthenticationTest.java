// SPDX-License-Identifier: MIT
package org.sifunity.integration.security;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;
import org.sifunity.integration.config.AdminCredentialsConfig;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Gateway authentication enforcement.
 *
 * <p>Verifies that the Gateway rejects requests with missing, invalid,
 * or expired authentication credentials. No environment setup is needed
 * since these tests validate rejection of unauthenticated requests.
 *
 * <p><strong>Validates: Requirements 5.1, 5.2, 5.3</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("security")
@Tag("authentication")
@DisplayName("Security — Authentication Rejection Tests")
public class AuthenticationTest extends ServiceEnabledIntegrationTestBase {

    private static final String TEST_ENDPOINT = "/environments";

    /**
     * Tests that a request without an Authorization header is rejected with 401.
     *
     * <p><strong>Validates: Requirement 5.1</strong></p>
     */
    @Test
    @DisplayName("Request without Authorization header → 401 Unauthorized")
    public void testNoAuthHeader_Returns401() {
        log.info("Starting test: no Authorization header → 401");

        Response response = given()
            .contentType("application/json")
            .accept("application/json")
            .header("timestamp", Instant.now().toString())
            .header("messageId", UUID.randomUUID().toString())
        .when()
            .get(gatewayPathPrefix() + TEST_ENDPOINT)
        .then()
            .log().ifValidationFails()
            .extract().response();

        log.info("Response status (no auth header): {}", response.getStatusCode());

        assertThat(response.getStatusCode())
            .as("Gateway should return 401 when Authorization header is missing")
            .isEqualTo(401);
    }

    /**
     * Tests that a request with a corrupted HMAC signature is rejected with 401.
     *
     * <p>Generates a valid-looking auth header using admin credentials but
     * corrupts the HMAC signature so it won't match server-side verification.</p>
     *
     * <p><strong>Validates: Requirement 5.2</strong></p>
     */
    @Test
    @DisplayName("Request with invalid HMAC signature → 401 Unauthorized")
    public void testInvalidHmac_Returns401() {
        log.info("Starting test: invalid HMAC signature → 401");

        AdminCredentialsConfig adminConfig = new AdminCredentialsConfig();
        String sessionToken = adminConfig.getSessionToken();
        String timestamp = Instant.now().toString();

        // Build auth header with corrupted HMAC — use a fake signature
        String corruptedHmac = Base64.getEncoder().encodeToString(
            "this-is-a-corrupted-hmac-signature".getBytes(StandardCharsets.UTF_8)
        );
        String credentials = sessionToken + ":" + corruptedHmac;
        String base64Credentials = Base64.getEncoder().encodeToString(
            credentials.getBytes(StandardCharsets.UTF_8)
        );
        String authHeader = "SIF_HMACSHA256 " + base64Credentials;

        Response response = given()
            .contentType("application/json")
            .accept("application/json")
            .header("Authorization", authHeader)
            .header("timestamp", timestamp)
            .header("messageId", UUID.randomUUID().toString())
        .when()
            .get(gatewayPathPrefix() + TEST_ENDPOINT)
        .then()
            .log().ifValidationFails()
            .extract().response();

        log.info("Response status (invalid HMAC): {}", response.getStatusCode());

        assertThat(response.getStatusCode())
            .as("Gateway should return 401 when HMAC signature is invalid")
            .isEqualTo(401);
    }

    /**
     * Tests that a request with an expired timestamp is rejected with 401.
     *
     * <p>Generates a valid HMAC signature using admin credentials but with
     * a timestamp set 1 hour in the past, exceeding the Gateway's timestamp
     * tolerance window.</p>
     *
     * <p><strong>Validates: Requirement 5.3</strong></p>
     */
    @Test
    @DisplayName("Request with expired timestamp → 401 Unauthorized")
    public void testExpiredTimestamp_Returns401() {
        log.info("Starting test: expired timestamp → 401");

        AdminCredentialsConfig adminConfig = new AdminCredentialsConfig();
        String sessionToken = adminConfig.getSessionToken();
        String sharedSecret = adminConfig.getSharedSecret();

        // Use timestamp 1 hour in the past
        String expiredTimestamp = Instant.now().minus(1, ChronoUnit.HOURS).toString();

        // Generate valid HMAC with the expired timestamp
        String authHeader = generateAuthHeader(sessionToken, sharedSecret, expiredTimestamp);

        Response response = given()
            .contentType("application/json")
            .accept("application/json")
            .header("Authorization", authHeader)
            .header("timestamp", expiredTimestamp)
            .header("messageId", UUID.randomUUID().toString())
        .when()
            .get(gatewayPathPrefix() + TEST_ENDPOINT)
        .then()
            .log().ifValidationFails()
            .extract().response();

        log.info("Response status (expired timestamp): {}", response.getStatusCode());

        assertThat(response.getStatusCode())
            .as("Gateway should return 401 when timestamp is expired")
            .isEqualTo(401);
    }
}
