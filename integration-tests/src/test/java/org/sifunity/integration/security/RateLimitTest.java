// SPDX-License-Identifier: MIT
package org.sifunity.integration.security;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;
import org.sifunity.integration.config.AdminCredentialsConfig;
import org.sifunity.integration.dto.EnvironmentRegistrationResponse;
import org.sifunity.integration.generator.AclGenerator;
import org.sifunity.integration.generator.EnvironmentGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Gateway rate limiting enforcement.
 *
 * <p>Verifies that the Gateway enforces per-environment rate limits using
 * a Redis-backed sliding window algorithm. The configured rate limit is
 * 100 requests per second per environment. Tests cover two scenarios:
 * <ol>
 *   <li>Burst exceeding limit — rapid requests beyond the limit trigger 429</li>
 *   <li>Window reset — after the 1-second window expires, requests are accepted again</li>
 * </ol>
 *
 * <p><strong>Validates: Requirements 7.1, 7.2</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("security")
@Tag("rate-limit")
@DisplayName("Security — Rate Limit Enforcement Tests")
public class RateLimitTest extends ServiceEnabledIntegrationTestBase {

    private static final String SERVICE_NAME = "StudentPersonals";
    private static final String ZONE_ID = "test-zone";
    private static final String CONTEXT_ID = "DEFAULT";

    /**
     * Number of requests to send in the burst test. Must exceed the configured
     * rate limit of 100 requests per second to trigger at least one 429 response.
     */
    private static final int BURST_SIZE = 150;

    private EnvironmentGenerator environmentGenerator;
    private AclGenerator aclGenerator;
    private AdminCredentialsConfig adminConfig;

    private EnvironmentRegistrationResponse consumerEnvironment;
    private AclEntryDTO consumerAcl;

    /**
     * Sets up test data before each test.
     *
     * <p>Creates a consumer environment with valid credentials and a CONSUME ACL
     * so requests are authenticated and authorized. This ensures rate limiting
     * is the only filter that can reject requests.</p>
     */
    @BeforeEach
    @Override
    public void setupTest() {
        super.setupTest();

        log.info("Setting up Rate Limit test");

        adminConfig = new AdminCredentialsConfig();

        String environmentServiceUrl = getEnvironmentServiceUrl();
        environmentGenerator = new EnvironmentGenerator(environmentServiceUrl);
        aclGenerator = new AclGenerator(environmentServiceUrl, environmentGenerator);

        // Create consumer environment with valid credentials
        consumerEnvironment = environmentGenerator.createConsumer(
            environmentGenerator.generateUniqueName("ratelimit-consumer")
        );
        log.info("Consumer environment created: id={}, sessionToken={}",
            consumerEnvironment.getId(), consumerEnvironment.getSessionToken());

        // Create CONSUME ACL so requests pass authorization
        consumerAcl = aclGenerator.createConsumePermission(
            UUID.fromString(consumerEnvironment.getId()),
            SERVICE_NAME,
            null, null, // wildcard zone/context
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Consumer ACL created: id={}", consumerAcl.id());
    }

    /**
     * Cleans up test data after each test in reverse order:
     * ACL → environment.
     */
    @AfterEach
    @Override
    public void teardownTest() {
        log.info("Tearing down Rate Limit test");

        try {
            if (consumerAcl != null) {
                aclGenerator.deleteAclEntry(
                    consumerAcl.id(),
                    adminConfig.getSessionToken(),
                    adminConfig.getSharedSecret()
                );
            }

            if (consumerEnvironment != null) {
                environmentGenerator.deleteEnvironment(UUID.fromString(consumerEnvironment.getId()));
            }
        } catch (Exception e) {
            log.error("Error during test cleanup", e);
        }

        super.teardownTest();
    }

    /**
     * Tests that sending requests in rapid succession exceeding the configured
     * rate limit results in at least one HTTP 429 Too Many Requests response.
     *
     * <p>The Gateway is configured with a rate limit of 100 requests per second
     * per environment using a 1-second sliding window. This test sends 150
     * requests as fast as possible and asserts that at least one is rejected
     * with 429.</p>
     *
     * <p><strong>Validates: Requirement 7.1</strong></p>
     */
    @Test
    @DisplayName("Burst exceeding rate limit → at least one 429 Too Many Requests")
    public void testBurstExceedingLimit_Returns429() {
        log.info("Starting test: burst exceeding rate limit → 429");

        List<Integer> statusCodes = new ArrayList<>();

        // Send requests in rapid succession exceeding the configured rate limit
        for (int i = 0; i < BURST_SIZE; i++) {
            Response response = given()
                .spec(sifRequest(
                    consumerEnvironment.getSessionToken(),
                    consumerEnvironment.getSharedSecret(),
                    ZONE_ID,
                    CONTEXT_ID
                ))
                .header("serviceName", SERVICE_NAME)
            .when()
                .get(gatewayPathPrefix() + "/requests/StudentPersonals")
            .then()
                .extract().response();

            statusCodes.add(response.getStatusCode());
        }

        long count429 = statusCodes.stream().filter(code -> code == 429).count();
        log.info("Burst test results: total={}, 429s={}, other={}",
            statusCodes.size(), count429, statusCodes.size() - count429);

        assertThat(count429)
            .as("At least one request should be rate-limited with 429 when burst exceeds configured limit")
            .isGreaterThan(0);
    }

    /**
     * Tests that after the rate limit window resets, requests are accepted again.
     *
     * <p>First triggers rate limiting by sending a burst of requests, then waits
     * for the 1-second sliding window to expire, and finally sends a new request
     * that should be accepted (not 429).</p>
     *
     * <p><strong>Validates: Requirement 7.2</strong></p>
     */
    @Test
    @DisplayName("After rate limit window resets → request accepted (not 429)")
    public void testWindowReset_RequestAccepted() {
        log.info("Starting test: window reset → request accepted");

        // First, trigger rate limiting by sending a burst
        int rateLimitTriggered = 0;
        for (int i = 0; i < BURST_SIZE; i++) {
            Response response = given()
                .spec(sifRequest(
                    consumerEnvironment.getSessionToken(),
                    consumerEnvironment.getSharedSecret(),
                    ZONE_ID,
                    CONTEXT_ID
                ))
                .header("serviceName", SERVICE_NAME)
            .when()
                .get(gatewayPathPrefix() + "/requests/StudentPersonals")
            .then()
                .extract().response();

            if (response.getStatusCode() == 429) {
                rateLimitTriggered++;
            }
        }

        log.info("Pre-reset burst: {} requests rate-limited out of {}", rateLimitTriggered, BURST_SIZE);

        assertThat(rateLimitTriggered)
            .as("Burst should trigger rate limiting before testing window reset")
            .isGreaterThan(0);

        // Wait for the rate limit window to reset (1-second window + buffer)
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("Rate limit window should have reset, sending new request");

        // Send a single request after the window resets
        Response response = given()
            .spec(sifRequest(
                consumerEnvironment.getSessionToken(),
                consumerEnvironment.getSharedSecret(),
                ZONE_ID,
                CONTEXT_ID
            ))
            .header("serviceName", SERVICE_NAME)
        .when()
            .get(gatewayPathPrefix() + "/requests/StudentPersonals")
        .then()
            .log().ifValidationFails()
            .extract().response();

        log.info("Post-reset response status: {}", response.getStatusCode());

        assertThat(response.getStatusCode())
            .as("Gateway should accept requests after rate limit window resets (not 429)")
            .isNotEqualTo(429);
    }
}
