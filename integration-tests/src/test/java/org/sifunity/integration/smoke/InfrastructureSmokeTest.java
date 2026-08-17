// SPDX-License-Identifier: MIT
package org.sifunity.integration.smoke;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sifunity.integration.base.IntegrationTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for integration test infrastructure.
 * 
 * <p>These tests verify that the basic infrastructure is working correctly:
 * <ul>
 *   <li>Testcontainers start successfully</li>
 *   <li>Database migrations apply correctly</li>
 *   <li>Containers are accessible</li>
 * </ul>
 * 
 * <p>These tests do NOT start microservices, so they run quickly and can
 * be used to verify the infrastructure setup before running full integration tests.
 * 
 * @since 1.0.0
 */
@Slf4j
@Tag("smoke")
@DisplayName("Infrastructure Smoke Tests")
public class InfrastructureSmokeTest extends IntegrationTestBase {
    
    @Test
    @DisplayName("PostgreSQL container is running and accessible")
    public void testPostgresContainerIsRunning() {
        log.info("Testing PostgreSQL container");
        
        assertThat(postgresContainer).isNotNull();
        assertThat(postgresContainer.isRunning()).isTrue();
        
        String jdbcUrl = postgresContainer.getJdbcUrl();
        assertThat(jdbcUrl).isNotNull().contains("postgresql");
        
        log.info("PostgreSQL container is running: {}", jdbcUrl);
    }
    
    @Test
    @DisplayName("Redis container is running and accessible")
    public void testRedisContainerIsRunning() {
        log.info("Testing Redis container");
        
        assertThat(redisContainer).isNotNull();
        assertThat(redisContainer.isRunning()).isTrue();
        
        String connectionString = redisContainer.getConnectionString();
        assertThat(connectionString).isNotNull().contains(":");
        
        log.info("Redis container is running: {}", connectionString);
    }
    
    @Test
    @DisplayName("LocalStack container is running and accessible")
    public void testLocalStackContainerIsRunning() {
        log.info("Testing LocalStack container");
        
        assertThat(localStackContainer).isNotNull();
        assertThat(localStackContainer.isRunning()).isTrue();
        
        String endpointUrl = localStackContainer.getEndpointUrl();
        assertThat(endpointUrl).isNotNull().contains("http://");
        
        String bucketName = localStackContainer.getBucketName();
        assertThat(bucketName).isNotNull().isNotEmpty();
        
        log.info("LocalStack container is running: {} (bucket: {})", endpointUrl, bucketName);
    }
    
    @Test
    @DisplayName("All containers are healthy")
    public void testAllContainersAreHealthy() {
        log.info("Testing all containers are healthy");
        
        assertThat(postgresContainer.isRunning()).isTrue();
        assertThat(redisContainer.isRunning()).isTrue();
        assertThat(localStackContainer.isRunning()).isTrue();
        
        log.info("All containers are healthy");
    }
    
    @Test
    @DisplayName("RestAssured is configured correctly")
    public void testRestAssuredConfiguration() {
        log.info("Testing RestAssured configuration");
        
        assertThat(gatewayBaseUrl).isNotNull().isEqualTo("http://localhost");
        assertThat(gatewayPort).isEqualTo(DEFAULT_GATEWAY_PORT);
        
        log.info("RestAssured configured: {}:{}", gatewayBaseUrl, gatewayPort);
    }
}
