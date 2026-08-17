// SPDX-License-Identifier: MIT
package org.sifunity.integration.security;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;
import org.sifunity.integration.config.AdminCredentialsConfig;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Gateway internal API endpoint blocking.
 *
 * <p>Verifies that the Gateway's {@code InternalApiBlockingFilter} rejects all
 * requests to {@code /internal/**} paths with HTTP 403 Forbidden, regardless
 * of authentication status. The filter runs at Order -1 (before authentication),
 * so even valid credentials cannot access internal endpoints.
 *
 * <p>Internal endpoints like {@code /internal/validate-token} and
 * {@code /internal/check-permission} are used for inter-service communication
 * and must never be accessible through the Gateway.
 *
 * <p><strong>Validates: Requirements 8.1, 8.2, 8.3</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("security")
@Tag("internal-api")
@DisplayName("Security — Internal API Blocking Tests")
public class InternalApiBlockingTest extends ServiceEnabledIntegrationTestBase {

    /**
     * Tests that a request to {@code /internal/validate-token} is blocked with 403.
     *
     * <p>The {@code /internal/validate-token} endpoint is used by the Gateway
     * internally to validate session tokens against the Environment Service.
     * It must not be accessible through external requests.</p>
     *
     * <p><strong>Validates: Requirement 8.1</strong></p>
     */
    @Test
    @DisplayName("/internal/validate-token → 403 Forbidden")
    public void testInternalValidateToken_Returns403() {
        log.info("Starting test: /internal/validate-token → 403");

        Response response = given()
            .contentType("application/json")
            .accept("application/json")
            .header("timestamp", Instant.now().toString())
            .header("messageId", UUID.randomUUID().toString())
        .when()
            .post("/internal/validate-token")
        .then()
            .log().ifValidationFails()
            .extract().response();

        log.info("Response status (/internal/validate-token): {}", response.getStatusCode());

        assertThat(response.getStatusCode())
            .as("Gateway should return 403 for /internal/validate-token")
            .isEqualTo(403);
    }

    /**
     * Tests that a request to {@code /internal/check-permission} is blocked with 403.
     *
     * <p>The {@code /internal/check-permission} endpoint is used by the Gateway
     * internally to check ACL permissions against the Environment Service.
     * It must not be accessible through external requests.</p>
     *
     * <p><strong>Validates: Requirement 8.2</strong></p>
     */
    @Test
    @DisplayName("/internal/check-permission → 403 Forbidden")
    public void testInternalCheckPermission_Returns403() {
        log.info("Starting test: /internal/check-permission → 403");

        Response response = given()
            .contentType("application/json")
            .accept("application/json")
            .header("timestamp", Instant.now().toString())
            .header("messageId", UUID.randomUUID().toString())
        .when()
            .post("/internal/check-permission")
        .then()
            .log().ifValidationFails()
            .extract().response();

        log.info("Response status (/internal/check-permission): {}", response.getStatusCode());

        assertThat(response.getStatusCode())
            .as("Gateway should return 403 for /internal/check-permission")
            .isEqualTo(403);
    }

    /**
     * Tests that an arbitrary {@code /internal/**} path is blocked with 403
     * even without authentication credentials.
     *
     * <p>The {@code InternalApiBlockingFilter} runs at Order -1, before the
     * authentication filter. This means internal paths are blocked before
     * any credential validation occurs.</p>
     *
     * <p><strong>Validates: Requirement 8.3</strong></p>
     */
    @Test
    @DisplayName("Arbitrary /internal/** path without auth → 403 Forbidden")
    public void testArbitraryInternalPath_NoAuth_Returns403() {
        log.info("Starting test: arbitrary /internal/** path without auth → 403");

        Response response = given()
            .contentType("application/json")
            .accept("application/json")
            .header("timestamp", Instant.now().toString())
            .header("messageId", UUID.randomUUID().toString())
        .when()
            .get("/internal/some-random-path")
        .then()
            .log().ifValidationFails()
            .extract().response();

        log.info("Response status (/internal/some-random-path, no auth): {}", response.getStatusCode());

        assertThat(response.getStatusCode())
            .as("Gateway should return 403 for arbitrary /internal/** path without auth")
            .isEqualTo(403);
    }

    /**
     * Tests that an arbitrary {@code /internal/**} path is blocked with 403
     * even with valid admin authentication credentials.
     *
     * <p>The {@code InternalApiBlockingFilter} runs at Order -1, before the
     * authentication filter (Order 1). Even fully authenticated admin requests
     * must be rejected for internal paths.</p>
     *
     * <p><strong>Validates: Requirement 8.3</strong></p>
     */
    @Test
    @DisplayName("Arbitrary /internal/** path with admin auth → 403 Forbidden")
    public void testArbitraryInternalPath_WithAuth_Returns403() {
        log.info("Starting test: arbitrary /internal/** path with admin auth → 403");

        AdminCredentialsConfig adminConfig = new AdminCredentialsConfig();
        String timestamp = Instant.now().toString();
        String authHeader = generateAuthHeader(
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret(),
            timestamp
        );

        Response response = given()
            .contentType("application/json")
            .accept("application/json")
            .header("Authorization", authHeader)
            .header("timestamp", timestamp)
            .header("messageId", UUID.randomUUID().toString())
        .when()
            .get("/internal/some-random-path")
        .then()
            .log().ifValidationFails()
            .extract().response();

        log.info("Response status (/internal/some-random-path, with auth): {}", response.getStatusCode());

        assertThat(response.getStatusCode())
            .as("Gateway should return 403 for arbitrary /internal/** path even with valid auth")
            .isEqualTo(403);
    }
}
