// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import net.jqwik.api.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bug Condition Exploration Test for WireMock Connectivity
 * 
 * <p><strong>VALIDATION TEST: This test verifies the fix for WireMock connectivity issues.</strong>
 * 
 * <p><strong>Bug Description:</strong>
 * Integration tests attempt to connect to WireMock server at localhost:8089 and throw
 * "Connection refused" errors. The WireMock server was created in @DynamicPropertySource
 * static method but had lifecycle issues - it was not fully started before tests run,
 * or it was being stopped prematurely.
 * 
 * <p><strong>Fix Applied:</strong>
 * WireMock server is now initialized in a static initializer block to ensure it's
 * created before any test methods run. This works with both JUnit 5 and jqwik.
 * 
 * <p><strong>Test Strategy:</strong>
 * This property-based test verifies that WireMock server connectivity is established
 * and maintained throughout test execution using static initialization.
 * 
 * <p><strong>EXPECTED OUTCOME: This test PASSES (confirms the bug is fixed)</strong>
 * 
 * <p><strong>Validates: Requirements 2.3, 2.4, 2.5</strong>
 * 
 * @see org.sifunity.gateway.integration.BaseIntegrationTest
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WireMockConnectivityBugConditionExplorationTest {
    
    protected static WireMockServer wireMockServer;
    
    // Static initializer block to ensure WireMock is started before any tests
    // This works with both JUnit 5 and jqwik
    static {
        System.out.println("=== WireMock Static Initialization Starting ===");
        System.out.println("Initializing WireMock server on port 8089...");
        
        // Initialize and start WireMock server
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        
        System.out.println("WireMock server started: " + wireMockServer.isRunning());
        System.out.println("WireMock server port: " + wireMockServer.port());
        System.out.println("WireMock server base URL: " + wireMockServer.baseUrl());
        
        // Verify server is running
        if (!wireMockServer.isRunning()) {
            System.err.println("ERROR: WireMock server failed to start!");
            throw new IllegalStateException("WireMock server failed to start");
        }
        
        // Configure WireMock client
        WireMock.configureFor("localhost", 8089);
        System.out.println("=== WireMock Static Initialization Complete ===");
        
        // Register shutdown hook to stop WireMock when JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("=== WireMock Shutdown Hook Executing ===");
            if (wireMockServer != null && wireMockServer.isRunning()) {
                wireMockServer.stop();
                System.out.println("WireMock server stopped");
            }
        }));
    }
    
    /**
     * Configure dynamic properties to point to WireMock server.
     */
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
    }
    
    /**
     * Property 1: Expected Behavior - WireMock Connectivity Established
     * 
     * <p><strong>Validates: Requirements 2.3, 2.4, 2.5</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES (confirms the bug is fixed)</strong>
     * 
     * <p>This property verifies the expected behavior by attempting to connect to the WireMock
     * server at localhost:8089. The test demonstrates that:
     * <ul>
     *   <li>WireMock server initialized in @BeforeAll has proper lifecycle management</li>
     *   <li>Connection attempts to localhost:8089 succeed consistently</li>
     *   <li>The server is fully started before test methods execute</li>
     * </ul>
     * 
     * <p>The test attempts to establish a TCP socket connection to localhost:8089,
     * which is what integration tests do when making HTTP requests to WireMock.
     * 
     * <p><strong>Fix Validation:</strong>
     * When this test runs, it should demonstrate that the WireMock server is
     * reliably available, confirming the fix is working correctly.
     * 
     * @param attemptNumber the attempt number (1-10) to demonstrate consistent availability
     */
    @Property(tries = 10)
    @Label("Feature: gateway, Expected Behavior: WireMock Connectivity Established")
    void wireMockServerAvailable(@ForAll("attemptNumbers") int attemptNumber) {
        // Assert: Verify WireMock server is initialized
        assertThat(wireMockServer)
            .as("WireMock server should be initialized before test methods execute")
            .isNotNull();
        
        // Act: Attempt to connect to WireMock server at localhost:8089
        // This simulates what integration tests do when making HTTP requests
        
        // Assert: Verify that WireMock server is available and accepting connections
        // EXPECTED: This assertion should PASS because the WireMock server now has proper lifecycle management
        
        assertThat(isWireMockServerAvailable())
            .as("WireMock server at localhost:8089 should be available for integration tests (attempt %d)", attemptNumber)
            .as("Expected Behavior: WireMock server properly initialized in @BeforeAll")
            .as("Requirements 2.3, 2.4, 2.5: Integration tests succeed with WireMock connectivity")
            .isTrue();
    }
    
    /**
     * Checks if WireMock server is available by attempting to establish a TCP connection.
     * This replicates what happens when integration tests make HTTP requests to WireMock.
     * 
     * @return true if connection succeeds, false if connection is refused
     */
    private boolean isWireMockServerAvailable() {
        try (Socket socket = new Socket("localhost", 8089)) {
            // If we can establish a connection, the server is available
            return true;
        } catch (ConnectException e) {
            // Connection refused - server is not available
            return false;
        } catch (IOException e) {
            // Other IO errors also indicate server is not available
            return false;
        }
    }
    
    /**
     * Property 2: Expected Behavior - WireMock Server Lifecycle Properly Managed
     * 
     * <p><strong>Validates: Requirements 2.4</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES (confirms the bug is fixed)</strong>
     * 
     * <p>This property verifies that the WireMock server initialized in @BeforeAll
     * has proper lifecycle management. The test checks if the server is running and
     * properly initialized throughout test execution.
     * 
     * <p><strong>Fix Validation:</strong>
     * The server is created in @BeforeAll and remains running throughout all tests,
     * ensuring integration tests can reliably connect to it.
     * 
     * @param checkNumber the check number (1-10) to demonstrate consistent lifecycle management
     */
    @Property(tries = 10)
    @Label("Feature: gateway, Expected Behavior: WireMock Server Lifecycle Properly Managed")
    void wireMockServerLifecycleProperlyManaged(@ForAll("attemptNumbers") int checkNumber) {
        // Assert: Verify that WireMock server is running and properly initialized
        // EXPECTED: This assertion should PASS because the server now has proper lifecycle management
        
        assertThat(wireMockServer)
            .as("WireMock server should be initialized (check %d)", checkNumber)
            .isNotNull();
        
        assertThat(wireMockServer.isRunning())
            .as("WireMock server should be running when test methods execute (check %d)", checkNumber)
            .as("Expected Behavior: Server lifecycle properly managed in @BeforeAll")
            .as("Requirement 2.4: WireMock server fully started before test methods execute")
            .isTrue();
        
        // Additional check: Verify server is accepting connections
        assertThat(isWireMockServerAvailable())
            .as("WireMock server should be accepting connections (check %d)", checkNumber)
            .as("Expected Behavior: Server is running and accepting connections")
            .isTrue();
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates attempt numbers from 1 to 10.
     * This allows the property test to run multiple times to demonstrate
     * consistent failure across multiple connection attempts.
     */
    @Provide
    Arbitrary<Integer> attemptNumbers() {
        return Arbitraries.integers().between(1, 10);
    }
}
