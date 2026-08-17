// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.io.IOException;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JaegerHealthIndicator.
 * 
 * <p>These tests verify that the health indicator correctly reports Jaeger
 * backend connectivity status and handles various failure scenarios gracefully.</p>
 * 
 * <p><b>Validates Requirements:</b> 18.5 (Health check endpoint for observability backends)</p>
 */
class JaegerHealthIndicatorTest {
    
    private static final String LOCALHOST = "localhost";
    private static final int CONNECTION_TIMEOUT_MS = 1000; // Short timeout for tests
    
    @Test
    void health_WhenJaegerIsAvailable_ReturnsUp() throws IOException {
        // Arrange - Start a mock server to simulate Jaeger
        try (ServerSocket mockJaeger = new ServerSocket(0)) {
            int port = mockJaeger.getLocalPort();
            String endpoint = "http://" + LOCALHOST + ":" + port;
            
            JaegerHealthIndicator healthIndicator = new JaegerHealthIndicator(
                    endpoint, CONNECTION_TIMEOUT_MS);
            
            // Act
            Health health = healthIndicator.health();
            
            // Assert
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("jaeger", "Available");
            assertThat(health.getDetails()).containsEntry("endpoint", endpoint);
            assertThat(health.getDetails()).containsEntry("status", "Accepting traces");
            assertThat(health.getDetails()).doesNotContainKey("error");
            assertThat(health.getDetails()).doesNotContainKey("impact");
        }
    }
    
    @Test
    void health_WhenJaegerIsUnavailable_ReturnsDown() {
        // Arrange - Use a port that's not listening
        String endpoint = "http://" + LOCALHOST + ":19999";
        
        JaegerHealthIndicator healthIndicator = new JaegerHealthIndicator(
                endpoint, CONNECTION_TIMEOUT_MS);
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("jaeger", "Unavailable");
        assertThat(health.getDetails()).containsEntry("endpoint", endpoint);
        assertThat(health.getDetails()).containsEntry("impact", "Traces are buffered locally");
        assertThat(health.getDetails()).containsKey("error");
    }
    
    @Test
    void health_WhenJaegerConnectionTimesOut_ReturnsDown() {
        // Arrange - Use a non-routable IP to force timeout
        // 192.0.2.0/24 is reserved for documentation and testing (RFC 5737)
        String endpoint = "http://192.0.2.1:14250";
        
        JaegerHealthIndicator healthIndicator = new JaegerHealthIndicator(
                endpoint, CONNECTION_TIMEOUT_MS);
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("jaeger", "Unavailable");
        assertThat(health.getDetails()).containsEntry("endpoint", endpoint);
        assertThat(health.getDetails()).containsEntry("impact", "Traces are buffered locally");
    }
    
    @Test
    void health_WhenEndpointHasNoPort_UsesDefaultPort() {
        // Arrange - Endpoint without explicit port, using non-routable IP
        // to ensure connection fails (localhost:80 may have something listening)
        String endpoint = "http://192.0.2.1";
        
        JaegerHealthIndicator healthIndicator = new JaegerHealthIndicator(
                endpoint, CONNECTION_TIMEOUT_MS);
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert - Should attempt connection to default port 14250 and fail
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("endpoint", endpoint);
    }
    
    @Test
    void health_WhenEndpointIsInvalid_ReturnsDown() {
        // Arrange - Invalid endpoint format
        String endpoint = "not-a-valid-url";
        
        JaegerHealthIndicator healthIndicator = new JaegerHealthIndicator(
                endpoint, CONNECTION_TIMEOUT_MS);
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("jaeger", "Unavailable");
        assertThat(health.getDetails()).containsKey("error");
    }
    
    @Test
    void health_IncludesImpactInformation_WhenDown() {
        // Arrange
        String endpoint = "http://" + LOCALHOST + ":19999";
        
        JaegerHealthIndicator healthIndicator = new JaegerHealthIndicator(
                endpoint, CONNECTION_TIMEOUT_MS);
        
        // Act
        Health health = healthIndicator.health();
        
        // Assert - Verify impact message explains graceful degradation
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("impact", "Traces are buffered locally");
    }
    
    @Test
    void health_DoesNotBlockIndefinitely_WhenJaegerIsUnreachable() {
        // Arrange
        String endpoint = "http://192.0.2.1:14250"; // Non-routable IP
        
        JaegerHealthIndicator healthIndicator = new JaegerHealthIndicator(
                endpoint, CONNECTION_TIMEOUT_MS);
        
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
        try (ServerSocket mockJaeger = new ServerSocket(0)) {
            int port = mockJaeger.getLocalPort();
            String endpoint = "http://" + LOCALHOST + ":" + port;
            
            JaegerHealthIndicator healthIndicator = new JaegerHealthIndicator(
                    endpoint, CONNECTION_TIMEOUT_MS);
            
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
}
