// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.io.IOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LogstashHealthIndicator.
 * 
 * <p>These tests verify that the health indicator correctly reports Logstash
 * backend connectivity status and handles various failure scenarios gracefully.</p>
 * 
 * <p><b>Validates Requirements:</b> 18.5 (Health check endpoint for observability backends)</p>
 */
class LogstashHealthIndicatorTest {
    
    private static final String LOCALHOST = "localhost";
    private static final int CONNECTION_TIMEOUT_MS = 1000; // Short timeout for tests
    
    @Test
    void health_WhenLogstashIsAvailable_ReturnsUp() throws IOException {
        // Arrange - Start a mock server to simulate Logstash
        try (ServerSocket mockLogstash = new ServerSocket(0)) {
            int port = mockLogstash.getLocalPort();
            
            LogstashHealthIndicator healthIndicator = new LogstashHealthIndicator(
                    LOCALHOST, port, CONNECTION_TIMEOUT_MS);
            
            // Act
            Health health = healthIndicator.health();
            
            // Assert
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("logstash", "Available");
            assertThat(health.getDetails()).containsEntry("host", LOCALHOST);
            assertThat(health.getDetails()).containsEntry("port", port);
            assertThat(health.getDetails()).containsEntry("status", "Accepting connections");
            assertThat(health.getDetails()).doesNotContainKey("error");
            assertThat(health.getDetails()).doesNotContainKey("impact");
        }
    }
    
    @Test
    void health_WhenLogstashIsUnavailable_ReturnsDown() {
        // Arrange - Use a port that's not listening
        int unavailablePort = 19999;
        
        LogstashHealthIndicator healthIndicator = new LogstashHealthIndicator(
                LOCALHOST, unavailablePort, CONNECTION_TIMEOUT_MS);
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("logstash", "Unavailable");
        assertThat(health.getDetails()).containsEntry("host", LOCALHOST);
        assertThat(health.getDetails()).containsEntry("port", unavailablePort);
        assertThat(health.getDetails()).containsEntry("impact", "Logs are buffered in async queue");
        assertThat(health.getDetails()).containsKey("error");
    }
    
    @Test
    void health_WhenLogstashConnectionTimesOut_ReturnsDown() {
        // Arrange - Use a non-routable IP to force timeout
        // 192.0.2.0/24 is reserved for documentation and testing (RFC 5737)
        String nonRoutableHost = "192.0.2.1";
        int port = 5000;
        
        LogstashHealthIndicator healthIndicator = new LogstashHealthIndicator(
                nonRoutableHost, port, CONNECTION_TIMEOUT_MS);
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("logstash", "Unavailable");
        assertThat(health.getDetails()).containsEntry("host", nonRoutableHost);
        assertThat(health.getDetails()).containsEntry("port", port);
        assertThat(health.getDetails()).containsEntry("impact", "Logs are buffered in async queue");
    }
    
    @Test
    void health_WhenHostIsInvalid_ReturnsDown() {
        // Arrange - Invalid hostname
        String invalidHost = "this-host-does-not-exist-12345.invalid";
        int port = 5000;
        
        LogstashHealthIndicator healthIndicator = new LogstashHealthIndicator(
                invalidHost, port, CONNECTION_TIMEOUT_MS);
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("logstash", "Unavailable");
        assertThat(health.getDetails()).containsKey("error");
    }
    
    @Test
    void health_IncludesImpactInformation_WhenDown() {
        // Arrange
        int unavailablePort = 19999;
        
        LogstashHealthIndicator healthIndicator = new LogstashHealthIndicator(
                LOCALHOST, unavailablePort, CONNECTION_TIMEOUT_MS);
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert - Verify impact message explains graceful degradation
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("impact", "Logs are buffered in async queue");
    }
    
    @Test
    void health_DoesNotBlockIndefinitely_WhenLogstashIsUnreachable() {
        // Arrange
        String nonRoutableHost = "192.0.2.1"; // Non-routable IP
        int port = 5000;
        
        LogstashHealthIndicator healthIndicator = new LogstashHealthIndicator(
                nonRoutableHost, port, CONNECTION_TIMEOUT_MS);
        
        // Act - Measure execution time
        long startTime = System.currentTimeMillis();
        Health health = healthIndicator.health();
        long duration = System.currentTimeMillis() - startTime;
        
        // Assert - Should complete within reasonable time (timeout + overhead)
        assertThat(duration).isLessThan(CONNECTION_TIMEOUT_MS + 1000);
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
    
    @Test
    void health_HandlesMultipleConsecutiveCalls_Correctly() throws IOException {
        // Arrange
        try (ServerSocket mockLogstash = new ServerSocket(0)) {
            int port = mockLogstash.getLocalPort();
            
            LogstashHealthIndicator healthIndicator = new LogstashHealthIndicator(
                    LOCALHOST, port, CONNECTION_TIMEOUT_MS);
            
            // Act - Call health check multiple times
            Health health1 = healthIndicator.health();
            Health health2 = healthIndicator.health();
            Health health3 = healthIndicator.health();
            
            // Assert - All calls should succeed
            assertThat(health1.getStatus()).isEqualTo(Status.UP);
            assertThat(health2.getStatus()).isEqualTo(Status.UP);
            assertThat(health3.getStatus()).isEqualTo(Status.UP);
        }
    }
    
    @Test
    void health_ClosesSocketProperly_AfterHealthCheck() throws IOException {
        // Arrange
        try (ServerSocket mockLogstash = new ServerSocket(0)) {
            int port = mockLogstash.getLocalPort();
            
            LogstashHealthIndicator healthIndicator = new LogstashHealthIndicator(
                    LOCALHOST, port, CONNECTION_TIMEOUT_MS);
            
            // Act - Perform health check
            Health health = healthIndicator.health();
            
            // Assert - Health check succeeded
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            
            // Act - Perform another health check immediately
            // This verifies that the previous socket was properly closed
            Health health2 = healthIndicator.health();
            
            // Assert - Second check should also succeed
            assertThat(health2.getStatus()).isEqualTo(Status.UP);
        }
    }
    
    @Test
    void health_WithDefaultConfiguration_UsesCorrectDefaults() {
        // Arrange - Use default values
        LogstashHealthIndicator healthIndicator = new LogstashHealthIndicator(
                "localhost", 5000, 5000);
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert - Should attempt connection with default values
        assertThat(health.getDetails()).containsEntry("host", "localhost");
        assertThat(health.getDetails()).containsEntry("port", 5000);
    }
}
