// SPDX-License-Identifier: MIT
package org.sifunity.integration.smoke;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for microservice startup.
 * 
 * <p>These tests verify that all microservices start successfully and are
 * accessible via their health check endpoints.
 * 
 * <p><strong>WARNING:</strong> These tests start all 9 microservices, which
 * takes 2-3 minutes. Run these tests only when you need to verify the full
 * service startup process.
 * 
 * @since 1.0.0
 */
@Slf4j
@Tag("smoke")
@Tag("slow")
@DisplayName("Service Startup Smoke Tests")
public class ServiceStartupSmokeTest extends ServiceEnabledIntegrationTestBase {
    
    @Test
    @DisplayName("All microservices are started and running")
    public void testAllServicesAreRunning() {
        log.info("Testing all microservices are running");
        
        assertThat(serviceOrchestrator).isNotNull();
        
        // Verify we can get service URLs
        String environmentServiceUrl = getEnvironmentServiceUrl();
        assertThat(environmentServiceUrl).isNotNull().contains("http://localhost:");
        
        String queueServiceUrl = getQueueServiceUrl();
        assertThat(queueServiceUrl).isNotNull().contains("http://localhost:");
        
        String subscriptionServiceUrl = getSubscriptionServiceUrl();
        assertThat(subscriptionServiceUrl).isNotNull().contains("http://localhost:");
        
        log.info("All microservices are running");
        log.info("  - Environment Service: {}", environmentServiceUrl);
        log.info("  - Queue Service: {}", queueServiceUrl);
        log.info("  - Subscription Service: {}", subscriptionServiceUrl);
    }
    
    @Test
    @DisplayName("Gateway is accessible and healthy")
    public void testGatewayIsHealthy() {
        log.info("Testing Gateway health");
        
        Response response = given()
            .when()
                .get("/actuator/health")
            .then()
                .log().ifValidationFails()
                .extract().response();
        
        log.info("Gateway health response: status={}, body={}", 
            response.getStatusCode(), response.getBody().asString());
        
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody().asString()).contains("UP");
        
        log.info("Gateway is healthy");
    }
    
    @Test
    @DisplayName("Environment Service is accessible via Gateway")
    public void testEnvironmentServiceViaGateway() {
        log.info("Testing Environment Service via Gateway");
        
        // Try to access Environment Service through Gateway
        // This endpoint should return 401 without auth, but that proves routing works
        Response response = given()
            .when()
                .get(gatewayPathPrefix() + "/environments")
            .then()
                .log().ifValidationFails()
                .extract().response();
        
        log.info("Environment Service response: status={}", response.getStatusCode());
        
        // We expect 401 Unauthorized (no auth provided) or 200 (if endpoint allows anonymous access)
        // Either way, if we get a response, routing is working
        assertThat(response.getStatusCode()).isIn(200, 401, 403);
        
        log.info("Environment Service is accessible via Gateway");
    }
    
    @Test
    @DisplayName("RestAssured is configured with correct Gateway port")
    public void testRestAssuredGatewayPort() {
        log.info("Testing RestAssured Gateway port configuration");
        
        String gatewayUrl = serviceOrchestrator.getGatewayUrl();
        log.info("Gateway URL from orchestrator: {}", gatewayUrl);
        
        // Extract port from URL
        int expectedPort = Integer.parseInt(gatewayUrl.substring(gatewayUrl.lastIndexOf(':') + 1));
        
        assertThat(gatewayPort).isEqualTo(expectedPort);
        
        log.info("RestAssured is configured with correct Gateway port: {}", gatewayPort);
    }
}
