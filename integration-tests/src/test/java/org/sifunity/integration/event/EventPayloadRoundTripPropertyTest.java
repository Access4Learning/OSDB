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
 * Property-based test for event payload round-trip through the broker.
 *
 * <p><strong>Property 1: Event payload round-trip</strong></p>
 * <p>For any valid JSON event payload, publishing the event through the events-connector
 * and then polling the subscriber's queue SHALL return a message whose payload contains
 * the originally published data.</p>
 *
 * <p>This is an integration-level property test that runs against the full broker stack.
 * Infrastructure (containers, services) is started once. Per-property-iteration, a random
 * JSON payload is generated, published as an event, and verified to arrive in the
 * subscriber's queue with matching content.</p>
 *
 * <p><strong>Validates: Requirements 1.3</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("event-flow")
@Tag("Feature: cross-service-integration-tests, Property 1: Event payload round-trip")
public class EventPayloadRoundTripPropertyTest extends ServiceEnabledIntegrationTestBase {

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

    private boolean infrastructureReady = false;

    /**
     * Sets up the full broker infrastructure and test fixtures once before all property iterations.
     * Creates consumer/provider environments, ACLs, queue, subscription, and provider registration.
     */
    private void ensureInfrastructure() {
        if (infrastructureReady) {
            return;
        }

        log.info("Setting up infrastructure for EventPayloadRoundTripPropertyTest");

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

        // Create consumer environment
        consumerEnvironment = environmentGenerator.createConsumer(
            environmentGenerator.generateUniqueName("pbt-event-consumer")
        );
        log.info("Consumer environment created: id={}", consumerEnvironment.getId());

        // Create provider environment
        providerEnvironment = environmentGenerator.createProvider(
            environmentGenerator.generateUniqueName("pbt-event-provider")
        );
        log.info("Provider environment created: id={}", providerEnvironment.getId());

        // Create ACL entries
        consumerAcl = aclGenerator.createConsumePermission(
            UUID.fromString(consumerEnvironment.getId()),
            SERVICE_NAME,
            null, null,
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );

        providerAcl = aclGenerator.createProvidePermission(
            UUID.fromString(providerEnvironment.getId()),
            SERVICE_NAME,
            null, null,
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );

        // Create queue for consumer
        consumerQueueId = queueGenerator.createQueue(
            consumerEnvironment,
            "pbt-event-queue",
            ZONE_ID,
            CONTEXT_ID
        );
        log.info("Consumer queue created: queueId={}", consumerQueueId);

        // Create EVENT subscription
        eventSubscription = subscriptionGenerator.createEventSubscriptionWildcard(
            consumerQueueId,
            SERVICE_NAME
        );
        log.info("EVENT subscription created: id={}", eventSubscription.id());

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

    /**
     * Tears down test fixtures. Called after all property iterations complete.
     */
    private void cleanupFixtures() {
        log.info("Cleaning up EventPayloadRoundTripPropertyTest fixtures");

        try {
            if (eventSubscription != null) {
                subscriptionGenerator.deleteSubscription(eventSubscription.id());
            }
            if (consumerQueueId != null && consumerEnvironment != null) {
                queueGenerator.deleteQueue(
                    consumerQueueId,
                    consumerEnvironment.getSessionToken(),
                    consumerEnvironment.getSharedSecret()
                );
            }
            if (providerRegistration != null) {
                providerGenerator.deleteProvider(
                    providerRegistration.id(),
                    adminConfig.getSessionToken(),
                    adminConfig.getSharedSecret()
                );
            }
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
            if (consumerEnvironment != null) {
                environmentGenerator.deleteEnvironment(UUID.fromString(consumerEnvironment.getId()));
            }
            if (providerEnvironment != null) {
                environmentGenerator.deleteEnvironment(UUID.fromString(providerEnvironment.getId()));
            }
        } catch (Exception e) {
            log.error("Error during fixture cleanup", e);
        }

        try {
            teardownInfrastructure();
        } catch (Exception e) {
            log.error("Error during infrastructure teardown", e);
        }
    }

    // ── Arbitraries ──────────────────────────────────────────────────

    /**
     * Generates random alphanumeric strings for use as field values.
     */
    @Provide
    Arbitrary<String> fieldValues() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(1)
            .ofMaxLength(50);
    }

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

    /**
     * Generates a random number of extra fields to add to the payload (0-5).
     */
    @Provide
    Arbitrary<Integer> extraFieldCounts() {
        return Arbitraries.integers().between(0, 5);
    }

    // ── Property ─────────────────────────────────────────────────────

    /**
     * <strong>Property 1: Event payload round-trip</strong>
     *
     * <p>For any valid JSON event payload generated with random refId, names, event action,
     * and optional extra fields, publishing the event through the events-connector and then
     * polling the subscriber's queue SHALL return a message whose payload contains the
     * originally published refId.</p>
     *
     * <p><strong>Validates: Requirements 1.3</strong></p>
     */
    @Property(tries = 10)
    @Tag("Feature: cross-service-integration-tests, Property 1: Event payload round-trip")
    void eventPayloadRoundTrip(
        @ForAll("refIds") String refId,
        @ForAll("firstNames") String firstName,
        @ForAll("lastNames") String lastName,
        @ForAll("eventActions") String eventAction,
        @ForAll("extraFieldCounts") int extraFieldCount
    ) {
        // Ensure infrastructure is running (idempotent)
        ensureInfrastructure();

        log.info("Property iteration: refId={}, name={} {}, action={}, extraFields={}",
            refId, firstName, lastName, eventAction, extraFieldCount);

        // Build a random JSON payload with varying structure
        String objectPayload = buildRandomStudentPayload(refId, firstName, lastName, extraFieldCount);
        String eventPayload = messageGenerator.generateEventJson(eventAction, "StudentPersonal", objectPayload);

        // Drain any leftover messages from previous iterations
        drainQueue();

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

        // Wait for async distribution
        sleep(3000);

        // Poll consumer queue
        Response pollResponse = queueGenerator.pollQueue(
            consumerQueueId,
            1,
            consumerEnvironment
        );

        assertThat(pollResponse.getStatusCode())
            .as("Consumer queue should contain the distributed event message")
            .isEqualTo(200);

        // Verify payload round-trip: the received message must contain the refId
        String receivedPayload = pollResponse.getBody().asString();
        assertThat(receivedPayload)
            .as("Received event payload should contain the published refId '%s'", refId)
            .contains(refId);

        // Also verify first and last name survived the round-trip
        assertThat(receivedPayload)
            .as("Received event payload should contain the published firstName '%s'", firstName)
            .contains(firstName);
        assertThat(receivedPayload)
            .as("Received event payload should contain the published lastName '%s'", lastName)
            .contains(lastName);

        // Acknowledge the message to clean up for next iteration
        String messageIdHeader = pollResponse.getHeader("messageid");
        if (messageIdHeader != null && !messageIdHeader.isBlank()) {
            queueGenerator.acknowledgeMessage(
                consumerQueueId,
                UUID.fromString(messageIdHeader),
                consumerEnvironment.getSessionToken(),
                consumerEnvironment.getSharedSecret()
            );
        }

        log.info("Property iteration passed: refId={}", refId);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Builds a random StudentPersonal JSON payload with optional extra fields.
     */
    private String buildRandomStudentPayload(
        String refId,
        String firstName,
        String lastName,
        int extraFieldCount
    ) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append(String.format("  \"refId\": \"%s\",\n", refId));
        json.append(String.format("  \"localId\": \"%s\",\n", refId));
        json.append("  \"name\": {\n");
        json.append("    \"type\": \"LGL\",\n");
        json.append(String.format("    \"familyName\": \"%s\",\n", lastName));
        json.append(String.format("    \"givenName\": \"%s\"\n", firstName));
        json.append("  }");

        // Add random extra fields for payload variation
        for (int i = 0; i < extraFieldCount; i++) {
            json.append(",\n");
            json.append(String.format("  \"extraField%d\": \"value-%s-%d\"", i, refId, i));
        }

        json.append("\n}");
        return json.toString();
    }

    /**
     * Drains any leftover messages from the consumer queue.
     */
    private void drainQueue() {
        try {
            for (int i = 0; i < 5; i++) {
                Response poll = queueGenerator.pollQueue(
                    consumerQueueId, 1, consumerEnvironment
                );
                if (poll.getStatusCode() == 204) {
                    break; // Queue is empty
                }
                String msgId = poll.getHeader("messageid");
                if (msgId != null && !msgId.isBlank()) {
                    queueGenerator.acknowledgeMessage(
                        consumerQueueId,
                        UUID.fromString(msgId),
                        consumerEnvironment.getSessionToken(),
                        consumerEnvironment.getSharedSecret()
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Error draining queue, continuing: {}", e.getMessage());
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
