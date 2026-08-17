// SPDX-License-Identifier: MIT
package org.sifunity.integration.tenant;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;
import org.sifunity.integration.config.AdminCredentialsConfig;
import org.sifunity.integration.dto.DomainResponse;
import org.sifunity.integration.dto.EnvironmentRegistrationResponse;
import org.sifunity.integration.dto.SolutionResponse;
import org.sifunity.integration.generator.DomainGenerator;
import org.sifunity.integration.generator.EnvironmentGenerator;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for multi-tenant data isolation between domains.
 *
 * <p>Verifies that data created under one domain is not visible when querying
 * under a different domain, and that environments registered under one domain
 * cannot access resources under another domain's path.</p>
 *
 * <p><strong>Validates: Requirements 9.1, 9.2, 9.3</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("multi-tenant")
@Tag("isolation")
@DisplayName("Tenant Isolation — Domain Data Not Visible Across Tenants")
public class TenantIsolationTest extends ServiceEnabledIntegrationTestBase {

    private static final String ZONE_ID = "test-zone";
    private static final String CONTEXT_ID = "DEFAULT";

    private DomainGenerator domainGenerator;
    private EnvironmentGenerator environmentGenerator;

    private String domainAId;
    private String domainBId;
    private String solutionAId;
    private String solutionBId;
    private DomainResponse domainA;
    private DomainResponse domainB;
    private SolutionResponse solutionA;
    private SolutionResponse solutionB;
    private EnvironmentRegistrationResponse domainAEnvironment;

    /**
     * Sets up test data before each test.
     *
     * <ol>
     *   <li>Initializes generators</li>
     *   <li>Creates Domain A with a solution</li>
     *   <li>Creates Domain B with a solution</li>
     * </ol>
     */
    @BeforeEach
    @Override
    public void setupTest() {
        super.setupTest();

        log.info("Setting up Tenant Isolation test");

        String environmentServiceUrl = getEnvironmentServiceUrl();
        domainGenerator = new DomainGenerator(environmentServiceUrl);
        environmentGenerator = new EnvironmentGenerator(environmentServiceUrl);

        // Generate unique IDs to avoid collisions
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        domainAId = "domain-a-" + uniqueSuffix;
        domainBId = "domain-b-" + uniqueSuffix;
        solutionAId = "solution-a-" + uniqueSuffix;
        solutionBId = "solution-b-" + uniqueSuffix;

        // Create Domain A with a solution
        domainA = domainGenerator.createDomain(domainAId, "Tenant Isolation Test - Domain A");
        log.info("Domain A created: domainId={}", domainA.domainId());

        solutionA = domainGenerator.createSolution(domainAId, solutionAId, "Domain A Solution");
        log.info("Solution A created: solutionId={} in domain={}", solutionA.solutionId(), domainAId);

        // Create Domain B with a solution
        domainB = domainGenerator.createDomain(domainBId, "Tenant Isolation Test - Domain B");
        log.info("Domain B created: domainId={}", domainB.domainId());

        solutionB = domainGenerator.createSolution(domainBId, solutionBId, "Domain B Solution");
        log.info("Solution B created: solutionId={} in domain={}", solutionB.solutionId(), domainBId);

        // Create an environment under Domain A's solution (via ADMIN credentials)
        domainAEnvironment = createEnvironmentUnderDomain(
            domainAId, solutionAId,
            "tenant-iso-env-" + uniqueSuffix
        );
        log.info("Domain A environment created: id={}, sessionToken={}",
            domainAEnvironment.getId(), domainAEnvironment.getSessionToken());
    }

    /**
     * Cleans up test data after each test.
     * Deletes Domain B first, then Domain A (cascade cleans children).
     */
    @AfterEach
    @Override
    public void teardownTest() {
        log.info("Tearing down Tenant Isolation test");

        try {
            // Delete environment under Domain A
            if (domainAEnvironment != null) {
                deleteEnvironmentUnderDomain(
                    domainAId, solutionAId,
                    UUID.fromString(domainAEnvironment.getId())
                );
            }

            // Delete Domain B first (cascade cleans children)
            if (domainB != null) {
                domainGenerator.deleteDomain(domainBId);
                log.info("Domain B deleted: {}", domainBId);
            }

            // Delete Domain A (cascade cleans children)
            if (domainA != null) {
                domainGenerator.deleteDomain(domainAId);
                log.info("Domain A deleted: {}", domainAId);
            }
        } catch (Exception e) {
            log.error("Error during test cleanup", e);
        }

        super.teardownTest();
    }

    /**
     * Tests that a solution created under Domain A is visible when querying Domain A
     * but NOT visible when querying Domain B.
     *
     * <p><strong>Validates: Requirements 9.1, 9.2</strong></p>
     *
     * <p>Test scenario:
     * <ol>
     *   <li>Create a solution under Domain A (done in @BeforeEach)</li>
     *   <li>Query solutions under Domain A → assert solution A is present</li>
     *   <li>Query solutions under Domain B → assert solution A is NOT present</li>
     * </ol>
     */
    @Test
    @DisplayName("Solution created under Domain A is not visible when querying Domain B")
    public void testDataIsolation_SolutionNotVisibleAcrossDomains() {
        log.info("Starting test: Data Isolation — solution not visible across domains");

        // When: Query solutions under Domain A
        List<SolutionResponse> domainASolutions = domainGenerator.listSolutions(domainAId);

        // Then: Solution A is present under Domain A (Requirement 9.1)
        assertThat(domainASolutions)
            .as("Domain A should contain solution A")
            .extracting(SolutionResponse::solutionId)
            .contains(solutionAId);

        log.info("Domain A solutions: {}", domainASolutions.stream()
            .map(SolutionResponse::solutionId).toList());

        // When: Query solutions under Domain B
        List<SolutionResponse> domainBSolutions = domainGenerator.listSolutions(domainBId);

        // Then: Solution A is NOT present under Domain B (Requirement 9.2)
        assertThat(domainBSolutions)
            .as("Domain B should NOT contain Domain A's solution")
            .extracting(SolutionResponse::solutionId)
            .doesNotContain(solutionAId);

        // Domain B should only contain its own solution
        assertThat(domainBSolutions)
            .as("Domain B should contain only its own solution")
            .extracting(SolutionResponse::solutionId)
            .contains(solutionBId);

        log.info("Domain B solutions: {}", domainBSolutions.stream()
            .map(SolutionResponse::solutionId).toList());

        log.info("Test completed: Data Isolation — solution not visible across domains");
    }

    /**
     * Tests that an environment registered under Domain A is rejected when
     * attempting to access resources under Domain B's path through the Gateway.
     *
     * <p><strong>Validates: Requirement 9.3</strong></p>
     *
     * <p>Test scenario:
     * <ol>
     *   <li>Environment registered under Domain A (done in @BeforeEach)</li>
     *   <li>Environment attempts to access Domain B's resource path via Gateway</li>
     *   <li>Assert Gateway returns 403 Forbidden (membership validation failure)</li>
     * </ol>
     */
    @Test
    @DisplayName("Environment under Domain A is rejected when accessing Domain B's resources")
    public void testCrossDomainAccessRejection() {
        log.info("Starting test: Cross-Domain Access Rejection");

        // When: Domain A's environment attempts to access Domain B's resource path
        Response response = given()
            .spec(sifRequest(
                domainAEnvironment.getSessionToken(),
                domainAEnvironment.getSharedSecret(),
                ZONE_ID,
                CONTEXT_ID
            ))
        .when()
            .get("/" + domainBId + "/" + solutionBId + "/requests")
        .then()
            .log().ifValidationFails()
            .extract().response();

        log.info("Cross-domain access response: status={}, body={}",
            response.getStatusCode(), response.getBody().asString());

        // Then: Gateway rejects with 403 Forbidden (Requirement 9.3)
        assertThat(response.getStatusCode())
            .as("Gateway should reject cross-domain access with 403 Forbidden")
            .isEqualTo(403);

        log.info("Test completed: Cross-Domain Access Rejection");
    }

    // ── Helper Methods ──────────────────────────────────────────────────

    /**
     * Creates an environment under a specific domain/solution path using ADMIN credentials.
     * The EnvironmentGenerator always creates under GLOBAL_DOMAIN/GLOBAL_SOLUTION,
     * so this method calls the Environment Service directly with the target domain/solution path.
     *
     * @param domainId        the target domain ID
     * @param solutionId      the target solution ID
     * @param applicationName the application name for the environment
     * @return the environment registration response with credentials
     */
    private EnvironmentRegistrationResponse createEnvironmentUnderDomain(
        String domainId, String solutionId, String applicationName
    ) {
        AdminCredentialsConfig adminConfig = new AdminCredentialsConfig();
        String environmentServiceUrl = getEnvironmentServiceUrl();

        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

        String url = environmentServiceUrl + "/" + domainId + "/" + solutionId + "/environments/environment";

        java.time.Instant timestamp = java.time.Instant.now();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", environmentGenerator.generateAuthHeader(
            adminConfig.getSessionToken(), adminConfig.getSharedSecret(), timestamp));
        headers.set("timestamp", timestamp.toString());
        headers.set("Content-Type", "application/json");

        // D2: wrapped under `environment`, applicationName -> consumerName, defaultZoneId ->
        // defaultZone{id}. DefaultZone.of() keeps a null zone absent rather than {"id": null} (D3).
        org.sifunity.integration.dto.EnvironmentRegistrationRequest request =
            new org.sifunity.integration.dto.EnvironmentRegistrationRequest(
                new org.sifunity.integration.dto.EnvironmentRegistrationBody(
                    applicationName,
                    EnvironmentType.BROKERED,
                    org.sifunity.integration.dto.EnvironmentRegistrationBody.DefaultZone.of(ZONE_ID),
                    null
                )
            );

        org.springframework.http.HttpEntity<org.sifunity.integration.dto.EnvironmentRegistrationRequest> entity =
            new org.springframework.http.HttpEntity<>(request, headers);

        // D2: the endpoint returns the `environment` envelope, not a flat body.
        org.springframework.http.ResponseEntity<org.sifunity.integration.dto.EnvironmentRegistrationEnvelope> response =
            restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.POST,
                entity,
                org.sifunity.integration.dto.EnvironmentRegistrationEnvelope.class
            );

        return response.getBody() == null ? null : response.getBody().getEnvironment();
    }

    /**
     * Deletes an environment under a specific domain/solution path using ADMIN credentials.
     *
     * @param domainId      the domain ID
     * @param solutionId    the solution ID
     * @param environmentId the environment UUID to delete
     */
    private void deleteEnvironmentUnderDomain(String domainId, String solutionId, UUID environmentId) {
        AdminCredentialsConfig adminConfig = new AdminCredentialsConfig();
        String environmentServiceUrl = getEnvironmentServiceUrl();

        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

        String url = environmentServiceUrl + "/" + domainId + "/" + solutionId
            + "/environments/" + environmentId;

        java.time.Instant timestamp = java.time.Instant.now();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", environmentGenerator.generateAuthHeader(
            adminConfig.getSessionToken(), adminConfig.getSharedSecret(), timestamp));
        headers.set("timestamp", timestamp.toString());

        org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);

        restTemplate.exchange(url, org.springframework.http.HttpMethod.DELETE, entity, Void.class);

        log.info("Environment deleted: {} under {}/{}", environmentId, domainId, solutionId);
    }
}
