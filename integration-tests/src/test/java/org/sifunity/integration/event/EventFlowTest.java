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
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;
import org.sifunity.integration.config.AdminCredentialsConfig;
import org.sifunity.integration.dto.EnvironmentRegistrationResponse;
import org.sifunity.integration.generator.AclGenerator;
import org.sifunity.integration.generator.EnvironmentGenerator;
import org.sifunity.integration.generator.MessageGenerator;
import org.sifunity.integration.generator.ProviderGenerator;
import org.sifunity.integration.generator.QueueGenerator;
import org.sifunity.integration.generator.SubscriptionGenerator;

import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.ProtocolDTO;

/**
 * Integration tests for the EVENT flow: single subscriber receives a published event.
 *
 * <p>The EVENT flow validates the end-to-end event distribution pipeline:
 * <ol>
 *   <li>Provider publishes an event via POST /{domainId}/{solutionId}/events</li>
 *   <li>Events Connector distributes the event to all matching subscriber queues</li>
 *   <li>Consumer polls their queue and receives the event payload</li>
 *   <li>Consumer acknowledges the message, removing it from the queue</li>
 * </ol>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("event-flow")
@DisplayName("Event Flow — Single Subscriber Integration Tests")
public class EventFlowTest extends ServiceEnabledIntegrationTestBase {

    private static final String SERVICE_NAME = "StudentPersonals";
    private static final String ZONE_ID = "test-zone";
    private static final String CONTEXT_ID = "DEFAULT";

    private EnvironmentGenerator environmentGenerator;
    private AclGenerator aclGenerator;
    private ProviderGenerator providerGenerator;
    private QueueGenerator queueGenerator;
    private SubscriptionGenerator subscriptionGenerator;
    private MessageGenerator messageGenerator;
    private AdminCredentialsConfig adminConfig;

    private EnvironmentRegistrationResponse consumerEnvironment;
    private EnvironmentRegistrationResponse providerEnvironment;
    private AclEntryDTO consumerAcl;
    private AclEntryDTO providerAcl;
    private ProviderRegistrationDTO providerRegistration;
    private UUID consumerQueueId;
    private SubscriptionDTO eventSubscription;

    /**
     * Sets up test data before each test.
     *
     * <ol>
     *   <li>Initializes generators</li>
     *   <li>Creates consumer and provider environments</li>
     *   <li>Creates ACL entries (CONSUME for consumer, PROVIDE for provider)</li>
     *   <li>Creates a queue for the consumer</li>
     *   <li>Creates an EVENT subscription linking the queue to the serviceName</li>
     *   <li>Registers the provider</li>
     * </ol>
     */
    @BeforeEach
    @Override
    public void setupTest() {
        super.setupTest();

        log.info("Setting up Event Flow test");

        // Initialize ADMIN credentials
        adminConfig = new AdminCredentialsConfig();

        // Initialize generators with actual service URLs
        String environmentServiceUrl = getEnvironmentServiceUrl();
        String queueServiceUrl = getQueueServiceUrl();
        String subscriptionServiceUrl = getSubscriptionServiceUrl();
        environmentGenerator = new EnvironmentGenerator(environmentServiceUrl);
        aclGenerator = new AclGenerator(environmentServiceUrl, environmentGenerator);
        providerGenerator = new ProviderGenerator(environmentServiceUrl);
        queueGenerator = new QueueGenerator(queueServiceUrl, environmentGenerator);
        subscriptionGenerator = new SubscriptionGenerator(subscriptionServiceUrl);
        messageGenerator = new MessageGenerator();

        // Create consumer environment
        consumerEnvironment = environmentGenerator.createConsumer(
            environmentGenerator.generateUniqueName("event-consumer")
        );
        log.info("Consumer environment created: id={}, sessionToken={}",
            consumerEnvironment.getId(), consumerEnvironment.getSessionToken());

        // Create provider environment
        providerEnvironment = environmentGenerator.createProvider(
            environmentGenerator.generateUniqueName("event-provider")
        );
        log.info("Provider environment created: id={}, sessionToken={}",
            providerEnvironment.getId(), providerEnvironment.getSessionToken());

        // Create ACL entries using ADMIN credentials
        consumerAcl = aclGenerator.createConsumePermission(
            UUID.fromString(consumerEnvironment.getId()),
            SERVICE_NAME,
            null, null, // wildcard zone/context
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Consumer ACL created: id={}", consumerAcl.id());

        providerAcl = aclGenerator.createProvidePermission(
            UUID.fromString(providerEnvironment.getId()),
            SERVICE_NAME,
            null, null, // wildcard zone/context
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Provider ACL created: id={}", providerAcl.id());

        // Create queue for consumer
        consumerQueueId = queueGenerator.createQueue(
            consumerEnvironment,
            "event-consumer-queue",
            ZONE_ID,
            CONTEXT_ID
        );
        log.info("Consumer queue created: queueId={}", consumerQueueId);

        // Create EVENT subscription linking queue to serviceName
        eventSubscription = subscriptionGenerator.createEventSubscriptionWildcard(
            consumerQueueId,
            SERVICE_NAME
        );
        log.info("EVENT subscription created: id={}, queueId={}, serviceName={}",
            eventSubscription.id(), eventSubscription.queueId(), eventSubscription.serviceName());

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
     * subscriptions → queue → provider registration → ACLs → environments.
     */
    @AfterEach
    @Override
    public void teardownTest() {
        log.info("Tearing down Event Flow test");

        try {
            // Delete subscription
            if (eventSubscription != null) {
                subscriptionGenerator.deleteSubscription(eventSubscription.id());
            }

            // Delete queue
            if (consumerQueueId != null && consumerEnvironment != null) {
                queueGenerator.deleteQueue(
                    consumerQueueId,
                    consumerEnvironment.getSessionToken(),
                    consumerEnvironment.getSharedSecret()
                );
            }

            // Delete provider registration
            if (providerRegistration != null) {
                providerGenerator.deleteProvider(
                    providerRegistration.id(),
                    adminConfig.getSessionToken(),
                    adminConfig.getSharedSecret()
                );
            }

            // Delete ACL entries
            if (consumerAcl != null) {
                aclGenerator.deleteAclEntry(
                    consumerAcl.id(),
                    adminConfig.getSessionToken(),
                    adminConfig.getSharedSecret()
                );
            }
            if (providerAcl != null) {
                aclGenerator.deleteAclEntry(
                    providerAcl.id(),
                    adminConfig.getSessionToken(),
                    adminConfig.getSharedSecret()
                );
            }

            // Delete environments
            if (consumerEnvironment != null) {
                environmentGenerator.deleteEnvironment(UUID.fromString(consumerEnvironment.getId()));
            }
            if (providerEnvironment != null) {
                environmentGenerator.deleteEnvironment(UUID.fromString(providerEnvironment.getId()));
            }

        } catch (Exception e) {
            log.error("Error during test cleanup", e);
        }

        super.teardownTest();
    }

    /**
     * Tests the complete event flow: publish → distribute → poll → acknowledge.
     *
     * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4</strong></p>
     *
     * <p>Test scenario:
     * <ol>
     *   <li>Provider publishes event via POST /{domainId}/{solutionId}/events</li>
     *   <li>Assert 202 Accepted</li>
     *   <li>Consumer polls queue → assert 200 with matching payload</li>
     *   <li>Consumer acknowledges message → poll again → assert 204 (empty)</li>
     * </ol>
     */
    @Test
    @DisplayName("Provider publishes event → consumer receives and acknowledges it")
    public void testEventFlow_PublishDistributePollAcknowledge() {
        log.info("Starting test: Event Flow — publish, distribute, poll, acknowledge");

        // Given: Event payload
        String studentRefId = UUID.randomUUID().toString().substring(0, 8);
        String eventPayload = messageGenerator.generateStudentCreateEventJson(
            studentRefId, "Jane", "Smith"
        );

        // When: Provider publishes event via Gateway
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

        log.info("Event published, response status: {}", publishResponse.getStatusCode());

        // Then: Events Connector returns 202 Accepted (Requirement 1.1)
        assertThat(publishResponse.getStatusCode())
            .as("Events Connector should return 202 Accepted for valid event publication")
            .isEqualTo(202);

        // Wait for async event distribution to subscriber queue (Requirement 1.2)
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When: Consumer polls their queue
        Response pollResponse = queueGenerator.pollQueue(
            consumerQueueId,
            1,
            consumerEnvironment
        );

        log.info("Consumer queue polled, status: {}, body: {}",
            pollResponse.getStatusCode(), pollResponse.getBody().asString());

        // Then: Queue returns 200 with event payload matching published data (Requirement 1.3)
        assertThat(pollResponse.getStatusCode())
            .as("Consumer queue should contain the distributed event message")
            .isEqualTo(200);

        String receivedPayload = pollResponse.getBody().asString();
        assertThat(receivedPayload)
            .as("Received event payload should contain the published student data")
            .contains(studentRefId);

        // Extract message ID for acknowledgment
        String messageIdHeader = pollResponse.getHeader("messageid");
        assertThat(messageIdHeader)
            .as("Poll response should include messageid header")
            .isNotNull()
            .isNotBlank();
        UUID messageId = UUID.fromString(messageIdHeader);
        log.info("Received message: messageId={}", messageId);

        // When: Consumer acknowledges the message (Requirement 1.4)
        Response ackResponse = queueGenerator.acknowledgeMessage(
            consumerQueueId,
            messageId,
            consumerEnvironment.getSessionToken(),
            consumerEnvironment.getSharedSecret()
        );

        log.info("Message acknowledged, next poll status: {}", ackResponse.getStatusCode());

        // Then: Queue is empty after acknowledgment — 204 No Content
        assertThat(ackResponse.getStatusCode())
            .as("Queue should be empty after message acknowledgment (204 No Content)")
            .isEqualTo(204);

        log.info("Test completed successfully: Event Flow — publish, distribute, poll, acknowledge");
    }
}
