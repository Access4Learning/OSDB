// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;

/**
 * Health indicator that checks connectivity to the Jaeger backend.
 * 
 * <p>This health indicator reports the status of the Jaeger tracing backend
 * separately from the main service health. When Jaeger is unavailable, the
 * service continues to operate normally (graceful degradation), but this
 * indicator will report DOWN status to alert operators.</p>
 * 
 * <p>The health check attempts to establish a TCP connection to the Jaeger
 * gRPC endpoint to verify that it's accepting connections. The check has a
 * timeout to prevent blocking the health endpoint.</p>
 * 
 * <p>This indicator is only active when observability tracing is enabled.</p>
 * 
 * <p><b>Validates Requirements:</b> 18.5 (Health check endpoint for observability backends)</p>
 * 
 * @see org.springframework.boot.actuate.health.HealthIndicator
 * @see org.sifunity.broker.common.config.ObservabilityConfig
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "observability.tracing.enabled", havingValue = "true", matchIfMissing = true)
public class JaegerHealthIndicator implements HealthIndicator {
    
    private final String jaegerEndpoint;
    private final int connectionTimeoutMs;
    
    /**
     * Creates a new Jaeger health indicator.
     * 
     * @param jaegerEndpoint the Jaeger collector gRPC endpoint (e.g., "http://localhost:14250")
     * @param connectionTimeoutMs timeout for connection attempt in milliseconds (default: 5000)
     */
    public JaegerHealthIndicator(
            @Value("${observability.tracing.jaeger.endpoint:http://localhost:14250}") String jaegerEndpoint,
            @Value("${observability.tracing.jaeger.connection-timeout:5000}") int connectionTimeoutMs) {
        this.jaegerEndpoint = jaegerEndpoint;
        this.connectionTimeoutMs = connectionTimeoutMs;
    }
    
    @Override
    public Health health() {
        try {
            boolean isHealthy = checkJaegerHealth();
            
            if (isHealthy) {
                return Health.up()
                        .withDetail("jaeger", "Available")
                        .withDetail("endpoint", jaegerEndpoint)
                        .withDetail("status", "Accepting traces")
                        .build();
            } else {
                return Health.down()
                        .withDetail("jaeger", "Unavailable")
                        .withDetail("endpoint", jaegerEndpoint)
                        .withDetail("error", "Connection refused or timed out")
                        .withDetail("impact", "Traces are buffered locally")
                        .build();
            }
        } catch (Exception e) {
            log.debug("Jaeger health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("jaeger", "Unavailable")
                    .withDetail("endpoint", jaegerEndpoint)
                    .withDetail("error", e.getMessage())
                    .withDetail("impact", "Traces are buffered locally")
                    .build();
        }
    }
    
    /**
     * Checks Jaeger health by attempting to establish a TCP connection.
     * 
     * <p>This method attempts a simple TCP socket connection to verify that
     * Jaeger is reachable and accepting connections on the gRPC port.
     * The connection is immediately closed after verification.</p>
     * 
     * @return true if Jaeger is reachable, false otherwise
     */
    private boolean checkJaegerHealth() {
        Socket socket = null;
        try {
            // Parse the endpoint URL to extract host and port
            URI uri = URI.create(jaegerEndpoint);
            String host = uri.getHost();
            int port = uri.getPort();
            
            // Default port if not specified
            if (port == -1) {
                port = 14250; // Default Jaeger gRPC port
            }
            
            // Create a socket with timeout
            socket = new Socket();
            InetSocketAddress address = new InetSocketAddress(host, port);
            
            // Attempt to connect with timeout
            socket.connect(address, connectionTimeoutMs);
            
            // If we got here, connection was successful
            log.debug("Successfully connected to Jaeger at {}:{}", host, port);
            return true;
            
        } catch (SocketTimeoutException e) {
            log.debug("Jaeger health check timed out after {}ms: {}", connectionTimeoutMs, e.getMessage());
            return false;
        } catch (IOException e) {
            log.debug("Failed to connect to Jaeger: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.debug("Unexpected error during Jaeger health check: {}", e.getMessage());
            return false;
        } finally {
            // Always close the socket
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    log.debug("Failed to close health check socket: {}", e.getMessage());
                }
            }
        }
    }
}
