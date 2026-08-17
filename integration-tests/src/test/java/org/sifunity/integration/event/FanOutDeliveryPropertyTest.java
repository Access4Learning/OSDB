// SPDX-License-Identifier: MIT
package org.sifunity.integration.event;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
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

/**
 * Property-based test for fan-out delivery with acknowledgment isolation.
 *
 * <p><strong>Property 2: Fan-out delivery with acknowledgment isolation</strong></p>
 * <p>For any event published to a service with multiple subscribers, each subscriber's
 * queue SHALL contain an independent copy of the event, and acknowledging the message
 * in one subscriber's queue SHALL leave all other subscribers' copies unaffected.</p>
 *
 * <p>This is an integration-level property test that runs against the full broker stack.
 * Infrastructure (containers, services) is started once. Per iteration, a random event
 * payload is generated, published, verified to arrive in both subscriber queues independently,
 * then Consumer A's copy is acknowledged and Consumer B's copy is verified to remain.</p>
 *
 * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("event-flow")
@Tag("Feature: cross-service-integration-tests, Property 2: Fan-out delivery with acknowledgment isolation")
public class FanOutDeliveryPropertyTest extends ServiceEnabledIntegrationTestBase {

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

    private boolean infrastructureReady = false;

    /**
     * Sets up the full broker infrastructure and fan-out test fixtures once before all
     * property iterations. Creates 1 provider + 2 consumers, ACLs, 2 queues, 2 EVENT
     * subscriptions for the same serviceName, and registers the provider.
     */
    private void ensureInfrastructure() {
        if (infrastructureReady) {
            return;
        }

        log.info("Setting up infrastructure for FanOutDeliveryPropertyTest");

        // Start containers and services via base class
        setupInfrastructure();

        // Initialize ADMIN credentials
        adminConfig = new AdminCredentialsConfig();

        // Initialize generators
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
            environmentGenerator.generateUniqueName("pbt-fanout-provider")
        );
        log.info("Provider environment created: id={}", providerEnvironment.getId());

        // Create Consumer A environment
        consumerAEnvironment = environmentGenerator.createConsumer(
            environmentGenerator.generateUniqueName("pbt-fanout-consumer-a")
        );
        log.info("Consumer A environment created: id={}", consumerAEnvironment.getId());

        // Create Consumer B environment
        consumerBEnvironment = environmentGenerator.createConsumer(
            environmentGenerator.generateUniqueName("pbt-fanout-consumer-b")
        );
        log.info("Consumer B environment created: id={}", consumerBEnvironment.getId());

        // Create ACL entries using ADMIN credentials
        providerAcl = aclGenerator.createProvidePermission(
            UUID.fromString(providerEnvironment.getId()),
            SERVICE_NAME,
            null, null,
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Provider ACL created: id={}", providerAcl.id());

        consumerAAcl = aclGenerator.createConsumePermission(
            UUID.fromString(consumerAEnvironment.getId()),
            SERVICE_NAME,
            null, null,
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Consumer A ACL created: id={}", consumerAAcl.id());

        consumerBAcl = aclGenerator.createConsumePermission(
            UUID.fromString(consumerBEnvironment.getId()),
            SERVICE_NAME,
            null, null,
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Consumer B ACL created: id={}", consumerBAcl.id());

        // Create queue for Consumer A
        consumerAQueueId = queueGenerator.createQueue(
            consumerAEnvironment,
            "pbt-fanout-consumer-a-queue",
            ZONE_ID,
            CONTEXT_ID
        );
        log.info("Consumer A queue created: queueId={}", consumerAQueueId);

        // Create queue for Consumer B
        consumerBQueueId = queueGenerator.createQueue(
            consumerBEnvironment,
            "pbt-fanout-consumer-b-queue",
            ZONE_ID,
            CONTEXT_ID
        );
        log.info("Consumer B queue created: queueId={}", consumerBQueueId);

        // Create EVENT subscription for Consumer A — same serviceName
        consumerASubscription = subscriptionGenerator.createEventSubscriptionWildcard(
            consumerAQueueId,
            SERVICE_NAME
        );
        log.info("Consumer A EVENT subscription created: id={}, queueId={}",
            consumerASubscription.id(), consumerASubscription.queueId());

        // Create EVENT subscription for Consumer B — same serviceName
        consumerBSubscription = subscriptionGenerator.createEventSubscriptionWildcard(
            consumerBQueueId,
            SERVICE_NAME
        );
        log.info("Consumer B EVENT subscription created: id={}, queueId={}",
            consumerBSubscription.id(), consumerBSubscription.queueId());

        // Register provider
        providerRegistration = providerGenerator.registerProvider(
            UUID.fromString(providerEnvironment.getId()),
            SERVICE_NAME,
            ZONE_ID,
            CONTEXT_ID,
            "http://provider-endpoint.example.com/" + SERVICE_NAME,
            Set.of(MessageExchangePattern.EVENT),
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Provider registered: id={}", providerRegistration.id());

        infrastructureReady = true;
    }

    // ── Arbitraries ──────────────────────────────────────────────────

    /**
     * Generates random refId strings (8-char hex-like identifiers).
     */
    @Provide
    Arbitrary<String> refIds() {
        return Arbitraries.strings()
            .withCharRange('a', 'f')
            .withCharRange('0', '9')
            .ofLength(8);
    }

    /**
     * Generates random first names.
     */
    @Provide
    Arbitrary<String> firstNames() {
        return Arbitraries.of(
            "Alice", "Bob", "Charlie", "Diana", "Eve", "Frank",
            "Grace", "Hank", "Iris", "Jack", "Karen", "Leo",
            "Mia", "Noah", "Olivia", "Paul", "Quinn", "Rosa"
        );
    }

    /**
     * Generates random last names.
     */
    @Provide
    Arbitrary<String> lastNames() {
        return Arbitraries.of(
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia",
            "Miller", "Davis", "Rodriguez", "Martinez", "Hernandez",
            "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas"
        );
    }

    /**
     * Generates random event actions.
     */
    @Provide
    Arbitrary<String> eventActions() {
        return Arbitraries.of("CREATE", "UPDATE", "DELETE");
    }

    // ── Property ─────────────────────────────────────────────────────

    /**
     * <strong>Property 2: Fan-out delivery with acknowledgment isolation</strong>
     *
     * <p>For any event published to a service with multiple subscribers, each subscriber's
     * queue SHALL contain an independent copy of the event, and acknowledging the message
     * in one subscriber's queue SHALL leave all other subscribers' copies unaffected.</p>
     *
     * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
     */
    @Property(tries = 10)
    @Tag("Feature: cross-service-integration-tests, Property 2: Fan-out delivery with acknowledgment isolation")
    void fanOutDeliveryWithAcknowledgmentIsolation(
        @ForAll("refIds") String refId,
        @ForAll("firstNames") String firstName,
        @ForAll("lastNames") String lastName,
        @ForAll("eventActions") String eventAction
    ) {
        // Ensure infrastructure is running (idempotent)
        ensureInfrastructure();

        log.info("Property iteration: refId={}, name={} {}, action={}",
            refId, firstName, lastName, eventAction);

        // Drain any leftover messages from previous iterations
        drainQueue(consumerAQueueId, consumerAEnvironment);
        drainQueue(consumerBQueueId, consumerBEnvironment);

        // Build random event payload
        String studentPayload = messageGenerator.generateStudentPersonalJson(refId, firstName, lastName);
        String eventPayload = messageGenerator.generateEventJson(eventAction, "StudentPersonal", studentPayload);

        // Publish event via Gateway
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
            .extract().response();

        assertThat(publishResponse.getStatusCode())
            .as("Events Connector should return 202 Accepted")
            .isEqualTo(202);

        // Wait for async event distribution to both subscriber queues
        sleep(3000);

        // Poll Consumer A's queue — should get independent copy (Requirement 2.3)
        Response pollResponseA = queueGenerator.pollQueue(
            consumerAQueueId,
            1,
            consumerAEnvironment
        );

        assertThat(pollResponseA.getStatusCode())
            .as("Consumer A queue should contain the distributed event message")
            .isEqualTo(200);

        String receivedPayloadA = pollResponseA.getBody().asString();
        assertThat(receivedPayloadA)
            .as("Consumer A received payload should contain the published refId '%s'", refId)
            .contains(refId);

        String messageIdHeaderA = pollResponseA.getHeader("messageid");
        assertThat(messageIdHeaderA)
            .as("Consumer A poll response should include messageid header")
            .isNotNull()
            .isNotBlank();
        UUID messageIdA = UUID.fromString(messageIdHeaderA);
        log.info("Consumer A received message: messageId={}", messageIdA);

        // Poll Consumer B's queue — should get independent copy (Requirement 2.3)
        Response pollResponseB = queueGenerator.pollQueue(
            consumerBQueueId,
            1,
            consumerBEnvironment
        );

        assertThat(pollResponseB.getStatusCode())
            .as("Consumer B queue should contain the distributed event message")
            .isEqualTo(200);

        String receivedPayloadB = pollResponseB.getBody().asString();
        assertThat(receivedPayloadB)
            .as("Consumer B received payload should contain the published refId '%s'", refId)
            .contains(refId);

        String messageIdHeaderB = pollResponseB.getHeader("messageid");
        assertThat(messageIdHeaderB)
            .as("Consumer B poll response should include messageid header")
            .isNotNull()
            .isNotBlank();
        UUID messageIdB = UUID.fromString(messageIdHeaderB);
        log.info("Consumer B received message: messageId={}", messageIdB);

        // Acknowledge Consumer A's message (Requirement 2.4)
        Response ackResponseA = queueGenerator.acknowledgeMessage(
            consumerAQueueId,
            messageIdA,
            consumerAEnvironment.getSessionToken(),
            consumerAEnvironment.getSharedSecret()
        );

        log.info("Consumer A message acknowledged, ack response status: {}", ackResponseA.getStatusCode());

        // Consumer A's queue should be empty after acknowledgment — 204 No Content
        assertThat(ackResponseA.getStatusCode())
            .as("Consumer A queue should be empty after message acknowledgment (204 No Content)")
            .isEqualTo(204);

        // Poll Consumer B's queue again — Consumer B's copy should still be present (Requirement 2.4)
        Response pollResponseBAfterAck = queueGenerator.pollQueue(
            consumerBQueueId,
            1,
            consumerBEnvironment
        );

        assertThat(pollResponseBAfterAck.getStatusCode())
            .as("Consumer B's copy should still be present after Consumer A acknowledged their message")
            .isEqualTo(200);

        String receivedPayloadBAfterAck = pollResponseBAfterAck.getBody().asString();
        assertThat(receivedPayloadBAfterAck)
            .as("Consumer B's copy should still contain the published refId '%s' after Consumer A's ack", refId)
            .contains(refId);

        // Clean up: acknowledge Consumer B's message for next iteration
        String messageIdHeaderBAfterAck = pollResponseBAfterAck.getHeader("messageid");
        if (messageIdHeaderBAfterAck != null && !messageIdHeaderBAfterAck.isBlank()) {
            queueGenerator.acknowledgeMessage(
                consumerBQueueId,
                UUID.fromString(messageIdHeaderBAfterAck),
                consumerBEnvironment.getSessionToken(),
                consumerBEnvironment.getSharedSecret()
            );
        }

        log.info("Property iteration passed: refId={}", refId);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Drains any leftover messages from the specified queue.
     */
    private void drainQueue(UUID queueId, EnvironmentRegistrationResponse environment) {
        try {
            for (int i = 0; i < 5; i++) {
                Response poll = queueGenerator.pollQueue(queueId, 1, environment);
                if (poll.getStatusCode() == 204) {
                    break; // Queue is empty
                }
                String msgId = poll.getHeader("messageid");
                if (msgId != null && !msgId.isBlank()) {
                    queueGenerator.acknowledgeMessage(
                        queueId,
                        UUID.fromString(msgId),
                        environment.getSessionToken(),
                        environment.getSharedSecret()
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Error draining queue {}, continuing: {}", queueId, e.getMessage());
        }
    }

    /**
     * Sleeps for the specified duration, handling interruption.
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
