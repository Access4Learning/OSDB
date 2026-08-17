// SPDX-License-Identifier: MIT
package org.sifunity.gateway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.sifunity.gateway.ratelimit.RateLimitResponse;
import org.sifunity.gateway.ratelimit.RedisRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Base class for integration tests.
 * 
 * <p>This class provides common setup for integration tests including:
 * <ul>
 *   <li>Mocked RedisRateLimiter for rate limiting tests</li>
 *   <li>WireMock server for mocking Environment Service</li>
 *   <li>WebTestClient for making HTTP requests</li>
 *   <li>ObjectMapper for JSON serialization</li>
 * </ul>
 * 
 * <p>All integration tests should extend this class to inherit the common setup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
    
    @Autowired
    protected WebTestClient webTestClient;
    
    @Autowired
    protected ObjectMapper objectMapper;
    
    @MockBean
    protected RedisRateLimiter redisRateLimiter;
    
    protected static WireMockServer wireMockServer;
    
    @BeforeAll
    static void startWireMock() {
        // Initialize and start WireMock server before any tests run
        wireMockServer = new WireMockServer(com.github.tomakehurst.wiremock.core.WireMockConfiguration
            .options()
            .port(8089)
            .notifier(new com.github.tomakehurst.wiremock.common.ConsoleNotifier(true)));
        wireMockServer.start();
        
        // Verify server is running
        if (!wireMockServer.isRunning()) {
            throw new IllegalStateException("WireMock server failed to start");
        }
        
        // Configure WireMock client
        WireMock.configureFor("localhost", 8089);
    }
    
    @AfterAll
    static void stopWireMock() {
        // Clean up WireMock server after all tests complete
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Configure environment service URL to point to WireMock server
        registry.add("environment-service.url", () -> "http://localhost:8089");
    }
    
    @BeforeEach
    void setUp() {
        // Reset WireMock stubs before each test
        if (wireMockServer != null) {
            wireMockServer.resetAll();
        }
        
        // Default behavior: allow all requests (rate limit not exceeded)
        when(redisRateLimiter.isAllowed(anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(true, 0)));
        when(redisRateLimiter.isAllowed(anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(Mono.just(new RateLimitResponse(true, 0)));
        
        // Mock Environment Service health check to return UP
        wireMockServer.stubFor(get(urlEqualTo("/actuator/health"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"UP\"}")));
    }
    
    @AfterEach
    void tearDown() {
        // Reset WireMock stubs after each test (server remains running)
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
        }
    }
}
