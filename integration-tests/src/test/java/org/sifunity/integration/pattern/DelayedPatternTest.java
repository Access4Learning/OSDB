// SPDX-License-Identifier: MIT
package org.sifunity.integration.pattern;

import com.github.tomakehurst.wiremock.client.WireMock;
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
import org.sifunity.integration.wiremock.WireMockProviderSimulator;

import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.ProtocolDTO;

/**
 * Integration tests for the DELAYED message exchange pattern.
 * 
 * <p>The DELAYED pattern is an asynchronous request/response pattern where:
 * <ol>
 *   <li>Consumer sends a request with a queueId to the Gateway</li>
 *   <li>Gateway routes the request to the Requests Connector</li>
 *   <li>Requests Connector looks up the Provider in the registry</li>
 *   <li>If Provider supports IMMEDIATE: Broker acts as proxy, calls Provider synchronously, places response on Consumer's queue</li>
 *   <li>If Provider supports DELAYED: Request is placed on Provider's request queue, Provider polls queue, processes request, submits response to Responses Connector</li>
 *   <li>Consumer polls their queue to retrieve the response</li>
 * </ol>
 * 
 * <p>These tests validate:
 * <ul>
 *   <li>DELAYED request to IMMEDIATE Provider (broker as proxy)</li>
 *   <li>DELAYED request to DELAYED Provider (true async with queues)</li>
 *   <li>Message persistence and queue state</li>
 *   <li>Response routing to correct Consumer queue</li>
 * </ul>
 * 
 * <p><strong>Property 1: Message exchange tests verify persistence</strong><br>
 * For any message exchange pattern test (IMMEDIATE, DELAYED, EVENT), the test should
 * verify that messages are properly persisted to storage, queue state is correct, and
 * acknowledged messages are cleaned up.
 * 
 * @since 1.0.0
 */
@Slf4j
@Tag("message-pattern")
@Tag("delayed")
@DisplayName("DELAYED Pattern Integration Tests")
public class DelayedPatternTest extends ServiceEnabledIntegrationTestBase {
    
    private static final String SERVICE_NAME = "StudentPersonals";
    private static final String ZONE_ID = "test-zone";
    private static final String CONTEXT_ID = "DEFAULT";
    
    private EnvironmentGenerator environmentGenerator;
    private AclGenerator aclGenerator;
    private ProviderGenerator providerGenerator;
    private MessageGenerator messageGenerator;
    private QueueGenerator queueGenerator;
    private WireMockProviderSimulator wireMockSimulator;
    private AdminCredentialsConfig adminConfig;
    
    private EnvironmentRegistrationResponse consumerEnvironment;
    private EnvironmentRegistrationResponse providerEnvironment;
    private AclEntryDTO consumerAcl;
    private AclEntryDTO providerAcl;
    private ProviderRegistrationDTO providerRegistration;
    
    /**
     * Sets up test data before each test.
     * 
     * <p>This method:
     * <ol>
     *   <li>Initializes generators</li>
     *   <li>Starts WireMock simulator</li>
     *   <li>Creates consumer and provider environments</li>
     *   <li>Creates ACL entries for both environments</li>
     * </ol>
     * 
     * Note: Provider registration is done in individual tests since
     * DELAYED pattern tests need different provider configurations.
     */
    @BeforeEach
    @Override
    public void setupTest() {
        super.setupTest();
        
        log.info("Setting up DELAYED pattern test");
        
        // Initialize ADMIN credentials
        adminConfig = new AdminCredentialsConfig();
        
        // Initialize generators with actual service URLs
        String environmentServiceUrl = getEnvironmentServiceUrl();
        String queueServiceUrl = getQueueServiceUrl();
        environmentGenerator = new EnvironmentGenerator(environmentServiceUrl);
        aclGenerator = new AclGenerator(environmentServiceUrl, environmentGenerator);
        providerGenerator = new ProviderGenerator(environmentServiceUrl);
        queueGenerator = new QueueGenerator(queueServiceUrl, environmentGenerator);
        messageGenerator = new MessageGenerator();
        
        // Start WireMock simulator
        wireMockSimulator = new WireMockProviderSimulator();
        wireMockSimulator.start();
        
        // Create consumer environment
        consumerEnvironment = environmentGenerator.createConsumer(
            environmentGenerator.generateUniqueName("test-consumer")
        );
        log.info("Consumer environment created: id={}, sessionToken={}",
            consumerEnvironment.getId(), consumerEnvironment.getSessionToken());
        
        // Create provider environment
        providerEnvironment = environmentGenerator.createProvider(
            environmentGenerator.generateUniqueName("test-provider")
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
    }
    
    /**
     * Cleans up test data after each test.
     * 
     * <p>This method:
     * <ol>
     *   <li>Deletes provider registration (if created)</li>
     *   <li>Deletes ACL entries</li>
     *   <li>Deletes environments</li>
     *   <li>Stops WireMock simulator</li>
     * </ol>
     */
    @AfterEach
    @Override
    public void teardownTest() {
        log.info("Tearing down DELAYED pattern test");
        
        try {
            // Delete provider registration using ADMIN credentials
            if (providerRegistration != null) {
                providerGenerator.deleteProvider(
                    providerRegistration.id(),
                    adminConfig.getSessionToken(),
                    adminConfig.getSharedSecret()
                );
            }
            
            // Delete ACL entries using ADMIN credentials
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
            
            // Stop WireMock
            if (wireMockSimulator != null) {
                wireMockSimulator.stop();
            }
            
        } catch (Exception e) {
            log.error("Error during test cleanup", e);
        }
        
        super.teardownTest();
    }
    
    /**
     * Tests DELAYED request to IMMEDIATE Provider (broker acts as proxy).
     * 
     * <p><strong>Validates: Requirements 1.2, 1.5</strong></p>
     * <p><strong>Property 1: Message exchange tests verify persistence</strong></p>
     * 
     * <p>Test scenario:
     * <ol>
     *   <li>Consumer creates a queue for receiving responses</li>
     *   <li>Provider is registered with IMMEDIATE pattern support only</li>
     *   <li>Consumer sends DELAYED request with queueId</li>
     *   <li>Broker receives request and returns 202 Accepted</li>
     *   <li>Broker acts as proxy: calls Provider synchronously via WireMock</li>
     *   <li>Broker receives Provider response and enqueues it to Consumer's queue</li>
     *   <li>Consumer polls their queue and retrieves the response</li>
     *   <li>Consumer acknowledges the message</li>
     * </ol>
     */
    @Test
    @DisplayName("DELAYED request to IMMEDIATE Provider returns response via Consumer queue")
    public void testDelayedRequestToImmediateProvider_ReturnsResponseViaQueue() {
        log.info("Starting test: DELAYED request to IMMEDIATE Provider");
        
        // Given: Consumer creates a queue for receiving responses
        UUID consumerQueueId = queueGenerator.createQueue(
            consumerEnvironment,
            "consumer-response-queue",
            ZONE_ID,
            CONTEXT_ID
        );
        log.info("Consumer queue created: queueId={}", consumerQueueId);
        
        // And: Provider is registered with IMMEDIATE pattern support only
        providerRegistration = providerGenerator.registerProvider(
            UUID.fromString(providerEnvironment.getId()),
            SERVICE_NAME,
            ZONE_ID,
            CONTEXT_ID,
            wireMockSimulator.getBaseUrl() + "/" + SERVICE_NAME,
            Set.of(MessageExchangePattern.IMMEDIATE),
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Provider registered: id={}, endpoint={}, supportedRequestTypes={}",
            providerRegistration.id(), ProtocolDTO.locationOf(providerRegistration.endPoint()), providerRegistration.supportedRequestTypes());
        
        // And: WireMock stub configured for synchronous response
        String studentPayload = messageGenerator.generateStudentPersonalResponse("12345");
        String sifEnvelopeResponse = messageGenerator.generateSifEnvelope(
            UUID.randomUUID().toString(),
            "RESPONSE",
            SERVICE_NAME,
            "OBJECT",
            ZONE_ID,
            CONTEXT_ID,
            studentPayload
        );
        
        wireMockSimulator.getWireMockServer().stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/" + SERVICE_NAME))
                .willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(sifEnvelopeResponse))
        );
        log.info("WireMock stub configured for IMMEDIATE response");
        
        // And: Request payload
        String requestPayload = messageGenerator.generateStudentPersonalRequest("12345");
        
        // When: Consumer sends DELAYED request with queueId
        Response response = given()
            .spec(sifRequest(
                consumerEnvironment.getSessionToken(),
                consumerEnvironment.getSharedSecret(),
                ZONE_ID,
                CONTEXT_ID
            ))
            .header("requestType", "DELAYED")
            .header("queueId", consumerQueueId.toString())
            .body(requestPayload)
        .when()
            .post(gatewayPathPrefix() + "/requests/" + SERVICE_NAME)
        .then()
            .log().ifValidationFails()
            .extract().response();
        
        log.info("DELAYED request sent, response status: {}", response.getStatusCode());
        
        // Then: Response status is 202 Accepted
        assertThat(response.getStatusCode())
            .as("Response should be 202 Accepted for DELAYED request")
            .isEqualTo(202);
        
        // And: Wait for async processing (proxy worker polls Redis, calls provider, enqueues response)
        try {
            Thread.sleep(5000); // 5 seconds for proxy worker to process and enqueue message
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // And: WireMock received the request (broker acted as proxy)
        wireMockSimulator.verifyRequestReceived("/" + SERVICE_NAME);
        log.info("WireMock verification passed - broker acted as proxy");
        
        // Wait for response routing chain: proxy worker → responses-connector → queue-service
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // And: Consumer polls their queue and retrieves the response
        Response pollResponse = queueGenerator.pollQueue(
            consumerQueueId,
            1,
            consumerEnvironment
        );
        
        log.info("Queue polled, response: {}", pollResponse.getBody().asString());
        
        // Then: Queue contains the response message
        assertThat(pollResponse.getStatusCode())
            .as("Consumer queue should contain 1 message")
            .isEqualTo(200);
        
        // And: Response message contains expected data
        String messagePayload = pollResponse.getBody().asString();
        assertThat(messagePayload)
            .as("Response message should contain expected student data")
            .contains("12345");
        
        log.info("Response message validated successfully");
        
        // And: Consumer acknowledges the message
        UUID messageId = UUID.fromString(pollResponse.getHeader("messageid"));
        queueGenerator.acknowledgeMessage(
            consumerQueueId,
            messageId,
            consumerEnvironment.getSessionToken(),
            consumerEnvironment.getSharedSecret()
        );
        log.info("Message acknowledged: messageId={}", messageId);
        
        // And: Verify message is removed from queue after acknowledgment
        Response pollAfterAck = queueGenerator.pollQueue(
            consumerQueueId,
            1,
            consumerEnvironment
        );
        
        assertThat(pollAfterAck.getStatusCode())
            .as("Queue should be empty after message acknowledgment")
            .isEqualTo(204);
        
        log.info("Test completed successfully: DELAYED request to IMMEDIATE Provider");
        
        // Clean up queue
        queueGenerator.deleteQueue(
            consumerQueueId,
            consumerEnvironment.getSessionToken(),
            consumerEnvironment.getSharedSecret()
        );
    }
    
    /**
     * Tests DELAYED request to DELAYED Provider (true async with queues).
     * 
     * <p><strong>Validates: Requirements 1.3, 1.5</strong></p>
     * <p><strong>Property 1: Message exchange tests verify persistence</strong></p>
     * 
     * <p>Test scenario:
     * <ol>
     *   <li>Consumer creates a queue for receiving responses</li>
     *   <li>Provider creates a queue for receiving requests</li>
     *   <li>Provider creates a DELAYED_REQUEST subscription linking their queue to the service</li>
     *   <li>Provider is registered with DELAYED pattern support</li>
     *   <li>Consumer sends DELAYED request with queueId</li>
     *   <li>Broker receives request and returns 202 Accepted</li>
     *   <li>Broker places request on Provider's request queue (via subscription)</li>
     *   <li>Provider polls their queue and retrieves the request</li>
     *   <li>Provider processes request and sends response to Responses Connector</li>
     *   <li>Responses Connector places response on Consumer's queue</li>
     *   <li>Consumer polls their queue and retrieves the response</li>
     *   <li>Consumer acknowledges the message</li>
     * </ol>
     */
    @Test
    @DisplayName("DELAYED request to DELAYED Provider returns response via Consumer queue")
    public void testDelayedRequestToDelayedProvider_ReturnsResponseViaQueue() {
        log.info("Starting test: DELAYED request to DELAYED Provider");
        
        // Initialize subscription generator
        SubscriptionGenerator subscriptionGenerator = new SubscriptionGenerator(getSubscriptionServiceUrl());
        
        // Given: Consumer creates a queue for receiving responses
        UUID consumerQueueId = queueGenerator.createQueue(
            consumerEnvironment,
            "consumer-response-queue",
            ZONE_ID,
            CONTEXT_ID
        );
        log.info("Consumer queue created: queueId={}", consumerQueueId);
        
        // And: Provider creates a queue for receiving requests
        UUID providerQueueId = queueGenerator.createQueue(
            providerEnvironment,
            "provider-request-queue",
            ZONE_ID,
            CONTEXT_ID
        );
        log.info("Provider queue created: queueId={}", providerQueueId);
        
        // And: Provider creates a DELAYED_REQUEST subscription
        SubscriptionDTO subscription = subscriptionGenerator.createDelayedRequestSubscription(
            providerQueueId,
            SERVICE_NAME,
            null // wildcard context
        );
        log.info("Provider subscription created: id={}, queueId={}, serviceName={}",
            subscription.id(), subscription.queueId(), subscription.serviceName());
        
        // And: Provider is registered with DELAYED pattern support
        providerRegistration = providerGenerator.registerProvider(
            UUID.fromString(providerEnvironment.getId()),
            SERVICE_NAME,
            ZONE_ID,
            CONTEXT_ID,
            "http://provider-endpoint.example.com/" + SERVICE_NAME, // Not used for DELAYED pattern
            Set.of(MessageExchangePattern.DELAYED),
            adminConfig.getSessionToken(),
            adminConfig.getSharedSecret()
        );
        log.info("Provider registered: id={}, endpoint={}, supportedRequestTypes={}",
            providerRegistration.id(), ProtocolDTO.locationOf(providerRegistration.endPoint()), providerRegistration.supportedRequestTypes());
        
        // And: Request payload
        String requestPayload = messageGenerator.generateStudentPersonalRequest("67890");
        
        // When: Consumer sends DELAYED request with queueId
        Response response = given()
            .spec(sifRequest(
                consumerEnvironment.getSessionToken(),
                consumerEnvironment.getSharedSecret(),
                ZONE_ID,
                CONTEXT_ID
            ))
            .header("requestType", "DELAYED")
            .header("queueId", consumerQueueId.toString())
            .body(requestPayload)
        .when()
            .post(gatewayPathPrefix() + "/requests/" + SERVICE_NAME)
        .then()
            .log().ifValidationFails()
            .extract().response();
        
        log.info("DELAYED request sent, response status: {}", response.getStatusCode());
        
        // Then: Response status is 202 Accepted
        assertThat(response.getStatusCode())
            .as("Response should be 202 Accepted for DELAYED request")
            .isEqualTo(202);
        
        // And: Wait for async processing (request should be enqueued to provider's queue)
        try {
            Thread.sleep(2000); // 2 seconds for message to be enqueued
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // And: Provider polls their queue and retrieves the request
        Response providerPollResponse = queueGenerator.pollQueue(
            providerQueueId,
            1,
            providerEnvironment
        );
        
        log.info("Provider queue polled, response: {}", providerPollResponse.getBody().asString());
        
        // Then: Provider queue contains the request message
        assertThat(providerPollResponse.getStatusCode())
            .as("Provider queue should contain 1 message")
            .isEqualTo(200);
        
        // And: Request message contains expected data
        String providerMessagePayload = providerPollResponse.getBody().asString();
        assertThat(providerMessagePayload)
            .as("Request message should contain expected student data")
            .contains("67890");
        
        log.info("Request message validated successfully on provider queue");
        
        // And: Provider acknowledges the request message
        UUID providerMessageId = UUID.fromString(providerPollResponse.getHeader("messageid"));
        queueGenerator.acknowledgeMessage(
            providerQueueId,
            providerMessageId,
            providerEnvironment.getSessionToken(),
            providerEnvironment.getSharedSecret()
        );
        log.info("Provider acknowledged request message: messageId={}", providerMessageId);
        
        // And: Provider processes request and sends response to Responses Connector
        String studentPayload = messageGenerator.generateStudentPersonalResponse("67890");
        String responseDto = messageGenerator.generateResponseDto(
            SERVICE_NAME,
            "OBJECT",
            studentPayload
        );
        
        // Provider sends response to Responses Connector with consumer's queueId
        Response providerResponseSubmission = given()
            .spec(sifRequest(
                providerEnvironment.getSessionToken(),
                providerEnvironment.getSharedSecret(),
                ZONE_ID,
                CONTEXT_ID
            ))
            .header("queueId", consumerQueueId.toString())
            .body(responseDto)
        .when()
            .post(gatewayPathPrefix() + "/responses/" + SERVICE_NAME)
        .then()
            .log().ifValidationFails()
            .extract().response();
        
        log.info("Provider submitted response, status: {}", providerResponseSubmission.getStatusCode());
        
        // Then: Response submission is accepted
        assertThat(providerResponseSubmission.getStatusCode())
            .as("Response submission should be 202 Accepted")
            .isEqualTo(202);
        
        // And: Wait for response to be enqueued to consumer's queue
        try {
            Thread.sleep(2000); // 2 seconds for response to be enqueued
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // And: Consumer polls their queue and retrieves the response
        Response consumerPollResponse = queueGenerator.pollQueue(
            consumerQueueId,
            1,
            consumerEnvironment
        );
        
        log.info("Consumer queue polled, response: {}", consumerPollResponse.getBody().asString());
        
        // Then: Consumer queue contains the response message
        assertThat(consumerPollResponse.getStatusCode())
            .as("Consumer queue should contain 1 message")
            .isEqualTo(200);
        
        // And: Response message contains expected data
        String consumerMessagePayload = consumerPollResponse.getBody().asString();
        assertThat(consumerMessagePayload)
            .as("Response message should contain expected student data")
            .contains("67890");
        
        log.info("Response message validated successfully on consumer queue");
        
        // And: Consumer acknowledges the message
        UUID consumerMessageId = UUID.fromString(consumerPollResponse.getHeader("messageid"));
        queueGenerator.acknowledgeMessage(
            consumerQueueId,
            consumerMessageId,
            consumerEnvironment.getSessionToken(),
            consumerEnvironment.getSharedSecret()
        );
        log.info("Consumer acknowledged response message: messageId={}", consumerMessageId);
        
        // And: Verify both queues are empty after acknowledgments
        Response providerPollAfterAck = queueGenerator.pollQueue(
            providerQueueId,
            1,
            providerEnvironment
        );
        Response consumerPollAfterAck = queueGenerator.pollQueue(
            consumerQueueId,
            1,
            consumerEnvironment
        );
        
        assertThat(providerPollAfterAck.getStatusCode())
            .as("Provider queue should be empty after message acknowledgment")
            .isEqualTo(204);
        assertThat(consumerPollAfterAck.getStatusCode())
            .as("Consumer queue should be empty after message acknowledgment")
            .isEqualTo(204);
        
        log.info("Test completed successfully: DELAYED request to DELAYED Provider");
        
        // Clean up
        subscriptionGenerator.deleteSubscription(subscription.id());
        queueGenerator.deleteQueue(
            providerQueueId,
            providerEnvironment.getSessionToken(),
            providerEnvironment.getSharedSecret()
        );
        queueGenerator.deleteQueue(
            consumerQueueId,
            consumerEnvironment.getSessionToken(),
            consumerEnvironment.getSharedSecret()
        );
    }
}
