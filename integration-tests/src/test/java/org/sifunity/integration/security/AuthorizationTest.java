// SPDX-License-Identifier: MIT
package org.sifunity.integration.security;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;
import org.sifunity.integration.config.AdminCredentialsConfig;
import org.sifunity.integration.dto.EnvironmentRegistrationResponse;
import org.sifunity.integration.generator.AclGenerator;
import org.sifunity.integration.generator.EnvironmentGenerator;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Gateway authorization enforcement.
 *
 * <p>Verifies that authenticated requests without sufficient ACL permissions
 * are rejected with HTTP 403 Forbidden. Tests cover three scenarios:
 * <ol>
 *   <li>No PROVIDE ACL — consumer has no ACL for the service</li>
 *   <li>REJECTED ACL — consumer has an explicitly rejected ACL entry</li>
 *   <li>PENDING ACL — consumer has a pending (REQUESTED) ACL entry</li>
 * </ol>
 *
 * <p><strong>Validates: Requirements 6.1, 6.2, 6.3</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("security")
@Tag("authorization")
@DisplayName("Security — Authorization Rejection Tests")
public class AuthorizationTest extends ServiceEnabledIntegrationTestBase {

    private static final String SERVICE_NAME = "StudentPersonals";
    private static final String ZONE_ID = "test-zone";
    private static final String CONTEXT_ID = "DEFAULT";

    private EnvironmentGenerator environmentGenerator;
    private AclGenerator aclGenerator;
    private AdminCredentialsConfig adminConfig;

    private EnvironmentRegistrationResponse consumerEnvironment;
    private AclEntryDTO rejectedAcl;
    private AclEntryDTO pendingAcl;

    /**
     * Sets up test data before each test.
     *
     * <p>Creates a consumer environment with valid credentials so requests
     * are authenticated. ACL entries are created per-test as needed.</p>
     */
    @BeforeEach
    @Override
    public void setupTest() {
        super.setupTest();

        log.info("Setting up Authorization test");

        adminConfig = new AdminCredentialsConfig();

        String environmentServiceUrl = getEnvironmentServiceUrl();
        environmentGenerator = new EnvironmentGenerator(environmentServiceUrl);
        aclGenerator = new AclGenerator(environmentServiceUrl, environmentGenerator);

        // Create consumer environment with valid credentials
        consumerEnvironment = environmentGenerator.createConsumer(
            environmentGenerator.generateUniqueName("authz-consumer")
        );
        log.info("Consumer environment created: id={}, sessionToken={}",
            consumerEnvironment.getId(), consumerEnvironment.getSessionToken());
    }

    /**
     * Cleans up test data after each test in reverse order:
     * ACLs → environment.
     */
    @AfterEach
    @Override
    public void teardownTest() {
        log.info("Tearing down Authorization test");

        try {
            // Delete ACL entries
            if (rejectedAcl != null) {
                aclGenerator.deleteAclEntry(
                    rejectedAcl.id(),
                    adminConfig.getSessionToken(),
                    adminConfig.getSharedSecret()
                );
            }
            if (pendingAcl != null) {
                aclGenerator.deleteAclEntry(
                    pendingAcl.id(),
                    adminConfig.getSessionToken(),
                    adminConfig.getSharedSecret()
                );
            }

            // Delete environment
            if (consumerEnvironment != null) {
                environmentGenerator.deleteEnvironment(UUID.fromString(consumerEnvironment.getId()));
            }
        } catch (Exception e) {
            log.error("Error during test cleanup", e);
        }

        super.teardownTest();
    }

    /**
     * Tests that an authenticated consumer without a PROVIDE ACL is rejected with 403
     * when accessing a provider-only endpoint.
     *
     * <p>The consumer has valid credentials (authenticated) but no ACL entry granting
     * PROVIDE access to the service. The Gateway should reject with 403 Forbidden.</p>
     *
     * <p><strong>Validates: Requirement 6.1</strong></p>
     */
    @Test
    @DisplayName("Authenticated consumer without PROVIDE ACL → 403 Forbidden")
    public void testNoProvideAcl_Returns403() {
        log.info("Starting test: no PROVIDE ACL → 403");

        // Consumer has no ACL at all — send request to provider-only endpoint (events)
        Response response = given()
            .spec(sifRequest(
                consumerEnvironment.getSessionToken(),
                consumerEnvironment.getSharedSecret(),
                ZONE_ID,
                CONTEXT_ID
            ))
            .header("serviceName", SERVICE_NAME)
            .body("{\"event\": \"test\"}")
        .when()
            .post(gatewayPathPrefix() + "/events")
        .then()
            .log().ifValidationFails()
            .extract().response();

        log.info("Response status (no PROVIDE ACL): {}", response.getStatusCode());

        assertThat(response.getStatusCode())
            .as("Gateway should return 403 when consumer has no PROVIDE ACL")
            .isEqualTo(403);
    }

    /**
     * Tests that an authenticated consumer with a REJECTED ACL entry is rejected with 403.
     *
     * <p>The consumer has valid credentials and an ACL entry for the service, but the
     * access level is REJECTED. The Gateway should reject with 403 Forbidden.</p>
     *
     * <p><strong>Validates: Requirement 6.2</strong></p>
     */
    @Test
    @DisplayName("Authenticated consumer with REJECTED ACL → 403 Forbidden")
    public void testRejectedAcl_Returns403() {
        log.info("Starting test: REJECTED ACL → 403");

        // Create ACL with REJECTED access level for PROVIDE
        rejectedAcl = aclGenerator.createRejectedPermission(
            UUID.fromString(consumerEnvironment.getId()),
            SERVICE_NAME,
            null, null, // wildcard zone/context
            AccessType.PROVIDE,
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("REJECTED ACL created: id={}", rejectedAcl.id());

        // Send request to provider-only endpoint
        Response response = given()
            .spec(sifRequest(
                consumerEnvironment.getSessionToken(),
                consumerEnvironment.getSharedSecret(),
                ZONE_ID,
                CONTEXT_ID
            ))
            .header("serviceName", SERVICE_NAME)
            .body("{\"event\": \"test\"}")
        .when()
            .post(gatewayPathPrefix() + "/events")
        .then()
            .log().ifValidationFails()
            .extract().response();

        log.info("Response status (REJECTED ACL): {}", response.getStatusCode());

        assertThat(response.getStatusCode())
            .as("Gateway should return 403 when consumer has REJECTED ACL")
            .isEqualTo(403);
    }

    /**
     * Tests that an authenticated consumer with a PENDING (REQUESTED) ACL entry
     * is rejected with 403.
     *
     * <p>The consumer has valid credentials and an ACL entry for the service, but the
     * access level is REQUESTED (pending approval). The Gateway should reject with
     * 403 Forbidden.</p>
     *
     * <p><strong>Validates: Requirement 6.3</strong></p>
     */
    @Test
    @DisplayName("Authenticated consumer with PENDING ACL → 403 Forbidden")
    public void testPendingAcl_Returns403() {
        log.info("Starting test: PENDING ACL → 403");

        // Create ACL with REQUESTED (pending) access level for PROVIDE
        pendingAcl = aclGenerator.createPendingPermission(
            UUID.fromString(consumerEnvironment.getId()),
            SERVICE_NAME,
            null, null, // wildcard zone/context
            AccessType.PROVIDE,
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("PENDING ACL created: id={}", pendingAcl.id());

        // Send request to provider-only endpoint
        Response response = given()
            .spec(sifRequest(
                consumerEnvironment.getSessionToken(),
                consumerEnvironment.getSharedSecret(),
                ZONE_ID,
                CONTEXT_ID
            ))
            .header("serviceName", SERVICE_NAME)
            .body("{\"event\": \"test\"}")
        .when()
            .post(gatewayPathPrefix() + "/events")
        .then()
            .log().ifValidationFails()
            .extract().response();

        log.info("Response status (PENDING ACL): {}", response.getStatusCode());

        assertThat(response.getStatusCode())
            .as("Gateway should return 403 when consumer has PENDING ACL")
            .isEqualTo(403);
    }
}
