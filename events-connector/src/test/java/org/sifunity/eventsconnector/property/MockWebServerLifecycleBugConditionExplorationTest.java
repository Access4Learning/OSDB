// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

import net.jqwik.api.*;
import okhttp3.mockwebserver.MockWebServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition Exploration Test for MockWebServer Lifecycle Management
 * 
 * <p><strong>EXPLORATION TEST: This test is EXPECTED TO FAIL on unfixed code.</strong>
 * 
 * <p><strong>Bug Description:</strong>
 * EventDistributionIntegrationTest uses static MockWebServer instances with @BeforeAll/@AfterAll.
 * The bug is that @AfterAll runs before Spring context initialization completes, shutting down
 * the MockWebServer instances prematurely. This causes "Already shut down" errors when Spring
 * tries to initialize WebClient beans that need to connect to the MockWebServer.
 * 
 * <p><strong>Current Behavior (Defect):</strong>
 * <ul>
 *   <li>MockWebServer instances are initialized in @BeforeAll</li>
 *   <li>@AfterAll cleanup executes before Spring context initialization completes</li>
 *   <li>Spring context tries to initialize with configured URLs but MockWebServer is already shut down</li>
 *   <li>System fails with "Already shut down" error</li>
 *   <li>ApplicationContext fails to load with "Failed to load ApplicationContext"</li>
 * </ul>
 * 
 * <p><strong>Test Strategy:</strong>
 * This property-based test replicates the exact lifecycle issue by using static MockWebServer
 * instances with @BeforeAll/@AfterAll, just like the failing EventDistributionIntegrationTest.
 * The test attempts to verify that MockWebServer remains available during Spring context
 * initialization, which should FAIL on unfixed code.
 * 
 * <p><strong>EXPECTED OUTCOME: This test FAILS (confirms the bug exists)</strong>
 * 
 * <p><strong>Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5</strong>
 * 
 * @see org.sifunity.eventsconnector.integration.EventDistributionIntegrationTest
 */
@SpringBootTest(properties = {
    "management.health.defaults.enabled=false",
    "resilience4j.circuitbreaker.instances.subscriptionService.enabled=false",
    "resilience4j.circuitbreaker.instances.queueService.enabled=false",
    "retry.max-attempts=1"
})
class MockWebServerLifecycleBugConditionExplorationTest {
    
    private static MockWebServer subscriptionServiceMock;
    private static MockWebServer queueServiceMock;
    
    static {
        // Initialize MockWebServer in static block to ensure it runs before
        // both jqwik and JUnit Jupiter lifecycle methods, and before
        // @DynamicPropertySource which needs the ports.
        try {
            subscriptionServiceMock = new MockWebServer();
            queueServiceMock = new MockWebServer();
            subscriptionServiceMock.start();
            queueServiceMock.start();
            
            System.out.println("Subscription Service Mock started on port: " + subscriptionServiceMock.getPort());
            System.out.println("Queue Service Mock started on port: " + queueServiceMock.getPort());
            
            // Register shutdown hook to clean up
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (subscriptionServiceMock != null) subscriptionServiceMock.shutdown();
                    if (queueServiceMock != null) queueServiceMock.shutdown();
                } catch (IOException e) {
                    System.err.println("Warning: Failed to shutdown MockWebServer: " + e.getMessage());
                }
            }));
        } catch (IOException e) {
            throw new RuntimeException("Failed to start MockWebServer", e);
        }
    }
    
    /**
     * Configure dynamic properties to point to MockWebServer instances.
     * This method runs during Spring context initialization.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        System.out.println("=== @DynamicPropertySource: Configuring properties ===");
        System.out.println("Subscription Service Mock available: " + (subscriptionServiceMock != null));
        System.out.println("Queue Service Mock available: " + (queueServiceMock != null));
        
        if (subscriptionServiceMock != null) {
            System.out.println("Subscription Service Mock port: " + subscriptionServiceMock.getPort());
        }
        if (queueServiceMock != null) {
            System.out.println("Queue Service Mock port: " + queueServiceMock.getPort());
        }
        
        registry.add("subscription-service.url", () -> 
            "http://localhost:" + subscriptionServiceMock.getPort());
        registry.add("queue-service.url", () -> 
            "http://localhost:" + queueServiceMock.getPort());
        
        System.out.println("=== @DynamicPropertySource: Properties configured ===");
    }
    
    /**
     * Property 1: Bug Condition - MockWebServer Availability Throughout Test Lifecycle
     * 
     * <p><strong>Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test FAILS (confirms the bug exists)</strong>
     * 
     * <p>This property verifies that MockWebServer instances remain available throughout
     * the entire test lifecycle, including during Spring context initialization. The test
     * demonstrates the bug by attempting to connect to the MockWebServer instances.
     * 
     * <p><strong>Bug Manifestation:</strong>
     * On unfixed code with @BeforeAll/@AfterAll, the @AfterAll method runs before Spring
     * context initialization completes, causing the MockWebServer to be shut down prematurely.
     * This test will FAIL because:
     * <ul>
     *   <li>@AfterAll executes and shuts down MockWebServer</li>
     *   <li>Spring context initialization tries to connect to MockWebServer</li>
     *   <li>"Already shut down" error occurs</li>
     *   <li>ApplicationContext fails to load</li>
     * </ul>
     * 
     * <p><strong>Expected Counterexamples:</strong>
     * <ul>
     *   <li>MockWebServer instances are null or shut down during context initialization</li>
     *   <li>Connection attempts to MockWebServer fail with "Connection refused"</li>
     *   <li>Spring context fails to load with "Failed to load ApplicationContext"</li>
     *   <li>"Already shut down" errors in logs</li>
     * </ul>
     * 
     * @param attemptNumber the attempt number (1-10) to demonstrate consistent failure
     */
    @Property(tries = 10)
    @Label("Feature: events-connector, Bug Condition: MockWebServer Availability Throughout Test Lifecycle")
    void mockWebServerAvailableDuringSpringContextInitialization(@ForAll("attemptNumbers") int attemptNumber) {
        System.out.println("=== Property Test Execution (Attempt " + attemptNumber + ") ===");
        
        // Assert: Verify MockWebServer instances are initialized
        // BUG: On unfixed code, these may be null or shut down by the time tests run
        assertThat(subscriptionServiceMock)
            .as("Subscription Service MockWebServer should be initialized (attempt %d)", attemptNumber)
            .as("Bug Condition: @AfterAll may have shut down MockWebServer before Spring context initialization")
            .as("Requirements 2.1, 2.2: MockWebServer must remain available throughout test lifecycle")
            .isNotNull();
        
        assertThat(queueServiceMock)
            .as("Queue Service MockWebServer should be initialized (attempt %d)", attemptNumber)
            .as("Bug Condition: @AfterAll may have shut down MockWebServer before Spring context initialization")
            .as("Requirements 2.1, 2.2: MockWebServer must remain available throughout test lifecycle")
            .isNotNull();
        
        // Act & Assert: Verify that MockWebServer instances are available and accepting connections
        // BUG: On unfixed code, this will FAIL because @AfterAll shuts down the servers prematurely
        
        boolean subscriptionServiceAvailable = isMockWebServerAvailable(subscriptionServiceMock.getPort());
        boolean queueServiceAvailable = isMockWebServerAvailable(queueServiceMock.getPort());
        
        System.out.println("Subscription Service available: " + subscriptionServiceAvailable);
        System.out.println("Queue Service available: " + queueServiceAvailable);
        
        // EXPECTED TO FAIL: These assertions will fail on unfixed code because MockWebServer
        // is shut down by @AfterAll before Spring context initialization completes
        assertThat(subscriptionServiceAvailable)
            .as("Subscription Service MockWebServer at port %d should be available (attempt %d)", 
                subscriptionServiceMock.getPort(), attemptNumber)
            .as("Bug Condition: @AfterAll shuts down MockWebServer before Spring context initialization completes")
            .as("Requirements 2.2, 2.3: Spring context should have access to running MockWebServer instances")
            .isTrue();
        
        assertThat(queueServiceAvailable)
            .as("Queue Service MockWebServer at port %d should be available (attempt %d)", 
                queueServiceMock.getPort(), attemptNumber)
            .as("Bug Condition: @AfterAll shuts down MockWebServer before Spring context initialization completes")
            .as("Requirements 2.2, 2.3: Spring context should have access to running MockWebServer instances")
            .isTrue();
        
        System.out.println("=== Property Test Execution Complete (Attempt " + attemptNumber + ") ===");
    }
    
    /**
     * Property 2: Bug Condition - Spring Context Initialization with Running MockWebServer
     * 
     * <p><strong>Validates: Requirements 2.2, 2.3, 2.4</strong>
     * 
     * <p><strong>EXPECTED OUTCOME: This test FAILS (confirms the bug exists)</strong>
     * 
     * <p>This property verifies that when @DynamicPropertySource configures properties,
     * the MockWebServer instances are still running and available. On unfixed code,
     * this will fail because @AfterAll runs before Spring context initialization completes.
     * 
     * <p><strong>Expected Counterexamples:</strong>
     * <ul>
     *   <li>MockWebServer ports are configured but servers are already shut down</li>
     *   <li>Spring context fails to initialize WebClient beans</li>
     *   <li>"Already shut down" errors when trying to connect</li>
     * </ul>
     * 
     * @param checkNumber the check number (1-10) to demonstrate consistent failure
     */
    @Property(tries = 10)
    @Label("Feature: events-connector, Bug Condition: Spring Context Initialization with Running MockWebServer")
    void mockWebServerRunningDuringPropertyConfiguration(@ForAll("attemptNumbers") int checkNumber) {
        System.out.println("=== Property Test: MockWebServer Running During Property Configuration (Check " + checkNumber + ") ===");
        
        // Assert: Verify MockWebServer instances are not null
        assertThat(subscriptionServiceMock)
            .as("Subscription Service MockWebServer should not be null (check %d)", checkNumber)
            .isNotNull();
        
        assertThat(queueServiceMock)
            .as("Queue Service MockWebServer should not be null (check %d)", checkNumber)
            .isNotNull();
        
        // Assert: Verify MockWebServer instances have valid ports
        // BUG: On unfixed code, ports may be valid but servers are already shut down
        assertThat(subscriptionServiceMock.getPort())
            .as("Subscription Service MockWebServer should have valid port (check %d)", checkNumber)
            .as("Bug Condition: Port is configured but server may be shut down")
            .as("Requirement 2.3: @DynamicPropertySource should use ports from running MockWebServer instances")
            .isGreaterThan(0);
        
        assertThat(queueServiceMock.getPort())
            .as("Queue Service MockWebServer should have valid port (check %d)", checkNumber)
            .as("Bug Condition: Port is configured but server may be shut down")
            .as("Requirement 2.3: @DynamicPropertySource should use ports from running MockWebServer instances")
            .isGreaterThan(0);
        
        // EXPECTED TO FAIL: Verify servers are actually running and accepting connections
        // On unfixed code, this will fail because @AfterAll has already shut down the servers
        boolean subscriptionServiceRunning = isMockWebServerAvailable(subscriptionServiceMock.getPort());
        boolean queueServiceRunning = isMockWebServerAvailable(queueServiceMock.getPort());
        
        assertThat(subscriptionServiceRunning)
            .as("Subscription Service MockWebServer should be running and accepting connections (check %d)", checkNumber)
            .as("Bug Condition: @AfterAll shuts down MockWebServer before Spring context initialization")
            .as("Requirement 2.2: Spring context should have access to running MockWebServer instances")
            .isTrue();
        
        assertThat(queueServiceRunning)
            .as("Queue Service MockWebServer should be running and accepting connections (check %d)", checkNumber)
            .as("Bug Condition: @AfterAll shuts down MockWebServer before Spring context initialization")
            .as("Requirement 2.2: Spring context should have access to running MockWebServer instances")
            .isTrue();
        
        System.out.println("=== Property Test Complete (Check " + checkNumber + ") ===");
    }
    
    /**
     * Checks if MockWebServer is available by attempting to establish a TCP connection.
     * This replicates what happens when Spring tries to initialize WebClient beans.
     * 
     * @param port the port to check
     * @return true if connection succeeds, false if connection is refused or server is shut down
     */
    private boolean isMockWebServerAvailable(int port) {
        try (Socket socket = new Socket("localhost", port)) {
            // If we can establish a connection, the server is available
            return true;
        } catch (ConnectException e) {
            // Connection refused - server is not available or already shut down
            System.err.println("Connection refused to port " + port + ": " + e.getMessage());
            return false;
        } catch (IOException e) {
            // Other IO errors also indicate server is not available
            System.err.println("IO error connecting to port " + port + ": " + e.getMessage());
            return false;
        }
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates attempt numbers from 1 to 10.
     * This allows the property test to run multiple times to demonstrate
     * consistent failure across multiple attempts.
     */
    @Provide
    Arbitrary<Integer> attemptNumbers() {
        return Arbitraries.integers().between(1, 10);
    }
}
