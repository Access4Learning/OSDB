// SPDX-License-Identifier: MIT
package org.sifunity.integration.security;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
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
 * Property-based test for rate limit enforcement.
 *
 * <p><strong>Property 4: Rate limit enforcement</strong></p>
 * <p>For any authenticated environment, when the number of requests in a
 * rate-limit window exceeds the configured limit, all subsequent requests
 * within that window SHALL receive HTTP 429 Too Many Requests.</p>
 *
 * <p>The configured rate limit is 100 requests per second per environment.
 * Each property iteration generates a random burst size above the limit
 * (110–200 requests), creates a FRESH environment (to avoid rate limit
 * state from previous iterations), sends the burst, and asserts that at
 * least one response is 429.</p>
 *
 * <p>Infrastructure (containers, services) is started once via lazy
 * initialization. A fresh environment is created per iteration because
 * rate limit state is per-environment.</p>
 *
 * <p><strong>Validates: Requirements 7.1</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("security")
@Tag("Feature: cross-service-integration-tests, Property 4: Rate limit enforcement")
public class RateLimitEnforcementPropertyTest extends ServiceEnabledIntegrationTestBase {

    private static final String SERVICE_NAME = "StudentPersonals";
    private static final String ZONE_ID = "test-zone";
    private static final String CONTEXT_ID = "DEFAULT";

    /**
     * Configured rate limit: 100 requests per second per environment.
     */
    private static final int CONFIGURED_RATE_LIMIT = 100;

    private boolean infrastructureReady = false;
    private AdminCredentialsConfig adminConfig;
    private EnvironmentGenerator environmentGenerator;
    private AclGenerator aclGenerator;

    /**
     * Lazily starts infrastructure once before all property iterations.
     */
    private void ensureInfrastructure() {
        if (infrastructureReady) {
            return;
        }
        log.info("Setting up infrastructure for RateLimitEnforcementPropertyTest");
        setupInfrastructure();
        adminConfig = new AdminCredentialsConfig();
        String environmentServiceUrl = getEnvironmentServiceUrl();
        environmentGenerator = new EnvironmentGenerator(environmentServiceUrl);
        aclGenerator = new AclGenerator(environmentServiceUrl, environmentGenerator);
        infrastructureReady = true;
    }

    // ── Arbitraries ──────────────────────────────────────────────────

    /**
     * Generates random burst sizes above the configured rate limit (110–200).
     * This ensures every iteration exceeds the 100 req/s limit.
     */
    @Provide
    Arbitrary<Integer> burstSizes() {
        return Arbitraries.integers().between(CONFIGURED_RATE_LIMIT + 10, CONFIGURED_RATE_LIMIT + 100);
    }

    // ── Property ─────────────────────────────────────────────────────

    /**
     * <strong>Property 4: Rate limit enforcement</strong>
     *
     * <p>For any burst size above the configured rate limit, sending that many
     * requests in rapid succession from a single environment SHALL result in
     * at least one HTTP 429 Too Many Requests response.</p>
     *
     * <p>Each iteration creates a fresh environment to avoid rate limit state
     * leaking between iterations. A short sleep between iterations allows
     * rate limit windows to reset.</p>
     *
     * <p><strong>Validates: Requirements 7.1</strong></p>
     */
    @Property(tries = 10)
    @Tag("Feature: cross-service-integration-tests, Property 4: Rate limit enforcement")
    void rateLimitEnforcementForBurstAboveLimit(
        @ForAll("burstSizes") int burstSize
    ) {
        ensureInfrastructure();

        log.info("Property iteration: burstSize={}", burstSize);

        // Create a FRESH environment for this iteration (rate limit state is per-environment)
        EnvironmentRegistrationResponse environment = null;
        AclEntryDTO acl = null;

        try {
            environment = environmentGenerator.createConsumer(
                environmentGenerator.generateUniqueName("ratelimit-prop")
            );
            log.info("Fresh environment created: id={}, sessionToken={}",
                environment.getId(), environment.getSessionToken());

            // Create CONSUME ACL so requests pass authorization
            acl = aclGenerator.createConsumePermission(
                UUID.fromString(environment.getId()),
                SERVICE_NAME,
                null, null, // wildcard zone/context
                adminConfig.getSessionToken(),
                adminConfig.getSharedSecret()
            );
            log.info("CONSUME ACL created: id={}", acl.id());

            // Send burst of requests in rapid succession
            List<Integer> statusCodes = new ArrayList<>(burstSize);

            for (int i = 0; i < burstSize; i++) {
                Response response = given()
                    .spec(sifRequest(
                        environment.getSessionToken(),
                        environment.getSharedSecret(),
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
            log.info("Burst results: burstSize={}, total={}, 429s={}, other={}",
                burstSize, statusCodes.size(), count429, statusCodes.size() - count429);

            assertThat(count429)
                .as("At least one request should be rate-limited with 429 when burst size (%d) exceeds configured limit (%d)",
                    burstSize, CONFIGURED_RATE_LIMIT)
                .isGreaterThan(0);

        } finally {
            // Clean up: ACL → environment
            cleanupIteration(acl, environment);

            // Wait between iterations for rate limit windows to reset
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ── Cleanup helper ───────────────────────────────────────────────

    /**
     * Cleans up resources created during a single property iteration.
     *
     * @param acl         the ACL entry to delete (may be null)
     * @param environment the environment to delete (may be null)
     */
    private void cleanupIteration(AclEntryDTO acl, EnvironmentRegistrationResponse environment) {
        try {
            if (acl != null) {
                aclGenerator.deleteAclEntry(
                    acl.id(),
                    adminConfig.getSessionToken(),
                    adminConfig.getSharedSecret()
                );
                log.info("Cleaned up ACL: id={}", acl.id());
            }
        } catch (Exception e) {
            log.warn("Failed to clean up ACL", e);
        }

        try {
            if (environment != null) {
                environmentGenerator.deleteEnvironment(UUID.fromString(environment.getId()));
                log.info("Cleaned up environment: id={}", environment.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to clean up environment", e);
        }
    }
}
