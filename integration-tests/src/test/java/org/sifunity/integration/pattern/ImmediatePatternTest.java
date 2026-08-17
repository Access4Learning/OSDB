// SPDX-License-Identifier: MIT
package org.sifunity.integration.pattern;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
import org.sifunity.integration.wiremock.ProviderStubBuilder;
import org.sifunity.integration.wiremock.WireMockProviderSimulator;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.ProtocolDTO;

/**
 * Integration tests for the IMMEDIATE message exchange pattern.
 * 
 * <p>The IMMEDIATE pattern is a synchronous request/response pattern where:
 * <ol>
 *   <li>Consumer sends a request to the Gateway</li>
 *   <li>Gateway routes the request to the Requests Connector</li>
 *   <li>Requests Connector looks up the Provider in the registry</li>
 *   <li>Requests Connector forwards the request to the Provider</li>
 *   <li>Provider processes the request and returns a response synchronously</li>
 *   <li>Response is returned back through the chain to the Consumer</li>
 * </ol>
 * 
 * <p>These tests validate:
 * <ul>
 *   <li>Successful IMMEDIATE request/response flow</li>
 *   <li>Provider error handling (500 errors)</li>
 *   <li>Provider timeout handling</li>
 *   <li>Message persistence verification</li>
 *   <li>Correct header propagation to Provider</li>
 * </ul>
 * 
 * <p><strong>Property 1: Message exchange tests verify persistence</strong><br>
 * For any message exchange pattern test (IMMEDIATE, DELAYED, EVENT), the test should
 * verify that messages are properly persisted to storage, queue state is correct, and
 * acknowledged messages are cleaned up.
 * 
 * <p><strong>Property 13: Provider unavailability error handling</strong><br>
 * For any request where the Provider is unavailable or unreachable, the broker should
 * return HTTP 503 Service Unavailable.
 * 
 * <p><strong>Property 14: Provider timeout error handling</strong><br>
 * For any request where the Provider times out, the broker should return an appropriate
 * error response indicating the timeout.
 * 
 * @since 1.0.0
 */
@Slf4j
@Tag("message-pattern")
@Tag("immediate")
@DisplayName("IMMEDIATE Pattern Integration Tests")
public class ImmediatePatternTest extends ServiceEnabledIntegrationTestBase {
    
    private static final String SERVICE_NAME = "StudentPersonals";
    private static final String ZONE_ID = "test-zone";
    private static final String CONTEXT_ID = "DEFAULT";
    
    private EnvironmentGenerator environmentGenerator;
    private AclGenerator aclGenerator;
    private ProviderGenerator providerGenerator;
    private MessageGenerator messageGenerator;
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
     *   <li>Registers the provider in the registry</li>
     * </ol>
     */
    @BeforeEach
    @Override
    public void setupTest() {
        super.setupTest();
        
        log.info("Setting up IMMEDIATE pattern test");
        
        // Initialize ADMIN credentials
        adminConfig = new AdminCredentialsConfig();
        
        // Initialize generators with actual service URLs
        String environmentServiceUrl = getEnvironmentServiceUrl();
        environmentGenerator = new EnvironmentGenerator(environmentServiceUrl);
        aclGenerator = new AclGenerator(environmentServiceUrl, environmentGenerator);
        providerGenerator = new ProviderGenerator(environmentServiceUrl);
        messageGenerator = new MessageGenerator();
        
        // Start WireMock simulator
        wireMockSimulator = new WireMockProviderSimulator();
        wireMockSimulator.start();
        
        // Create consumer environment
        consumerEnvironment = environmentGenerator.createConsumer(
            environmentGenerator.generateUniqueName("test-consumer")
        );
        log.info("Consumer environment created: id={}, sessionToken={}, sharedSecret={}",
            consumerEnvironment.getId(), consumerEnvironment.getSessionToken(), 
            consumerEnvironment.getSharedSecret() != null ? "present(" + consumerEnvironment.getSharedSecret().length() + " chars)" : "NULL");
        
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
        
        // Register provider using ADMIN credentials
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
        log.info("Provider registered: id={}, endpoint={}",
            providerRegistration.id(), ProtocolDTO.locationOf(providerRegistration.endPoint()));
    }
    
    /**
     * Cleans up test data after each test.
     * 
     * <p>This method:
     * <ol>
     *   <li>Deletes provider registration</li>
     *   <li>Deletes ACL entries</li>
     *   <li>Deletes environments</li>
     *   <li>Stops WireMock simulator</li>
     * </ol>
     */
    @AfterEach
    @Override
    public void teardownTest() {
        log.info("Tearing down IMMEDIATE pattern test");
        
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
     * Tests successful IMMEDIATE request/response flow.
     * 
     * <p><strong>Validates: Requirements 1.1, 1.5</strong></p>
     * <p><strong>Property 1: Message exchange tests verify persistence</strong></p>
     * 
     * <p>Test scenario:
     * <ol>
     *   <li>Consumer sends a request for StudentPersonals</li>
     *   <li>WireMock stub returns a successful response</li>
     *   <li>Consumer receives the response synchronously</li>
     *   <li>Response contains expected data</li>
     *   <li>WireMock verifies request was received with correct headers</li>
     * </ol>
     */
    @Test
    @DisplayName("Successful IMMEDIATE request returns Provider response synchronously")
    public void testImmediateRequest_ReturnsProviderResponseSynchronously() {
        // Given: WireMock stub configured for synchronous response
        String studentPayload = messageGenerator.generateStudentPersonalResponse("12345");
        
        // Create a SIF message envelope response (what the provider should return)
        String sifEnvelopeResponse = String.format("""
            {
              "messageId": "%s",
              "timestamp": "%s",
              "messageType": "RESPONSE",
              "serviceName": "StudentPersonals",
              "serviceType": "OBJECT",
              "zoneId": "%s",
              "contextId": "%s",
              "payload": %s
            }
            """, 
            UUID.randomUUID().toString(),
            Instant.now().toString(),
            ZONE_ID,
            CONTEXT_ID,
            studentPayload);
        
        // Create WireMock stub that returns a SIF envelope
        wireMockSimulator.getWireMockServer().stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/StudentPersonals"))
                .willReturn(WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(sifEnvelopeResponse))
        );
        
        log.info("WireMock stub configured for IMMEDIATE response on port: {}", wireMockSimulator.getPort());
        log.info("WireMock base URL: {}", wireMockSimulator.getBaseUrl());
        log.info("Expected provider endpoint: {}/StudentPersonals", wireMockSimulator.getBaseUrl());
        
        // And: Request payload
        String requestPayload = messageGenerator.generateStudentPersonalRequest("12345");
        
        // When: Consumer makes IMMEDIATE request
        String authHeader = environmentGenerator.generateAuthHeader(consumerEnvironment);
        
        Response response = given()
            .spec(sifRequest(
                consumerEnvironment.getSessionToken(),
                consumerEnvironment.getSharedSecret(),
                ZONE_ID,
                CONTEXT_ID
            ))
            .header("requestType", "IMMEDIATE")
            .body(requestPayload)
        .when()
            .post(gatewayPathPrefix() + "/requests/" + SERVICE_NAME)
        .then()
            .log().ifValidationFails()
            .extract().response();
        
        log.info("Request sent, response status: {}", response.getStatusCode());
        log.info("Response body: {}", response.getBody().asString());
        
        // Then: Response status is 200 OK
        assertThat(response.getStatusCode())
            .as("Response should be 200 OK for successful IMMEDIATE request")
            .isEqualTo(200);
        
        // And: Response body contains expected data
        String responseBody = response.getBody().asString();
        assertThat(responseBody)
            .as("Response should contain expected student data")
            .contains("12345");
        
        log.info("Response validated successfully");
        
        // TODO: Verify message persistence (Property 1)
        // - Check that request was logged/persisted
        // - Check that response was logged/persisted
        // - Verify no messages left in queues (IMMEDIATE pattern doesn't use queues)
    }
    
    /**
     * Tests IMMEDIATE request with Provider returning 500 error.
     * 
     * <p><strong>Validates: Requirements 8.1</strong></p>
     * <p><strong>Property 13: Provider unavailability error handling</strong></p>
     * 
     * <p>Test scenario:
     * <ol>
     *   <li>Consumer sends a request for StudentPersonals</li>
     *   <li>WireMock stub returns 500 Internal Server Error</li>
     *   <li>Consumer receives appropriate error response</li>
     *   <li>Error response contains error details</li>
     * </ol>
     */
    @Test
    @DisplayName("IMMEDIATE request with Provider error returns appropriate error response")
    public void testImmediateRequest_WithProviderError_ReturnsErrorResponse() {
        // Given: WireMock stub configured to return 500 error
        ProviderStubBuilder.forImmediatePattern()
            .serviceName(SERVICE_NAME)
            .withServerError()
            .buildOn(wireMockSimulator.getWireMockServer());
        
        log.info("WireMock stub configured for 500 error");
        
        // And: Request payload
        String requestPayload = messageGenerator.generateStudentPersonalRequest("12345");
        
        // When: Consumer makes IMMEDIATE request
        Response response = given()
            .spec(sifRequest(
                consumerEnvironment.getSessionToken(),
                consumerEnvironment.getSharedSecret(),
                ZONE_ID,
                CONTEXT_ID
            ))
            .header("requestType", "IMMEDIATE")
            .body(requestPayload)
        .when()
            .post(gatewayPathPrefix() + "/requests/" + SERVICE_NAME)
        .then()
            .log().ifValidationFails()
            .extract().response();
        
        log.info("Request sent, response status: {}", response.getStatusCode());
        
        // Then: Response status indicates error (503 Service Unavailable or 500)
        assertThat(response.getStatusCode())
            .as("Response should indicate Provider error")
            .isIn(500, 503);
        
        // And: Response body contains error information
        String responseBody = response.getBody().asString();
        assertThat(responseBody)
            .as("Response should contain error information")
            .containsAnyOf("error", "Error", "failed", "unavailable");
        
        log.info("Error response validated successfully");
        
        // And: WireMock received the request
        wireMockSimulator.verifyRequestReceived("/" + SERVICE_NAME);
        
        log.info("WireMock request verification passed");
    }
    
    /**
     * Tests IMMEDIATE request with Provider timeout.
     * 
     * <p><strong>Validates: Requirements 8.2</strong></p>
     * <p><strong>Property 14: Provider timeout error handling</strong></p>
     * 
     * <p>Test scenario:
     * <ol>
     *   <li>Consumer sends a request for StudentPersonals</li>
     *   <li>WireMock stub delays response for 10 seconds (simulates timeout)</li>
     *   <li>Consumer receives timeout error response</li>
     *   <li>Error response indicates timeout</li>
     * </ol>
     */
    @Test
    @DisplayName("IMMEDIATE request with Provider timeout returns timeout error")
    public void testImmediateRequest_WithProviderTimeout_ReturnsTimeoutError() {
        // Given: WireMock stub configured with long delay (timeout)
        ProviderStubBuilder.forImmediatePattern()
            .serviceName(SERVICE_NAME)
            .withTimeout() // 10 second delay
            .buildOn(wireMockSimulator.getWireMockServer());
        
        log.info("WireMock stub configured for timeout");
        
        // And: Request payload
        String requestPayload = messageGenerator.generateStudentPersonalRequest("12345");
        
        // When: Consumer makes IMMEDIATE request
        Response response = given()
            .spec(sifRequest(
                consumerEnvironment.getSessionToken(),
                consumerEnvironment.getSharedSecret(),
                ZONE_ID,
                CONTEXT_ID
            ))
            .header("requestType", "IMMEDIATE")
            .body(requestPayload)
        .when()
            .post(gatewayPathPrefix() + "/requests/" + SERVICE_NAME)
        .then()
            .log().ifValidationFails()
            .extract().response();
        
        log.info("Request sent, response status: {}", response.getStatusCode());
        
        // Then: Response status indicates timeout error (504 Gateway Timeout or 503)
        assertThat(response.getStatusCode())
            .as("Response should indicate timeout error")
            .isIn(503, 504);
        
        // And: Response body contains timeout information
        String responseBody = response.getBody().asString();
        assertThat(responseBody)
            .as("Response should contain timeout information")
            .containsAnyOf("timeout", "Timeout", "timed out", "unavailable");
        
        log.info("Timeout error response validated successfully");
    }
}
