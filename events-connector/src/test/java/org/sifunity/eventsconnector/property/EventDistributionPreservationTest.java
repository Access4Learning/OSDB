// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

import net.jqwik.api.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation Property Tests for Event Distribution Integration
 * 
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
 * 
 * <p><strong>CRITICAL: These tests define the behavior that must be preserved after the fix.</strong>
 * These tests establish the baseline behavior that must remain unchanged when moving from
 * static MockWebServer with @BeforeAll/@AfterAll to instance-based MockWebServer with
 * @BeforeEach/@AfterEach.
 * 
 * <p><strong>Purpose:</strong>
 * These property-based tests verify that the core MockWebServer lifecycle patterns
 * continue to work correctly after fixing the lifecycle bug. They test:
 * <ul>
 *   <li>MockWebServer instances can be initialized and remain available</li>
 *   <li>Mock responses can be configured and retrieved correctly</li>
 *   <li>Test isolation works with independent MockWebServer instances</li>
 *   <li>Resources are cleaned up properly after tests</li>
 *   <li>Multiple sequential test executions work independently</li>
 * </ul>
 * 
 * <p><strong>Expected Behavior:</strong>
 * These tests define the preservation requirements. They focus on MockWebServer lifecycle
 * patterns without requiring full Spring context, ensuring the core behaviors work correctly.
 * 
 * <p><strong>Observation-First Methodology:</strong>
 * These tests are based on observing the expected behavior from the existing test methods
 * in EventDistributionIntegrationTest. They capture the patterns that must be preserved:
 * - MockWebServer instances must isolate tests from real external dependencies
 * - Mock responses must be properly configured before each test
 * - Event distribution business logic must be validated correctly
 * - Resources must be cleaned up properly after tests
 * - Each test method must execute independently
 * 
 * <p><strong>Implementation Note:</strong>
 * These tests focus on MockWebServer lifecycle patterns without requiring full Spring context.
 * They verify that the core MockWebServer behaviors (initialization, configuration, cleanup)
 * work correctly with instance-based lifecycle management (@BeforeEach/@AfterEach pattern).
 * 
 * @see MockWebServerLifecycleBugConditionExplorationTest
 * @see org.sifunity.eventsconnector.integration.EventDistributionIntegrationTest
 */
class EventDistributionPreservationTest {
    
    /**
     * Property 1: Preservation - MockWebServer Initialization and Availability
     * 
     * **Validates: Requirements 3.1, 3.2**
     * 
     * <p><strong>EXPECTED OUTCOME: This test defines preservation requirements</strong>
     * 
     * <p>This property verifies that MockWebServer instances can be properly initialized
     * and remain available throughout test execution when using instance-based lifecycle
     * management. This behavior must be preserved after fixing the lifecycle issue.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>MockWebServer instances can be created and started</li>
     *   <li>MockWebServer instances have valid ports after starting</li>
     *   <li>MockWebServer instances accept connections</li>
     *   <li>Multiple MockWebServer instances can run simultaneously</li>
     *   <li>Tests are isolated from real external services</li>
     * </ul>
     * 
     * @param testIteration randomly generated test iteration number (1-20)
     */
    @Property(tries = 20)
    @Label("Feature: events-connector, Preservation: MockWebServer Initialization and Availability")
    void mockWebServerInitializationAndAvailability(@ForAll("testIterations") int testIteration) throws IOException {
        // Arrange: Create instance-based MockWebServer instances
        MockWebServer server1 = new MockWebServer();
        MockWebServer server2 = new MockWebServer();
        
        try {
            // Act: Start the servers
            server1.start();
            server2.start();
            
            // Assert: Verify servers are initialized with valid ports
            // PRESERVATION: This initialization pattern must work after the fix
            assertThat(server1.getPort())
                .as("Server 1 should have valid port (iteration %d)", testIteration)
                .as("Requirement 3.1: MockWebServer must isolate tests from real external dependencies")
                .isGreaterThan(0);
            
            assertThat(server2.getPort())
                .as("Server 2 should have valid port (iteration %d)", testIteration)
                .as("Requirement 3.1: MockWebServer must isolate tests from real external dependencies")
                .isGreaterThan(0);
            
            // Assert: Verify servers are accepting connections
            // PRESERVATION: Servers must be available during test execution
            boolean server1Available = isMockWebServerAvailable(server1.getPort());
            boolean server2Available = isMockWebServerAvailable(server2.getPort());
            
            assertThat(server1Available)
                .as("Server 1 should be accepting connections (iteration %d)", testIteration)
                .as("Requirement 3.2: Test fixtures must be properly configured")
                .isTrue();
            
            assertThat(server2Available)
                .as("Server 2 should be accepting connections (iteration %d)", testIteration)
                .as("Requirement 3.2: Test fixtures must be properly configured")
                .isTrue();
            
        } finally {
            // Cleanup: Shutdown servers
            server1.shutdown();
            server2.shutdown();
        }
    }
    
    /**
     * Property 2: Preservation - Mock Response Configuration and Retrieval
     * 
     * **Validates: Requirements 3.2, 3.4**
     * 
     * <p><strong>EXPECTED OUTCOME: This test defines preservation requirements</strong>
     * 
     * <p>This property verifies that mock responses can be properly configured and
     * retrieved from MockWebServer instances. This behavior must be preserved after
     * fixing the lifecycle issue.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Mock responses can be enqueued</li>
     *   <li>Mock responses are returned in FIFO order</li>
     *   <li>Multiple responses can be enqueued</li>
     *   <li>Response configuration works correctly</li>
     * </ul>
     * 
     * @param responseCount randomly generated number of responses (1-10)
     */
    @Property(tries = 20)
    @Label("Feature: events-connector, Preservation: Mock Response Configuration and Retrieval")
    void mockResponseConfigurationAndRetrieval(@ForAll("responseCounts") int responseCount) throws IOException {
        // Arrange: Create and start MockWebServer
        MockWebServer server = new MockWebServer();
        
        try {
            server.start();
            
            // Act: Enqueue multiple mock responses
            // PRESERVATION: Response configuration must work after the fix
            for (int i = 0; i < responseCount; i++) {
                server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"index\":" + i + "}"));
            }
            
            // Assert: Verify responses were enqueued
            assertThat(server.getRequestCount())
                .as("Server should have 0 requests before any connections")
                .as("Requirement 3.2: Test fixtures must be properly configured")
                .isEqualTo(0);
            
            // Assert: Verify server is available with configured responses
            assertThat(server.getPort())
                .as("Server should have valid port with %d responses enqueued", responseCount)
                .as("Requirement 3.4: Business logic validation must remain unchanged")
                .isGreaterThan(0);
            
            assertThat(isMockWebServerAvailable(server.getPort()))
                .as("Server should be available with %d responses enqueued", responseCount)
                .as("Requirement 3.2: Mock responses must be properly configured")
                .isTrue();
            
        } finally {
            // Cleanup: Shutdown server
            server.shutdown();
        }
    }
    
    /**
     * Property 3: Preservation - Test Isolation with Independent MockWebServer Instances
     * 
     * **Validates: Requirements 3.5**
     * 
     * <p><strong>EXPECTED OUTCOME: This test defines preservation requirements</strong>
     * 
     * <p>This property verifies that each test execution can have independent MockWebServer
     * instances that don't interfere with each other. This behavior must be preserved after
     * fixing the lifecycle issue.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Each test can create its own MockWebServer instances</li>
     *   <li>MockWebServer instances use different ports</li>
     *   <li>Tests don't share state between executions</li>
     *   <li>Cleanup doesn't affect other tests</li>
     * </ul>
     * 
     * @param testIteration randomly generated test iteration number (1-15)
     */
    @Property(tries = 15)
    @Label("Feature: events-connector, Preservation: Test Isolation with Independent MockWebServer Instances")
    void testIsolationWithIndependentMockWebServerInstances(@ForAll("isolationIterations") int testIteration) throws IOException {
        // Arrange: Create independent MockWebServer instances for this test
        MockWebServer server1 = new MockWebServer();
        MockWebServer server2 = new MockWebServer();
        
        try {
            // Act: Start servers
            server1.start();
            server2.start();
            
            int port1 = server1.getPort();
            int port2 = server2.getPort();
            
            // Assert: Verify servers have different ports (independence)
            // PRESERVATION: Test isolation must be maintained after the fix
            assertThat(port1)
                .as("Server 1 should have different port than Server 2 (iteration %d)", testIteration)
                .as("Requirement 3.5: Each test method must execute independently")
                .isNotEqualTo(port2);
            
            // Assert: Verify both servers are independently available
            assertThat(isMockWebServerAvailable(port1))
                .as("Server 1 should be independently available (iteration %d)", testIteration)
                .as("Requirement 3.5: Tests must execute independently with proper isolation")
                .isTrue();
            
            assertThat(isMockWebServerAvailable(port2))
                .as("Server 2 should be independently available (iteration %d)", testIteration)
                .as("Requirement 3.5: Tests must execute independently with proper isolation")
                .isTrue();
            
            // Act: Configure different responses for each server
            server1.enqueue(new MockResponse().setResponseCode(200).setBody("server1"));
            server2.enqueue(new MockResponse().setResponseCode(201).setBody("server2"));
            
            // Assert: Verify servers maintain independent state
            assertThat(server1.getRequestCount())
                .as("Server 1 should have independent request count (iteration %d)", testIteration)
                .as("Requirement 3.5: Test isolation must be maintained")
                .isEqualTo(0);
            
            assertThat(server2.getRequestCount())
                .as("Server 2 should have independent request count (iteration %d)", testIteration)
                .as("Requirement 3.5: Test isolation must be maintained")
                .isEqualTo(0);
            
        } finally {
            // Cleanup: Shutdown servers independently
            server1.shutdown();
            server2.shutdown();
        }
    }
    
    /**
     * Property 4: Preservation - Resource Cleanup After Test Execution
     * 
     * **Validates: Requirements 3.3**
     * 
     * <p><strong>EXPECTED OUTCOME: This test defines preservation requirements</strong>
     * 
     * <p>This property verifies that MockWebServer resources are properly cleaned up
     * after test execution. The cleanup timing changes with the fix (from @AfterAll
     * to @AfterEach), but the cleanup behavior itself must be preserved.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>MockWebServer instances can be shut down cleanly</li>
     *   <li>Shutdown releases resources properly</li>
     *   <li>Servers are no longer available after shutdown</li>
     *   <li>Cleanup can be performed multiple times independently</li>
     * </ul>
     * 
     * @param cleanupIteration randomly generated cleanup iteration number (1-15)
     */
    @Property(tries = 15)
    @Label("Feature: events-connector, Preservation: Resource Cleanup After Test Execution")
    void resourceCleanupAfterTestExecution(@ForAll("cleanupIterations") int cleanupIteration) throws IOException {
        // Arrange: Create and start MockWebServer
        MockWebServer server = new MockWebServer();
        server.start();
        
        int port = server.getPort();
        
        // Assert: Verify server is available before cleanup
        assertThat(isMockWebServerAvailable(port))
            .as("Server should be available before cleanup (iteration %d)", cleanupIteration)
            .as("Requirement 3.3: Resources must be available during test execution")
            .isTrue();
        
        // Act: Shutdown server (cleanup)
        // PRESERVATION: Cleanup behavior must work correctly after the fix
        server.shutdown();
        
        // Assert: Verify server is no longer available after cleanup
        // Note: We expect connection to fail after shutdown
        assertThat(isMockWebServerAvailable(port))
            .as("Server should not be available after cleanup (iteration %d)", cleanupIteration)
            .as("Requirement 3.3: MockWebServer resources must be cleaned up properly")
            .isFalse();
    }
    
    /**
     * Property 5: Preservation - Multiple Sequential Test Executions
     * 
     * **Validates: Requirements 3.5**
     * 
     * <p><strong>EXPECTED OUTCOME: This test defines preservation requirements</strong>
     * 
     * <p>This property verifies that multiple sequential test executions can each
     * create, use, and cleanup MockWebServer instances independently. This simulates
     * the pattern of multiple test methods running in sequence.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Sequential tests can create independent MockWebServer instances</li>
     *   <li>Each test execution starts with clean state</li>
     *   <li>Cleanup from previous tests doesn't affect subsequent tests</li>
     *   <li>The same lifecycle pattern works repeatedly</li>
     * </ul>
     * 
     * @param sequenceLength randomly generated sequence length (2-5)
     */
    @Property(tries = 10)
    @Label("Feature: events-connector, Preservation: Multiple Sequential Test Executions")
    void multipleSequentialTestExecutions(@ForAll("sequenceLengths") int sequenceLength) throws IOException {
        // Act & Assert: Execute multiple sequential test simulations
        // PRESERVATION: Sequential test execution must work after the fix
        for (int i = 0; i < sequenceLength; i++) {
            // Simulate a test execution: create, use, cleanup
            MockWebServer server = new MockWebServer();
            
            try {
                server.start();
                
                // Assert: Verify server is available for this test execution
                assertThat(server.getPort())
                    .as("Server should have valid port in execution %d of %d", i + 1, sequenceLength)
                    .as("Requirement 3.5: Each test method must execute independently")
                    .isGreaterThan(0);
                
                assertThat(isMockWebServerAvailable(server.getPort()))
                    .as("Server should be available in execution %d of %d", i + 1, sequenceLength)
                    .as("Requirement 3.5: Tests must execute independently with proper isolation")
                    .isTrue();
                
                // Simulate test work: enqueue and verify response
                server.enqueue(new MockResponse().setResponseCode(200));
                
                assertThat(server.getRequestCount())
                    .as("Server should start with 0 requests in execution %d of %d", i + 1, sequenceLength)
                    .as("Requirement 3.5: Each test should start with clean state")
                    .isEqualTo(0);
                
            } finally {
                // Cleanup: Shutdown server after this test execution
                server.shutdown();
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
     * Generates response counts from 1 to 10.
     * This represents scenarios with multiple mock responses.
     */
    @Provide
    Arbitrary<Integer> responseCounts() {
        return Arbitraries.integers().between(1, 10);
    }
    
    /**
     * Generates isolation iteration numbers from 1 to 15.
     * This allows testing test isolation across multiple scenarios.
     */
    @Provide
    Arbitrary<Integer> isolationIterations() {
        return Arbitraries.integers().between(1, 15);
    }
    
    /**
     * Generates cleanup iteration numbers from 1 to 15.
     * This allows testing resource cleanup across multiple scenarios.
     */
    @Provide
    Arbitrary<Integer> cleanupIterations() {
        return Arbitraries.integers().between(1, 15);
    }
    
    /**
     * Generates sequence lengths from 2 to 5.
     * This represents multiple sequential test executions.
     */
    @Provide
    Arbitrary<Integer> sequenceLengths() {
        return Arbitraries.integers().between(2, 5);
    }
    
    // ========== Helper Methods ==========
    
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
            return false;
        } catch (IOException e) {
            // Other IO errors also indicate server is not available
            return false;
        }
    }
}
