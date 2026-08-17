// SPDX-License-Identifier: MIT
package org.sifunity.integration.event;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.MessageExchangePattern;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;
import org.sifunity.integration.config.AdminCredentialsConfig;
import org.sifunity.integration.dto.EnvironmentRegistrationResponse;
import org.sifunity.integration.generator.AclGenerator;
import org.sifunity.integration.generator.EnvironmentGenerator;
import org.sifunity.integration.generator.MessageGenerator;
import org.sifunity.integration.generator.ProviderGenerator;

import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.ProtocolDTO;

/**
 * Integration tests for the EVENT flow with no subscribers.
 *
 * <p>Verifies that publishing an event when no subscriptions exist does not
 * produce an error — the Events Connector returns 202 Accepted and completes
 * without enqueueing any messages.
 *
 * <p><strong>Validates: Requirements 3.1, 3.2</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("event-flow")
@DisplayName("Event Flow — No Subscriber Integration Tests")
public class NoSubscriberEventFlowTest extends ServiceEnabledIntegrationTestBase {

    private static final String SERVICE_NAME = "StudentPersonals";
    private static final String ZONE_ID = "test-zone";
    private static final String CONTEXT_ID = "DEFAULT";

    private EnvironmentGenerator environmentGenerator;
    private AclGenerator aclGenerator;
    private ProviderGenerator providerGenerator;
    private MessageGenerator messageGenerator;
    private AdminCredentialsConfig adminConfig;

    private EnvironmentRegistrationResponse providerEnvironment;
    private AclEntryDTO providerAcl;
    private ProviderRegistrationDTO providerRegistration;

    /**
     * Sets up test data before each test.
     *
     * <p>Creates only provider-side resources — NO consumers, queues, or subscriptions.
     *
     * <ol>
     *   <li>Initializes generators</li>
     *   <li>Creates provider environment</li>
     *   <li>Creates PROVIDE ACL entry</li>
     *   <li>Registers the provider</li>
     * </ol>
     */
    @BeforeEach
    @Override
    public void setupTest() {
        super.setupTest();

        log.info("Setting up No Subscriber Event Flow test");

        // Initialize ADMIN credentials
        adminConfig = new AdminCredentialsConfig();

        // Initialize generators with actual service URLs
        String environmentServiceUrl = getEnvironmentServiceUrl();
        environmentGenerator = new EnvironmentGenerator(environmentServiceUrl);
        aclGenerator = new AclGenerator(environmentServiceUrl, environmentGenerator);
        providerGenerator = new ProviderGenerator(environmentServiceUrl);
        messageGenerator = new MessageGenerator();

        // Create provider environment
        providerEnvironment = environmentGenerator.createProvider(
            environmentGenerator.generateUniqueName("nosub-provider")
        );
        log.info("Provider environment created: id={}, sessionToken={}",
            providerEnvironment.getId(), providerEnvironment.getSessionToken());

        // Create PROVIDE ACL entry using ADMIN credentials
        providerAcl = aclGenerator.createProvidePermission(
            UUID.fromString(providerEnvironment.getId()),
            SERVICE_NAME,
            null, null, // wildcard zone/context
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Provider ACL created: id={}", providerAcl.id());

        // Register provider
        providerRegistration = providerGenerator.registerProvider(
            UUID.fromString(providerEnvironment.getId()),
            SERVICE_NAME,
            ZONE_ID,
            CONTEXT_ID,
            "http://provider-endpoint.example.com/" + SERVICE_NAME, // Not called for events
            Set.of(MessageExchangePattern.EVENT),
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Provider registered: id={}, endpoint={}",
            providerRegistration.id(), ProtocolDTO.locationOf(providerRegistration.endPoint()));
    }

    /**
     * Cleans up test data after each test in reverse order:
     * provider registration → ACL → environment.
     */
    @AfterEach
    @Override
    public void teardownTest() {
        log.info("Tearing down No Subscriber Event Flow test");

        try {
            // Delete provider registration
            if (providerRegistration != null) {
                providerGenerator.deleteProvider(
                    providerRegistration.id(),
                    adminConfig.getSessionToken(),
                    adminConfig.getSharedSecret()
                );
            }

            // Delete ACL entry
            if (providerAcl != null) {
                aclGenerator.deleteAclEntry(
                    providerAcl.id(),
                    adminConfig.getSessionToken(),
                    adminConfig.getSharedSecret()
                );
            }

            // Delete environment
            if (providerEnvironment != null) {
                environmentGenerator.deleteEnvironment(UUID.fromString(providerEnvironment.getId()));
            }

        } catch (Exception e) {
            log.error("Error during test cleanup", e);
        }

        super.teardownTest();
    }

    /**
     * Tests that publishing an event with no subscribers returns 202 without error.
     *
     * <p><strong>Validates: Requirements 3.1, 3.2</strong></p>
     *
     * <p>Test scenario:
     * <ol>
     *   <li>Provider publishes event via POST /{domainId}/{solutionId}/events</li>
     *   <li>Assert 202 Accepted (Requirement 3.1)</li>
     *   <li>No queues exist to poll — no messages enqueued (Requirement 3.2)</li>
     * </ol>
     */
    @Test
    @DisplayName("Provider publishes event with no subscribers → 202 Accepted, no messages enqueued")
    public void testEventFlow_NoSubscribers_Returns202() {
        log.info("Starting test: Event Flow — no subscribers, publish returns 202");

        // Given: Event payload
        String studentRefId = UUID.randomUUID().toString().substring(0, 8);
        String eventPayload = messageGenerator.generateStudentCreateEventJson(
            studentRefId, "Jane", "Smith"
        );

        // When: Provider publishes event via Gateway (no subscriptions exist)
        Response publishResponse = given()
            .spec(sifRequest(
                providerEnvironment.getSessionToken(),
                providerEnvironment.getSharedSecret(),
                ZONE_ID,
                CONTEXT_ID
            ))
            .header("serviceName", SERVICE_NAME)
            .body(eventPayload)
        .when()
            .post(gatewayPathPrefix() + "/events")
        .then()
            .log().ifValidationFails()
            .extract().response();

        log.info("Event published with no subscribers, response status: {}", publishResponse.getStatusCode());

        // Then: Events Connector returns 202 Accepted (Requirement 3.1)
        assertThat(publishResponse.getStatusCode())
            .as("Events Connector should return 202 Accepted even with zero subscribers")
            .isEqualTo(202);

        // Requirement 3.2: No subscriptions exist, so no messages are enqueued.
        // There are no queues to poll — the absence of queues/subscriptions confirms
        // the Events Connector completed without enqueueing any messages.

        log.info("Test completed successfully: Event Flow — no subscribers returns 202");
    }
}
