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
 * Integration tests for the EVENT flow with multiple subscribers (fan-out).
 *
 * <p>The fan-out EVENT flow validates that when multiple consumers subscribe to
 * the same serviceName, each receives an independent copy of the published event:
 * <ol>
 *   <li>Provider publishes an event via POST /{domainId}/{solutionId}/events</li>
 *   <li>Events Connector distributes the event to ALL matching subscriber queues</li>
 *   <li>Each consumer polls their own queue and receives the event payload independently</li>
 *   <li>Acknowledging one consumer's message does not affect the other consumer's copy</li>
 * </ol>
 *
 * <p><strong>Validates: Requirements 2.1, 2.2, 2.3, 2.4</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("event-flow")
@Tag("fan-out")
@DisplayName("Event Flow — Multiple Subscriber Fan-Out Integration Tests")
public class MultiSubscriberEventFlowTest extends ServiceEnabledIntegrationTestBase {

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

    // Provider
    private EnvironmentRegistrationResponse providerEnvironment;
    private AclEntryDTO providerAcl;
    private ProviderRegistrationDTO providerRegistration;

    // Consumer A
    private EnvironmentRegistrationResponse consumerAEnvironment;
    private AclEntryDTO consumerAAcl;
    private UUID consumerAQueueId;
    private SubscriptionDTO consumerASubscription;

    // Consumer B
    private EnvironmentRegistrationResponse consumerBEnvironment;
    private AclEntryDTO consumerBAcl;
    private UUID consumerBQueueId;
    private SubscriptionDTO consumerBSubscription;

    /**
     * Sets up test data before each test.
     *
     * <ol>
     *   <li>Initializes generators</li>
     *   <li>Creates provider environment</li>
     *   <li>Creates two consumer environments (A and B)</li>
     *   <li>Creates ACL entries (PROVIDE for provider, CONSUME for both consumers)</li>
     *   <li>Creates a queue for each consumer</li>
     *   <li>Creates an EVENT subscription for each consumer queue, both for same serviceName</li>
     *   <li>Registers the provider</li>
     * </ol>
     */
    @BeforeEach
    @Override
    public void setupTest() {
        super.setupTest();

        log.info("Setting up Multi-Subscriber Event Flow test");

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

        // Create provider environment
        providerEnvironment = environmentGenerator.createProvider(
            environmentGenerator.generateUniqueName("fanout-provider")
        );
        log.info("Provider environment created: id={}, sessionToken={}",
            providerEnvironment.getId(), providerEnvironment.getSessionToken());

        // Create Consumer A environment
        consumerAEnvironment = environmentGenerator.createConsumer(
            environmentGenerator.generateUniqueName("fanout-consumer-a")
        );
        log.info("Consumer A environment created: id={}, sessionToken={}",
            consumerAEnvironment.getId(), consumerAEnvironment.getSessionToken());

        // Create Consumer B environment
        consumerBEnvironment = environmentGenerator.createConsumer(
            environmentGenerator.generateUniqueName("fanout-consumer-b")
        );
        log.info("Consumer B environment created: id={}, sessionToken={}",
            consumerBEnvironment.getId(), consumerBEnvironment.getSessionToken());

        // Create ACL entries using ADMIN credentials
        providerAcl = aclGenerator.createProvidePermission(
            UUID.fromString(providerEnvironment.getId()),
            SERVICE_NAME,
            null, null, // wildcard zone/context
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Provider ACL created: id={}", providerAcl.id());

        consumerAAcl = aclGenerator.createConsumePermission(
            UUID.fromString(consumerAEnvironment.getId()),
            SERVICE_NAME,
            null, null, // wildcard zone/context
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Consumer A ACL created: id={}", consumerAAcl.id());

        consumerBAcl = aclGenerator.createConsumePermission(
            UUID.fromString(consumerBEnvironment.getId()),
            SERVICE_NAME,
            null, null, // wildcard zone/context
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Consumer B ACL created: id={}", consumerBAcl.id());

        // Create queue for Consumer A
        consumerAQueueId = queueGenerator.createQueue(
            consumerAEnvironment,
            "fanout-consumer-a-queue",
            ZONE_ID,
            CONTEXT_ID
        );
        log.info("Consumer A queue created: queueId={}", consumerAQueueId);

        // Create queue for Consumer B
        consumerBQueueId = queueGenerator.createQueue(
            consumerBEnvironment,
            "fanout-consumer-b-queue",
            ZONE_ID,
            CONTEXT_ID
        );
        log.info("Consumer B queue created: queueId={}", consumerBQueueId);

        // Create EVENT subscription for Consumer A — same serviceName
        consumerASubscription = subscriptionGenerator.createEventSubscriptionWildcard(
            consumerAQueueId,
            SERVICE_NAME
        );
        log.info("Consumer A EVENT subscription created: id={}, queueId={}, serviceName={}",
            consumerASubscription.id(), consumerASubscription.queueId(), consumerASubscription.serviceName());

        // Create EVENT subscription for Consumer B — same serviceName
        consumerBSubscription = subscriptionGenerator.createEventSubscriptionWildcard(
            consumerBQueueId,
            SERVICE_NAME
        );
        log.info("Consumer B EVENT subscription created: id={}, queueId={}, serviceName={}",
            consumerBSubscription.id(), consumerBSubscription.queueId(), consumerBSubscription.serviceName());

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
     * subscriptions → queues → provider registration → ACLs → environments.
     */
    @AfterEach
    @Override
    public void teardownTest() {
        log.info("Tearing down Multi-Subscriber Event Flow test");

        try {
            // Delete subscriptions
            if (consumerBSubscription != null) {
                subscriptionGenerator.deleteSubscription(consumerBSubscription.id());
            }
            if (consumerASubscription != null) {
                subscriptionGenerator.deleteSubscription(consumerASubscription.id());
            }

            // Delete queues
            if (consumerBQueueId != null && consumerBEnvironment != null) {
                queueGenerator.deleteQueue(
                    consumerBQueueId,
                    consumerBEnvironment.getSessionToken(),
                    consumerBEnvironment.getSharedSecret()
                );
            }
            if (consumerAQueueId != null && consumerAEnvironment != null) {
                queueGenerator.deleteQueue(
                    consumerAQueueId,
                    consumerAEnvironment.getSessionToken(),
                    consumerAEnvironment.getSharedSecret()
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
            if (consumerBAcl != null) {
                aclGenerator.deleteAclEntry(
                    consumerBAcl.id(),
                    adminConfig.getSessionToken(),
                    adminConfig.getSharedSecret()
                );
            }
            if (consumerAAcl != null) {
                aclGenerator.deleteAclEntry(
                    consumerAAcl.id(),
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
            if (consumerBEnvironment != null) {
                environmentGenerator.deleteEnvironment(UUID.fromString(consumerBEnvironment.getId()));
            }
            if (consumerAEnvironment != null) {
                environmentGenerator.deleteEnvironment(UUID.fromString(consumerAEnvironment.getId()));
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
     * Tests fan-out event distribution: publish → both consumers receive → ack isolation.
     *
     * <p><strong>Validates: Requirements 2.1, 2.2, 2.3, 2.4</strong></p>
     *
     * <p>Test scenario:
     * <ol>
     *   <li>Provider publishes event → assert 202 Accepted</li>
     *   <li>Consumer A polls queue → assert 200 with matching payload</li>
     *   <li>Consumer B polls queue → assert 200 with matching payload</li>
     *   <li>Consumer A acknowledges message</li>
     *   <li>Consumer B polls queue → assert Consumer B's copy still present</li>
     * </ol>
     */
    @Test
    @DisplayName("Provider publishes event → both consumers receive independent copies, ack isolation verified")
    public void testMultiSubscriberEventFlow_FanOutWithAckIsolation() {
        log.info("Starting test: Multi-Subscriber Event Flow — fan-out with ack isolation");

        // Given: Event payload
        String studentRefId = UUID.randomUUID().toString().substring(0, 8);
        String eventPayload = messageGenerator.generateStudentCreateEventJson(
            studentRefId, "Alice", "Johnson"
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

        // Then: Events Connector returns 202 Accepted (Requirement 2.2)
        assertThat(publishResponse.getStatusCode())
            .as("Events Connector should return 202 Accepted for valid event publication")
            .isEqualTo(202);

        // Wait for async event distribution to both subscriber queues
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When: Consumer A polls their queue (Requirement 2.3)
        Response pollResponseA = queueGenerator.pollQueue(
            consumerAQueueId,
            1,
            consumerAEnvironment
        );

        log.info("Consumer A queue polled, status: {}, body: {}",
            pollResponseA.getStatusCode(), pollResponseA.getBody().asString());

        // Then: Consumer A receives 200 with matching payload
        assertThat(pollResponseA.getStatusCode())
            .as("Consumer A queue should contain the distributed event message")
            .isEqualTo(200);

        String receivedPayloadA = pollResponseA.getBody().asString();
        assertThat(receivedPayloadA)
            .as("Consumer A received payload should contain the published student data")
            .contains(studentRefId);

        // Extract Consumer A's message ID
        String messageIdHeaderA = pollResponseA.getHeader("messageid");
        assertThat(messageIdHeaderA)
            .as("Consumer A poll response should include messageid header")
            .isNotNull()
            .isNotBlank();
        UUID messageIdA = UUID.fromString(messageIdHeaderA);
        log.info("Consumer A received message: messageId={}", messageIdA);

        // When: Consumer B polls their queue (Requirement 2.3)
        Response pollResponseB = queueGenerator.pollQueue(
            consumerBQueueId,
            1,
            consumerBEnvironment
        );

        log.info("Consumer B queue polled, status: {}, body: {}",
            pollResponseB.getStatusCode(), pollResponseB.getBody().asString());

        // Then: Consumer B receives 200 with matching payload
        assertThat(pollResponseB.getStatusCode())
            .as("Consumer B queue should contain the distributed event message")
            .isEqualTo(200);

        String receivedPayloadB = pollResponseB.getBody().asString();
        assertThat(receivedPayloadB)
            .as("Consumer B received payload should contain the published student data")
            .contains(studentRefId);

        // Extract Consumer B's message ID
        String messageIdHeaderB = pollResponseB.getHeader("messageid");
        assertThat(messageIdHeaderB)
            .as("Consumer B poll response should include messageid header")
            .isNotNull()
            .isNotBlank();
        UUID messageIdB = UUID.fromString(messageIdHeaderB);
        log.info("Consumer B received message: messageId={}", messageIdB);

        // When: Consumer A acknowledges their message (Requirement 2.4)
        Response ackResponseA = queueGenerator.acknowledgeMessage(
            consumerAQueueId,
            messageIdA,
            consumerAEnvironment.getSessionToken(),
            consumerAEnvironment.getSharedSecret()
        );

        log.info("Consumer A message acknowledged, ack response status: {}", ackResponseA.getStatusCode());

        // Then: Consumer A's queue is empty after acknowledgment — 204 No Content
        assertThat(ackResponseA.getStatusCode())
            .as("Consumer A queue should be empty after message acknowledgment (204 No Content)")
            .isEqualTo(204);

        // When: Consumer B polls their queue again — Consumer B's copy should still be present (Requirement 2.4)
        Response pollResponseBAfterAckA = queueGenerator.pollQueue(
            consumerBQueueId,
            1,
            consumerBEnvironment
        );

        log.info("Consumer B queue polled after Consumer A ack, status: {}, body: {}",
            pollResponseBAfterAckA.getStatusCode(), pollResponseBAfterAckA.getBody().asString());

        // Then: Consumer B's copy is still present — 200 with matching payload
        assertThat(pollResponseBAfterAckA.getStatusCode())
            .as("Consumer B's copy should still be present after Consumer A acknowledged their message")
            .isEqualTo(200);

        String receivedPayloadBAfterAck = pollResponseBAfterAckA.getBody().asString();
        assertThat(receivedPayloadBAfterAck)
            .as("Consumer B's copy should still contain the published student data after Consumer A's ack")
            .contains(studentRefId);

        log.info("Test completed successfully: Multi-Subscriber Event Flow — fan-out with ack isolation");
    }
}
