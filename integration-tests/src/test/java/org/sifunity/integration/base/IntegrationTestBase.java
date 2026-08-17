// SPDX-License-Identifier: MIT
package org.sifunity.integration.base;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.LogConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.sifunity.integration.container.LocalStackTestContainer;
import org.sifunity.integration.container.PostgresTestContainer;
import org.sifunity.integration.container.RedisTestContainer;
import org.sifunity.integration.util.DatabaseMigrationUtil;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Base class for all integration tests.
 * 
 * <p>This class provides the infrastructure setup for integration tests:
 * <ul>
 *   <li>Testcontainers: PostgreSQL, Redis, LocalStack (S3)</li>
 *   <li>Database migrations: Applied from all microservices</li>
 *   <li>Test data cleanup: Automatic cleanup between tests</li>
 * </ul>
 * 
 * <p>The test lifecycle is:
 * <ol>
 *   <li>Before all tests: Start containers, apply migrations, start services</li>
 *   <li>Before each test: Clean up test data</li>
 *   <li>After each test: Clean up test data</li>
 *   <li>After all tests: Stop services, stop containers</li>
 * </ol>
 * 
 * <p>All integration test classes should extend this base class to get the
 * infrastructure setup automatically.
 * 
 * @since 1.0.0
 */
@Slf4j
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class IntegrationTestBase {
    
    /**
     * Default Gateway port for integration tests.
     * Can be overridden by subclasses if needed.
     */
    protected static final int DEFAULT_GATEWAY_PORT = 18080;
    
    /**
     * Gateway base URL for RestAssured.
     */
    protected static String gatewayBaseUrl;
    
    /**
     * Gateway port for RestAssured.
     */
    protected static int gatewayPort = DEFAULT_GATEWAY_PORT;
    
    /**
     * PostgreSQL container for database.
     * Shared across all tests in the class.
     */
    protected static PostgresTestContainer postgresContainer;
    
    /**
     * Redis container for caching and message streams.
     * Shared across all tests in the class.
     */
    protected static RedisTestContainer redisContainer;
    
    /**
     * LocalStack container for S3 simulation.
     * Shared across all tests in the class.
     */
    protected static LocalStackTestContainer localStackContainer;
    
    /**
     * Service orchestrator for starting microservices.
     * Shared across all tests in the class.
     */
    protected static org.sifunity.integration.service.ServiceOrchestrator serviceOrchestrator;
    
    /**
     * Flag to control whether services should be started.
     * Subclasses can override this to enable service startup.
     */
    protected boolean shouldStartServices() {
        return false; // Default: don't start services (for faster test development)
    }
    
    /**
     * Sets up the test infrastructure before all tests.
     * 
     * <p>This method:
     * <ol>
     *   <li>Starts Testcontainers (PostgreSQL, Redis, LocalStack)</li>
     *   <li>Waits for containers to be healthy</li>
     *   <li>Applies database migrations</li>
     *   <li>Starts all microservices (implemented by subclasses)</li>
     * </ol>
     */
    @BeforeAll
    public void setupInfrastructure() {
        log.info("=".repeat(80));
        log.info("Setting up integration test infrastructure");
        log.info("=".repeat(80));
        
        try {
            // Start containers
            startContainers();
            
            // Apply database migrations
            applyDatabaseMigrations();
            
            // Start services (implemented by subclasses if needed)
            startServices();
            
            // Configure RestAssured
            configureRestAssured();
            
            log.info("Integration test infrastructure setup completed successfully");
            
        } catch (Exception e) {
            log.error("Failed to setup integration test infrastructure", e);
            throw new RuntimeException("Infrastructure setup failed", e);
        }
    }
    
    /**
     * Tears down the test infrastructure after all tests.
     * 
     * <p>This method:
     * <ol>
     *   <li>Stops all microservices</li>
     *   <li>Stops Testcontainers</li>
     * </ol>
     */
    @AfterAll
    public void teardownInfrastructure() {
        log.info("=".repeat(80));
        log.info("Tearing down integration test infrastructure");
        log.info("=".repeat(80));
        
        try {
            // Stop services (implemented by subclasses if needed)
            stopServices();
            
            // Stop containers
            stopContainers();
            
            log.info("Integration test infrastructure teardown completed successfully");
            
        } catch (Exception e) {
            log.error("Failed to teardown integration test infrastructure", e);
        }
    }
    
    /**
     * Sets up test data before each test.
     * 
     * <p>This method cleans up any existing test data to ensure test isolation.
     * Subclasses can override this method to add custom setup logic.
     */
    @BeforeEach
    public void setupTest() {
        log.info("-".repeat(80));
        log.info("Setting up test");
        log.info("-".repeat(80));
        
        // Clean up test data before each test
        cleanupTestData();
    }
    
    /**
     * Tears down test data after each test.
     * 
     * <p>This method cleans up test data to ensure test isolation.
     * Subclasses can override this method to add custom teardown logic.
     */
    @AfterEach
    public void teardownTest() {
        log.info("-".repeat(80));
        log.info("Tearing down test");
        log.info("-".repeat(80));
        
        // Clean up test data after each test
        cleanupTestData();
    }
    
    /**
     * Starts all Testcontainers.
     * 
     * <p>Containers are started in parallel for faster startup.
     * Each container waits for its health check to pass before proceeding.
     */
    private void startContainers() {
        log.info("Starting Testcontainers");
        
        // Get container instances
        postgresContainer = PostgresTestContainer.getInstance();
        redisContainer = RedisTestContainer.getInstance();
        localStackContainer = LocalStackTestContainer.getInstance();
        
        // Start containers
        log.info("Starting PostgreSQL container");
        postgresContainer.start();
        
        log.info("Starting Redis container");
        redisContainer.start();
        
        log.info("Starting LocalStack container");
        localStackContainer.start();
        
        log.info("All containers started successfully");
        logContainerDetails();
    }
    
    /**
     * Stops all Testcontainers.
     */
    private void stopContainers() {
        log.info("Stopping Testcontainers");
        
        if (localStackContainer != null && localStackContainer.isRunning()) {
            log.info("Stopping LocalStack container");
            localStackContainer.stop();
        }
        
        if (redisContainer != null && redisContainer.isRunning()) {
            log.info("Stopping Redis container");
            redisContainer.stop();
        }
        
        if (postgresContainer != null && postgresContainer.isRunning()) {
            log.info("Stopping PostgreSQL container");
            postgresContainer.stop();
        }
        
        log.info("All containers stopped successfully");
    }
    
    /**
     * Applies database migrations from all microservices.
     */
    private void applyDatabaseMigrations() {
        log.info("Applying database migrations");
        
        try {
            DatabaseMigrationUtil.applyMigrations(
                postgresContainer.getJdbcUrl(),
                postgresContainer.getUsername(),
                postgresContainer.getPassword()
            );
            
            log.info("Database migrations applied successfully");
            
        } catch (Exception e) {
            log.error("Failed to apply database migrations", e);
            throw new RuntimeException("Database migration failed", e);
        }
    }
    
    /**
     * Logs container connection details for debugging.
     */
    private void logContainerDetails() {
        log.info("Container connection details:");
        log.info("  PostgreSQL:");
        log.info("    - JDBC URL: {}", postgresContainer.getJdbcUrl());
        log.info("    - Host: {}", postgresContainer.getHost());
        log.info("    - Port: {}", postgresContainer.getPort());
        log.info("    - Database: {}", postgresContainer.getDatabaseName());
        log.info("    - Username: {}", postgresContainer.getUsername());
        
        log.info("  Redis:");
        log.info("    - Host: {}", redisContainer.getRedisHost());
        log.info("    - Port: {}", redisContainer.getRedisPort());
        log.info("    - Connection: {}", redisContainer.getConnectionString());
        
        log.info("  LocalStack (S3):");
        log.info("    - Endpoint: {}", localStackContainer.getEndpointUrl());
        log.info("    - Bucket: {}", localStackContainer.getBucketName());
        log.info("    - Region: {}", localStackContainer.getAwsRegion());
    }
    
    /**
     * Starts all microservices.
     * 
     * <p>This method should be overridden by subclasses that need to start services.
     * The default implementation does nothing (services can be started manually in tests).
     */
    protected void startServices() {
        if (!shouldStartServices()) {
            log.info("Service startup disabled (override shouldStartServices() to enable)");
            return;
        }
        
        log.info("Starting all microservices");
        
        try {
            // Create service orchestrator
            serviceOrchestrator = new org.sifunity.integration.service.ServiceOrchestrator();
            
            // Log exact Testcontainers connection details for debugging
            log.info("Testcontainers connection details:");
            log.info("  PostgreSQL JDBC URL: {}", postgresContainer.getJdbcUrl());
            log.info("  Redis host: {}", redisContainer.getRedisHost());
            log.info("  Redis port: {}", redisContainer.getRedisPort());
            log.info("  LocalStack endpoint: {}", localStackContainer.getEndpointUrl());
            log.info("  LocalStack bucket: {}", localStackContainer.getBucketName());

            // Start all services
            serviceOrchestrator.startAll(
                postgresContainer.getJdbcUrl(),
                postgresContainer.getUsername(),
                postgresContainer.getPassword(),
                redisContainer.getRedisHost(),
                redisContainer.getRedisPort(),
                localStackContainer.getEndpointUrl(),
                localStackContainer.getBucketName()
            );
            
            // Update Gateway port for RestAssured
            String gatewayUrl = serviceOrchestrator.getGatewayUrl();
            log.info("Gateway URL: {}", gatewayUrl);
            
            // Extract port from URL (format: http://localhost:PORT)
            int port = Integer.parseInt(gatewayUrl.substring(gatewayUrl.lastIndexOf(':') + 1));
            setGatewayPort(port);
            
            log.info("All microservices started successfully");
            
        } catch (Exception e) {
            log.error("Failed to start microservices", e);
            throw new RuntimeException("Failed to start microservices", e);
        }
    }
    
    /**
     * Stops all microservices.
     * 
     * <p>This method should be overridden by subclasses that started services.
     * The default implementation does nothing.
     */
    protected void stopServices() {
        if (serviceOrchestrator != null) {
            log.info("Stopping all microservices");
            
            try {
                serviceOrchestrator.stopAll();
                log.info("All microservices stopped successfully");
            } catch (Exception e) {
                log.error("Error stopping microservices", e);
            }
        } else {
            log.info("No services to stop");
        }
    }
    
    /**
     * Cleans up test data to ensure test isolation.
     * 
     * <p>This method should be overridden by subclasses to clean up their test data.
     * The default implementation clears the S3 bucket and Redis cache.
     */
    protected void cleanupTestData() {
        log.info("Cleaning up test data");
        
        // Clear S3 bucket
        if (localStackContainer != null) {
            localStackContainer.clearBucket();
        }
        
        // Clear Redis cache to ensure test isolation
        if (redisContainer != null) {
            redisContainer.clearCache();
        }
        
        // Subclasses should override to add more cleanup logic:
        // - Delete environments
        // - Delete ACL entries
        // - Delete provider registrations
        // - Delete queues
        // - Delete subscriptions
    }
    
    /**
     * Gets the PostgreSQL container.
     * 
     * @return the PostgreSQL container
     */
    protected PostgresTestContainer getPostgresContainer() {
        return postgresContainer;
    }
    
    /**
     * Gets the Redis container.
     * 
     * @return the Redis container
     */
    protected RedisTestContainer getRedisContainer() {
        return redisContainer;
    }
    
    /**
     * Gets the LocalStack container.
     * 
     * @return the LocalStack container
     */
    protected LocalStackTestContainer getLocalStackContainer() {
        return localStackContainer;
    }
    
    /**
     * Configures RestAssured for integration tests.
     * 
     * <p>This method:
     * <ul>
     *   <li>Sets the base URI and port for the Gateway</li>
     *   <li>Enables request/response logging on validation failure</li>
     *   <li>Configures default timeouts</li>
     * </ul>
     */
    private void configureRestAssured() {
        log.info("Configuring RestAssured");
        
        // Set base URI and port
        gatewayBaseUrl = "http://localhost";
        RestAssured.baseURI = gatewayBaseUrl;
        RestAssured.port = gatewayPort;
        
        // Enable logging on validation failure
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL);
        
        // Configure log config to pretty print
        RestAssured.config = RestAssured.config()
            .logConfig(LogConfig.logConfig()
                .enablePrettyPrinting(true)
                .enableLoggingOfRequestAndResponseIfValidationFails());
        
        log.info("RestAssured configured:");
        log.info("  - Base URI: {}", RestAssured.baseURI);
        log.info("  - Port: {}", RestAssured.port);
        log.info("  - Full URL: {}:{}", RestAssured.baseURI, RestAssured.port);
    }
    
    /**
     * Creates a base request specification with common headers.
     * 
     * @return a RequestSpecification with common configuration
     */
    protected RequestSpecification baseRequest() {
        return new RequestSpecBuilder()
            .setContentType("application/json")
            .setAccept("application/json")
            .build();
    }
    
    /**
     * Returns the gateway URL path prefix for the global admin domain/solution.
     * All gateway-routed requests must include /{domainId}/{solutionId} prefix.
     */
    protected String gatewayPathPrefix() {
        return "/" + org.sifunity.broker.common.constant.GlobalAdminConstants.GLOBAL_DOMAIN 
            + "/" + org.sifunity.broker.common.constant.GlobalAdminConstants.GLOBAL_SOLUTION;
    }
    
    /**
     * Creates a request specification with authentication headers.
     * 
     * @param sessionToken the session token
     * @param sharedSecret the shared secret for HMAC
     * @return a RequestSpecification with authentication headers
     */
    protected RequestSpecification authenticatedRequest(String sessionToken, String sharedSecret) {
        String timestamp = Instant.now().toString();
        String authHeader = generateAuthHeader(sessionToken, sharedSecret, timestamp);
        
        return new RequestSpecBuilder()
            .addRequestSpecification(baseRequest())
            .addHeader("Authorization", authHeader)
            .addHeader("timestamp", timestamp)
            .build();
    }
    
    /**
     * Creates a request specification with authentication and SIF headers.
     * 
     * @param sessionToken the session token
     * @param sharedSecret the shared secret for HMAC
     * @param zoneId the zone ID
     * @param contextId the context ID
     * @return a RequestSpecification with authentication and SIF headers
     */
    protected RequestSpecification sifRequest(
        String sessionToken,
        String sharedSecret,
        String zoneId,
        String contextId
    ) {
        String messageId = java.util.UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        String authHeader = generateAuthHeader(sessionToken, sharedSecret, timestamp);
        
        return new RequestSpecBuilder()
            .addRequestSpecification(baseRequest())
            .addHeader("Authorization", authHeader)
            .addHeader("timestamp", timestamp)
            .addHeader("messageId", messageId)
            .addHeader("zoneId", zoneId)
            .addHeader("contextId", contextId)
            .build();
    }
    
    /**
     * Generates a SIF HMACSHA256 authentication header.
     * 
     * @param sessionToken the session token
     * @param sharedSecret the shared secret
     * @param timestamp the timestamp
     * @return the authentication header value
     */
    protected String generateAuthHeader(String sessionToken, String sharedSecret, String timestamp) {
        try {
            // Create the string to sign: sessionToken:timestamp
            String toSign = sessionToken + ":" + timestamp;
            
            // Compute HMAC-SHA256 (must match SifAuthenticationUtils.computeHmac())
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8));
            
            // Base64 encode the HMAC
            String hmacBase64 = Base64.getEncoder().encodeToString(hmacBytes);
            
            // Return the full auth header: SIF_HMACSHA256 Base64(sessionToken:hmac)
            return "SIF_HMACSHA256 " + Base64.getEncoder().encodeToString((sessionToken + ":" + hmacBase64).getBytes(StandardCharsets.UTF_8));
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate auth header", e);
        }
    }
    
    /**
     * Creates common SIF headers for requests.
     * 
     * @param zoneId the zone ID
     * @param contextId the context ID
     * @return a map of SIF headers
     */
    protected Map<String, String> sifHeaders(String zoneId, String contextId) {
        return Map.of(
            "messageId", java.util.UUID.randomUUID().toString(),
            "zoneId", zoneId,
            "contextId", contextId,
            "timestamp", Instant.now().toString()
        );
    }
    
    /**
     * Sets the Gateway port for RestAssured.
     * 
     * <p>This method should be called by subclasses before setupInfrastructure()
     * if they need to use a different port than the default.
     * 
     * @param port the Gateway port
     */
    protected void setGatewayPort(int port) {
        gatewayPort = port;
    }
    
    /**
     * Gets the service orchestrator.
     * 
     * @return the service orchestrator, or null if services not started
     */
    protected org.sifunity.integration.service.ServiceOrchestrator getServiceOrchestrator() {
        return serviceOrchestrator;
    }
    
    /**
     * Gets the URL for a specific service.
     * 
     * @param serviceName the service name
     * @return the service URL
     * @throws IllegalStateException if services are not started
     */
    protected String getServiceUrl(String serviceName) {
        if (serviceOrchestrator == null) {
            throw new IllegalStateException("Services are not started");
        }
        return serviceOrchestrator.getServiceUrl(serviceName);
    }
    
    /**
     * Gets the Environment Service URL.
     * 
     * @return the Environment Service URL
     */
    protected String getEnvironmentServiceUrl() {
        return getServiceUrl("environment-service");
    }
    
    /**
     * Gets the Queue Service URL.
     * 
     * @return the Queue Service URL
     */
    protected String getQueueServiceUrl() {
        return getServiceUrl("queue-service");
    }
    
    /**
     * Gets the Subscription Service URL.
     * 
     * @return the Subscription Service URL
     */
    protected String getSubscriptionServiceUrl() {
        return getServiceUrl("subscription-service");
    }
}
