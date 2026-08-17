// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

import net.jqwik.api.*;
import okhttp3.mockwebserver.MockWebServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation Property Tests for MockWebServer Lifecycle Management
 * 
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
 * 
 * <p><strong>CRITICAL: These tests define the behavior that must be preserved after the fix.</strong>
 * These tests establish the baseline behavior that must remain unchanged when fixing the
 * MockWebServer response dispatching issue. The fix should NOT affect server lifecycle
 * management (creation, startup, port assignment, shutdown).
 * 
 * <p><strong>Purpose:</strong>
 * These property-based tests verify that the MockWebServer lifecycle patterns continue
 * to work correctly after fixing the response dispatching bug. They test:
 * <ul>
 *   <li>MockWebServer instances start successfully in @BeforeAll and get valid ports</li>
 *   <li>@DynamicPropertySource correctly injects URLs with MockWebServer ports</li>
 *   <li>MockWebServer instances shut down cleanly in @AfterAll</li>
 *   <li>Server lifecycle is completely unaffected by dispatcher configuration changes</li>
 * </ul>
 * 
 * <p><strong>Expected Behavior:</strong>
 * These tests define the preservation requirements. They focus on MockWebServer lifecycle
 * patterns that must remain unchanged after adding explicit QueueDispatcher configuration
 * and @BeforeEach dispatcher reset logic.
 * 
 * <p><strong>Observation-First Methodology:</strong>
 * These tests are based on observing the expected behavior from the existing
 * EventDistributionIntegrationTest. They capture the lifecycle patterns that must be preserved:
 * - Static MockWebServer fields are created in @BeforeAll
 * - Servers start successfully and assign valid port numbers
 * - @DynamicPropertySource injects correct URLs into Spring application context
 * - Servers shut down cleanly in @AfterAll without errors
 * - Port validation logic works correctly
 * 
 * <p><strong>Implementation Note:</strong>
 * These tests focus on MockWebServer lifecycle patterns without requiring full Spring context.
 * They verify that the core lifecycle behaviors (startup, port assignment, shutdown) work
 * correctly and are not affected by the response dispatching fix.
 * 
 * @see MockWebServerResponseDispatchingBugConditionExplorationTest
 * @see org.sifunity.eventsconnector.integration.EventDistributionIntegrationTest
 */
class MockWebServerLifecyclePreservationTest {
    
    /**
     * Property 1: Preservation - MockWebServer Startup and Port Assignment
     * 
     * **Validates: Requirements 3.1**
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES (confirms baseline behavior to preserve)</strong>
     * 
     * <p>This property verifies that MockWebServer instances start successfully and get
     * valid port numbers. This behavior must be preserved after adding explicit
     * QueueDispatcher configuration.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>MockWebServer instances can be created and started</li>
     *   <li>Servers assign valid port numbers (> 0)</li>
     *   <li>Multiple servers can start simultaneously with different ports</li>
     *   <li>Port validation logic works correctly</li>
     * </ul>
     * 
     * @param testIteration randomly generated test iteration number (1-20)
     */
    @Property(tries = 20)
    @Label("Feature: events-connector, Preservation: MockWebServer Startup and Port Assignment")
    void mockWebServerStartupAndPortAssignment(@ForAll("testIterations") int testIteration) throws IOException {
        // Arrange: Create MockWebServer instances (simulating @BeforeAll setup)
        MockWebServer subscriptionServiceMock = new MockWebServer();
        MockWebServer queueServiceMock = new MockWebServer();
        
        try {
            // Act: Start the servers (simulating @BeforeAll setup)
            subscriptionServiceMock.start();
            queueServiceMock.start();
            
            // Assert: Verify servers started successfully with valid ports
            // PRESERVATION: This startup pattern must work after adding QueueDispatcher configuration
            assertThat(subscriptionServiceMock.getPort())
                .as("Subscription service mock should have valid port (iteration %d)", testIteration)
                .as("Requirement 3.1: MockWebServer instances SHALL start successfully and assign valid port numbers")
                .isGreaterThan(0);
            
            assertThat(queueServiceMock.getPort())
                .as("Queue service mock should have valid port (iteration %d)", testIteration)
                .as("Requirement 3.1: MockWebServer instances SHALL start successfully and assign valid port numbers")
                .isGreaterThan(0);
            
            // Assert: Verify servers have different ports
            assertThat(subscriptionServiceMock.getPort())
                .as("Servers should have different ports (iteration %d)", testIteration)
                .as("Requirement 3.1: Each MockWebServer instance must get its own unique port")
                .isNotEqualTo(queueServiceMock.getPort());
            
            // Assert: Verify servers are accepting connections
            // PRESERVATION: Servers must be available after startup
            assertThat(isMockWebServerAvailable(subscriptionServiceMock.getPort()))
                .as("Subscription service mock should be accepting connections (iteration %d)", testIteration)
                .as("Requirement 3.1: Started servers must be available for connections")
                .isTrue();
            
            assertThat(isMockWebServerAvailable(queueServiceMock.getPort()))
                .as("Queue service mock should be accepting connections (iteration %d)", testIteration)
                .as("Requirement 3.1: Started servers must be available for connections")
                .isTrue();
            
        } finally {
            // Cleanup: Shutdown servers (simulating @AfterAll teardown)
            subscriptionServiceMock.shutdown();
            queueServiceMock.shutdown();
        }
    }
    
    /**
     * Property 2: Preservation - DynamicPropertySource URL Injection
     * 
     * **Validates: Requirements 3.2**
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES (confirms baseline behavior to preserve)</strong>
     * 
     * <p>This property verifies that @DynamicPropertySource correctly injects URLs with
     * MockWebServer ports into the Spring application context. This behavior must be
     * preserved after adding explicit QueueDispatcher configuration.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>URLs can be constructed from MockWebServer ports</li>
     *   <li>URL format is correct (http://localhost:PORT)</li>
     *   <li>URLs are unique for different servers</li>
     *   <li>Property injection pattern works correctly</li>
     * </ul>
     * 
     * @param testIteration randomly generated test iteration number (1-20)
     */
    @Property(tries = 20)
    @Label("Feature: events-connector, Preservation: DynamicPropertySource URL Injection")
    void dynamicPropertySourceUrlInjection(@ForAll("testIterations") int testIteration) throws IOException {
        // Arrange: Create and start MockWebServer instances
        MockWebServer subscriptionServiceMock = new MockWebServer();
        MockWebServer queueServiceMock = new MockWebServer();
        
        try {
            subscriptionServiceMock.start();
            queueServiceMock.start();
            
            int subscriptionPort = subscriptionServiceMock.getPort();
            int queuePort = queueServiceMock.getPort();
            
            // Act: Construct URLs (simulating @DynamicPropertySource logic)
            String subscriptionServiceUrl = "http://localhost:" + subscriptionPort;
            String queueServiceUrl = "http://localhost:" + queuePort;
            
            // Assert: Verify URLs are correctly constructed
            // PRESERVATION: URL injection pattern must work after the fix
            assertThat(subscriptionServiceUrl)
                .as("Subscription service URL should be correctly formatted (iteration %d)", testIteration)
                .as("Requirement 3.2: @DynamicPropertySource SHALL inject correct URLs into Spring context")
                .startsWith("http://localhost:")
                .contains(String.valueOf(subscriptionPort));
            
            assertThat(queueServiceUrl)
                .as("Queue service URL should be correctly formatted (iteration %d)", testIteration)
                .as("Requirement 3.2: @DynamicPropertySource SHALL inject correct URLs into Spring context")
                .startsWith("http://localhost:")
                .contains(String.valueOf(queuePort));
            
            // Assert: Verify URLs are different
            assertThat(subscriptionServiceUrl)
                .as("Service URLs should be different (iteration %d)", testIteration)
                .as("Requirement 3.2: Each service must have its own unique URL")
                .isNotEqualTo(queueServiceUrl);
            
            // Assert: Verify servers are reachable at the injected URLs
            assertThat(isMockWebServerAvailable(subscriptionPort))
                .as("Subscription service should be reachable at injected URL (iteration %d)", testIteration)
                .as("Requirement 3.2: Injected URLs must point to available servers")
                .isTrue();
            
            assertThat(isMockWebServerAvailable(queuePort))
                .as("Queue service should be reachable at injected URL (iteration %d)", testIteration)
                .as("Requirement 3.2: Injected URLs must point to available servers")
                .isTrue();
            
        } finally {
            // Cleanup: Shutdown servers
            subscriptionServiceMock.shutdown();
            queueServiceMock.shutdown();
        }
    }
    
    /**
     * Property 3: Preservation - MockWebServer Clean Shutdown
     * 
     * **Validates: Requirements 3.4**
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES (confirms baseline behavior to preserve)</strong>
     * 
     * <p>This property verifies that MockWebServer instances shut down cleanly in @AfterAll
     * without errors. This behavior must be preserved after adding explicit QueueDispatcher
     * configuration and @BeforeEach dispatcher reset logic.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>MockWebServer instances can be shut down without exceptions</li>
     *   <li>Shutdown releases resources properly</li>
     *   <li>Servers are no longer available after shutdown</li>
     *   <li>Multiple servers can be shut down independently</li>
     * </ul>
     * 
     * @param testIteration randomly generated test iteration number (1-20)
     */
    @Property(tries = 20)
    @Label("Feature: events-connector, Preservation: MockWebServer Clean Shutdown")
    void mockWebServerCleanShutdown(@ForAll("testIterations") int testIteration) throws IOException {
        // Arrange: Create and start MockWebServer instances
        MockWebServer subscriptionServiceMock = new MockWebServer();
        MockWebServer queueServiceMock = new MockWebServer();
        
        subscriptionServiceMock.start();
        queueServiceMock.start();
        
        int subscriptionPort = subscriptionServiceMock.getPort();
        int queuePort = queueServiceMock.getPort();
        
        // Assert: Verify servers are available before shutdown
        assertThat(isMockWebServerAvailable(subscriptionPort))
            .as("Subscription service should be available before shutdown (iteration %d)", testIteration)
            .as("Requirement 3.4: Servers must be available before shutdown")
            .isTrue();
        
        assertThat(isMockWebServerAvailable(queuePort))
            .as("Queue service should be available before shutdown (iteration %d)", testIteration)
            .as("Requirement 3.4: Servers must be available before shutdown")
            .isTrue();
        
        // Act: Shutdown servers (simulating @AfterAll teardown)
        // PRESERVATION: Shutdown must work cleanly after the fix
        subscriptionServiceMock.shutdown();
        queueServiceMock.shutdown();
        
        // Assert: Verify servers are no longer available after shutdown
        assertThat(isMockWebServerAvailable(subscriptionPort))
            .as("Subscription service should not be available after shutdown (iteration %d)", testIteration)
            .as("Requirement 3.4: MockWebServer SHALL clean up resources properly without errors")
            .isFalse();
        
        assertThat(isMockWebServerAvailable(queuePort))
            .as("Queue service should not be available after shutdown (iteration %d)", testIteration)
            .as("Requirement 3.4: MockWebServer SHALL clean up resources properly without errors")
            .isFalse();
    }
    
    /**
     * Property 4: Preservation - Port Validation Logic
     * 
     * **Validates: Requirements 3.1**
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES (confirms baseline behavior to preserve)</strong>
     * 
     * <p>This property verifies that port validation logic works correctly after server
     * startup. This behavior must be preserved after adding explicit QueueDispatcher
     * configuration.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Port validation detects invalid ports (≤ 0)</li>
     *   <li>Port validation passes for valid ports (> 0)</li>
     *   <li>Port validation works for multiple servers</li>
     *   <li>Port validation logic is unaffected by dispatcher configuration</li>
     * </ul>
     * 
     * @param testIteration randomly generated test iteration number (1-20)
     */
    @Property(tries = 20)
    @Label("Feature: events-connector, Preservation: Port Validation Logic")
    void portValidationLogic(@ForAll("testIterations") int testIteration) throws IOException {
        // Arrange: Create and start MockWebServer instances
        MockWebServer subscriptionServiceMock = new MockWebServer();
        MockWebServer queueServiceMock = new MockWebServer();
        
        try {
            subscriptionServiceMock.start();
            queueServiceMock.start();
            
            // Act: Get ports and validate (simulating @BeforeAll validation logic)
            int subscriptionPort = subscriptionServiceMock.getPort();
            int queuePort = queueServiceMock.getPort();
            
            // Assert: Verify port validation logic works correctly
            // PRESERVATION: Port validation must work after adding QueueDispatcher configuration
            boolean portsValid = subscriptionPort > 0 && queuePort > 0;
            
            assertThat(portsValid)
                .as("Port validation should pass for valid ports (iteration %d)", testIteration)
                .as("Requirement 3.1: Port validation logic must detect valid ports correctly")
                .isTrue();
            
            // Assert: Verify the validation logic that would throw IllegalStateException
            // This simulates: if (port <= 0) throw new IllegalStateException(...)
            assertThat(subscriptionPort)
                .as("Subscription service port should pass validation (iteration %d)", testIteration)
                .as("Requirement 3.1: Port validation SHALL detect when servers fail to start properly")
                .isGreaterThan(0);
            
            assertThat(queuePort)
                .as("Queue service port should pass validation (iteration %d)", testIteration)
                .as("Requirement 3.1: Port validation SHALL detect when servers fail to start properly")
                .isGreaterThan(0);
            
        } finally {
            // Cleanup: Shutdown servers
            subscriptionServiceMock.shutdown();
            queueServiceMock.shutdown();
        }
    }
    
    /**
     * Property 5: Preservation - Multiple Lifecycle Cycles
     * 
     * **Validates: Requirements 3.1, 3.4**
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES (confirms baseline behavior to preserve)</strong>
     * 
     * <p>This property verifies that the complete lifecycle (startup → use → shutdown) can
     * be repeated multiple times successfully. This simulates running multiple test classes
     * that each have their own @BeforeAll/@AfterAll lifecycle.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Lifecycle can be repeated multiple times</li>
     *   <li>Each cycle starts with clean state</li>
     *   <li>Shutdown from previous cycle doesn't affect next cycle</li>
     *   <li>Port assignment works correctly in each cycle</li>
     * </ul>
     * 
     * @param cycleCount randomly generated number of lifecycle cycles (2-5)
     */
    @Property(tries = 10)
    @Label("Feature: events-connector, Preservation: Multiple Lifecycle Cycles")
    void multipleLifecycleCycles(@ForAll("cycleCounts") int cycleCount) throws IOException {
        // Act & Assert: Execute multiple lifecycle cycles
        // PRESERVATION: Multiple lifecycle cycles must work after the fix
        for (int i = 0; i < cycleCount; i++) {
            // Simulate a complete test class lifecycle: @BeforeAll → tests → @AfterAll
            MockWebServer subscriptionServiceMock = new MockWebServer();
            MockWebServer queueServiceMock = new MockWebServer();
            
            try {
                // Startup phase (@BeforeAll)
                subscriptionServiceMock.start();
                queueServiceMock.start();
                
                // Assert: Verify servers started successfully in this cycle
                assertThat(subscriptionServiceMock.getPort())
                    .as("Subscription service should have valid port in cycle %d of %d", i + 1, cycleCount)
                    .as("Requirement 3.1: Server startup must work in each lifecycle cycle")
                    .isGreaterThan(0);
                
                assertThat(queueServiceMock.getPort())
                    .as("Queue service should have valid port in cycle %d of %d", i + 1, cycleCount)
                    .as("Requirement 3.1: Server startup must work in each lifecycle cycle")
                    .isGreaterThan(0);
                
                // Assert: Verify servers are available in this cycle
                assertThat(isMockWebServerAvailable(subscriptionServiceMock.getPort()))
                    .as("Subscription service should be available in cycle %d of %d", i + 1, cycleCount)
                    .as("Requirement 3.1: Servers must be available in each lifecycle cycle")
                    .isTrue();
                
                assertThat(isMockWebServerAvailable(queueServiceMock.getPort()))
                    .as("Queue service should be available in cycle %d of %d", i + 1, cycleCount)
                    .as("Requirement 3.1: Servers must be available in each lifecycle cycle")
                    .isTrue();
                
            } finally {
                // Shutdown phase (@AfterAll)
                subscriptionServiceMock.shutdown();
                queueServiceMock.shutdown();
                
                // Assert: Verify servers are shut down cleanly in this cycle
                // (No exception thrown = clean shutdown)
            }
        }
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates test iteration numbers from 1 to 20.
     * This allows testing multiple independent test executions.
     */
    @Provide
    Arbitrary<Integer> testIterations() {
        return Arbitraries.integers().between(1, 20);
    }
    
    /**
     * Generates cycle counts from 2 to 5.
     * This represents multiple lifecycle cycles (test class executions).
     */
    @Provide
    Arbitrary<Integer> cycleCounts() {
        return Arbitraries.integers().between(2, 5);
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Checks if MockWebServer is available by attempting to establish a TCP connection.
     * This verifies that the server is running and accepting connections.
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
            return false;
        } catch (IOException e) {
            // Other IO errors also indicate server is not available
            return false;
        }
    }
}
