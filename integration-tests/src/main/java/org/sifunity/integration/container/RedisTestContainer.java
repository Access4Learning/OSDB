// SPDX-License-Identifier: MIT
package org.sifunity.integration.container;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Redis test container configuration for integration tests.
 * 
 * <p>This container provides a Redis 7 instance for caching and message streams
 * during integration testing. The container is configured with:
 * <ul>
 *   <li>Image: redis:7-alpine</li>
 *   <li>Exposed port: 6379</li>
 *   <li>Health check: Wait for Redis to respond to PING</li>
 * </ul>
 * 
 * <p>The container is started once before all tests and stopped after all tests complete.
 * Redis data is cleared between tests to ensure test isolation.
 * 
 * @since 1.0.0
 */
@Slf4j
public class RedisTestContainer extends GenericContainer<RedisTestContainer> {
    
    private static final String IMAGE_VERSION = "redis:7-alpine";
    private static final int REDIS_PORT = 6379;
    
    private static RedisTestContainer container;
    
    /**
     * Creates a new Redis test container with default configuration.
     */
    private RedisTestContainer() {
        super(DockerImageName.parse(IMAGE_VERSION));
        
        // Expose Redis port
        withExposedPorts(REDIS_PORT);
        
        // Configure health check - wait for Redis to respond to PING
        waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)
            .withStartupTimeout(Duration.ofSeconds(30)));
        
        // Enable reuse for faster test execution (optional)
        withReuse(false); // Set to true for development to reuse containers
        
        log.info("Redis test container configured");
    }
    
    /**
     * Gets the singleton instance of the Redis test container.
     * 
     * @return the Redis test container instance
     */
    public static RedisTestContainer getInstance() {
        if (container == null) {
            container = new RedisTestContainer();
        }
        return container;
    }
    
    /**
     * Starts the Redis container if not already started.
     */
    @Override
    public void start() {
        super.start();
        log.info("Redis test container started: host={}, port={}", 
            getHost(), getMappedPort(REDIS_PORT));
    }
    
    /**
     * Stops the Redis container.
     */
    @Override
    public void stop() {
        log.info("Stopping Redis test container");
        super.stop();
    }
    
    /**
     * Gets the Redis host.
     * 
     * @return the host
     */
    public String getRedisHost() {
        return getHost();
    }
    
    /**
     * Gets the mapped port for Redis.
     * 
     * @return the mapped port
     */
    public Integer getRedisPort() {
        return getMappedPort(REDIS_PORT);
    }
    
    /**
     * Gets the Redis connection string.
     * 
     * @return the connection string in format "host:port"
     */
    public String getConnectionString() {
        return getHost() + ":" + getMappedPort(REDIS_PORT);
    }
    
    /**
     * Clears all data from Redis.
     * 
     * <p>This method executes the FLUSHALL command to remove all keys from all databases.
     * This is useful for ensuring test isolation between tests.
     */
    public void clearCache() {
        try {
            log.debug("Clearing Redis cache");
            execInContainer("redis-cli", "FLUSHALL");
            log.debug("Redis cache cleared successfully");
        } catch (Exception e) {
            log.error("Failed to clear Redis cache", e);
            throw new RuntimeException("Failed to clear Redis cache", e);
        }
    }
}
