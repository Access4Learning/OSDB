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

/**
 * Health indicator that checks connectivity to the Logstash backend.
 * 
 * <p>This health indicator reports the status of the Logstash logging backend
 * separately from the main service health. When Logstash is unavailable, the
 * service continues to operate normally (graceful degradation), but this
 * indicator will report DOWN status to alert operators.</p>
 * 
 * <p>The health check attempts to establish a TCP connection to the Logstash
 * endpoint to verify that it's accepting connections. The check has a timeout
 * to prevent blocking the health endpoint.</p>
 * 
 * <p>This indicator is only active when observability is enabled.</p>
 * 
 * <p><b>Validates Requirements:</b> 18.5 (Health check endpoint for observability backends)</p>
 * 
 * @see org.springframework.boot.actuate.health.HealthIndicator
 * @see org.sifunity.broker.common.logging.ResilientLogstashTcpSocketAppender
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "observability.enabled", havingValue = "true", matchIfMissing = true)
public class LogstashHealthIndicator implements HealthIndicator {
    
    private final String logstashHost;
    private final int logstashPort;
    private final int connectionTimeoutMs;
    
    /**
     * Creates a new Logstash health indicator.
     * 
     * @param logstashHost the Logstash server hostname (default: localhost)
     * @param logstashPort the Logstash server port (default: 5000)
     * @param connectionTimeoutMs timeout for connection attempt in milliseconds (default: 5000)
     */
    public LogstashHealthIndicator(
            @Value("${observability.logging.logstash.host:localhost}") String logstashHost,
            @Value("${observability.logging.logstash.port:5000}") int logstashPort,
            @Value("${observability.logging.logstash.connection-timeout:5000}") int connectionTimeoutMs) {
        this.logstashHost = logstashHost;
        this.logstashPort = logstashPort;
        this.connectionTimeoutMs = connectionTimeoutMs;
    }
    
    @Override
    public Health health() {
        try {
            boolean isHealthy = checkLogstashHealth();
            
            if (isHealthy) {
                return Health.up()
                        .withDetail("logstash", "Available")
                        .withDetail("host", logstashHost)
                        .withDetail("port", logstashPort)
                        .withDetail("status", "Accepting connections")
                        .build();
            } else {
                return Health.down()
                        .withDetail("logstash", "Unavailable")
                        .withDetail("host", logstashHost)
                        .withDetail("port", logstashPort)
                        .withDetail("error", "Connection refused or timed out")
                        .withDetail("impact", "Logs are buffered in async queue")
                        .build();
            }
        } catch (Exception e) {
            log.debug("Logstash health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("logstash", "Unavailable")
                    .withDetail("host", logstashHost)
                    .withDetail("port", logstashPort)
                    .withDetail("error", e.getMessage())
                    .withDetail("impact", "Logs are buffered in async queue")
                    .build();
        }
    }
    
    /**
     * Checks Logstash health by attempting to establish a TCP connection.
     * 
     * <p>This method attempts a simple TCP socket connection to verify that
     * Logstash is reachable and accepting connections on the configured port.
     * The connection is immediately closed after verification.</p>
     * 
     * @return true if Logstash is reachable, false otherwise
     */
    private boolean checkLogstashHealth() {
        Socket socket = null;
        try {
            // Create a socket with timeout
            socket = new Socket();
            InetSocketAddress address = new InetSocketAddress(logstashHost, logstashPort);
            
            // Attempt to connect with timeout
            socket.connect(address, connectionTimeoutMs);
            
            // If we got here, connection was successful
            log.debug("Successfully connected to Logstash at {}:{}", logstashHost, logstashPort);
            return true;
            
        } catch (SocketTimeoutException e) {
            log.debug("Logstash health check timed out after {}ms: {}", connectionTimeoutMs, e.getMessage());
            return false;
        } catch (IOException e) {
            log.debug("Failed to connect to Logstash at {}:{}: {}", logstashHost, logstashPort, e.getMessage());
            return false;
        } catch (Exception e) {
            log.debug("Unexpected error during Logstash health check: {}", e.getMessage());
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
