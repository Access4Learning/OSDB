// SPDX-License-Identifier: MIT
package org.sifunity.integration.service;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.integration.config.ServiceConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestrates the startup and shutdown of all microservices in the correct order.
 * 
 * <p>The service startup order is critical to ensure dependencies are available:
 * <ol>
 *   <li>Environment Service (foundational - provides authentication/authorization)</li>
 *   <li>Core Services (Queue, Subscription, Job) - can start in parallel</li>
 *   <li>Connector Services (Requests, Responses, Events, Services) - can start in parallel</li>
 *   <li>API Gateway (entry point - routes to all services)</li>
 * </ol>
 * 
 * <p>Each service is started with test-specific configuration and waits for
 * its health check to pass before proceeding to the next service.
 * 
 * <p>Example usage:
 * <pre>
 * ServiceOrchestrator orchestrator = new ServiceOrchestrator();
 * orchestrator.startAll(
 *     postgresUrl, postgresUsername, postgresPassword,
 *     redisHost, redisPort,
 *     s3Endpoint, s3Bucket
 * );
 * 
 * // Services are now running and healthy
 * String gatewayUrl = orchestrator.getGatewayUrl();
 * 
 * // After tests complete
 * orchestrator.stopAll();
 * </pre>
 * 
 * @since 1.0.0
 */
@Slf4j
public class ServiceOrchestrator {
    
    private final Map<String, ServiceStarter> services = new HashMap<>();
    
    // Service names
    private static final String ENVIRONMENT_SERVICE = "environment-service";
    private static final String QUEUE_SERVICE = "queue-service";
    private static final String SUBSCRIPTION_SERVICE = "subscription-service";
    private static final String JOB_SERVICE = "job-service";
    private static final String REQUESTS_CONNECTOR = "requests-connector";
    private static final String RESPONSES_CONNECTOR = "responses-connector";
    private static final String EVENTS_CONNECTOR = "events-connector";
    private static final String SERVICES_CONNECTOR = "services-connector";
    private static final String GATEWAY = "gateway";
    
    /**
     * Starts all microservices in the correct order.
     * 
     * @param postgresUrl the PostgreSQL JDBC URL
     * @param postgresUsername the PostgreSQL username
     * @param postgresPassword the PostgreSQL password
     * @param redisHost the Redis host
     * @param redisPort the Redis port
     * @param s3Endpoint the S3 endpoint URL
     * @param s3Bucket the S3 bucket name
     */
    public void startAll(
        String postgresUrl,
        String postgresUsername,
        String postgresPassword,
        String redisHost,
        int redisPort,
        String s3Endpoint,
        String s3Bucket
    ) {
        log.info("=".repeat(80));
        log.info("Starting all microservices");
        log.info("=".repeat(80));
        
        try {
            // Phase 1: Start Environment Service (foundational)
            log.info("Phase 1: Starting Environment Service");
            startEnvironmentService(postgresUrl, postgresUsername, postgresPassword, redisHost, redisPort);
            
            // Phase 2: Start Core Services (parallel)
            log.info("Phase 2: Starting Core Services (Queue, Subscription, Job)");
            startQueueService(postgresUrl, postgresUsername, postgresPassword, redisHost, redisPort, s3Endpoint, s3Bucket);
            startSubscriptionService(postgresUrl, postgresUsername, postgresPassword, redisHost, redisPort);
            startJobService(postgresUrl, postgresUsername, postgresPassword, redisHost, redisPort);
            
            // Phase 3: Start Connector Services (Responses first, then others)
            log.info("Phase 3: Starting Connector Services (Requests, Responses, Events, Services)");
            startResponsesConnector(postgresUrl, postgresUsername, postgresPassword, redisHost, redisPort);
            startRequestsConnector(postgresUrl, postgresUsername, postgresPassword, redisHost, redisPort);
            startEventsConnector(postgresUrl, postgresUsername, postgresPassword, redisHost, redisPort);
            startServicesConnector(postgresUrl, postgresUsername, postgresPassword, redisHost, redisPort);
            
            // Phase 4: Start API Gateway (entry point)
            log.info("Phase 4: Starting API Gateway");
            startGateway(postgresUrl, postgresUsername, postgresPassword, redisHost, redisPort);
            
            log.info("=".repeat(80));
            log.info("All microservices started successfully");
            log.info("=".repeat(80));
            logServiceUrls();
            
        } catch (Exception e) {
            log.error("Failed to start all microservices", e);
            stopAll(); // Clean up any started services
            throw new RuntimeException("Failed to start microservices", e);
        }
    }
    
    /**
     * Stops all microservices in reverse order.
     */
    public void stopAll() {
        log.info("=".repeat(80));
        log.info("Stopping all microservices");
        log.info("=".repeat(80));
        
        // Stop in reverse order
        stopService(GATEWAY);
        stopService(SERVICES_CONNECTOR);
        stopService(EVENTS_CONNECTOR);
        stopService(RESPONSES_CONNECTOR);
        stopService(REQUESTS_CONNECTOR);
        stopService(JOB_SERVICE);
        stopService(SUBSCRIPTION_SERVICE);
        stopService(QUEUE_SERVICE);
        stopService(ENVIRONMENT_SERVICE);
        
        log.info("All microservices stopped");
    }
    
    /**
     * Gets the API Gateway base URL.
     * 
     * @return the Gateway URL
     */
    public String getGatewayUrl() {
        ServiceStarter gateway = services.get(GATEWAY);
        if (gateway == null || !gateway.isRunning()) {
            throw new IllegalStateException("Gateway is not running");
        }
        return gateway.getBaseUrl();
    }
    
    /**
     * Gets the base URL for a specific service.
     * 
     * @param serviceName the service name
     * @return the service URL
     */
    public String getServiceUrl(String serviceName) {
        ServiceStarter service = services.get(serviceName);
        if (service == null || !service.isRunning()) {
            throw new IllegalStateException("Service is not running: " + serviceName);
        }
        return service.getBaseUrl();
    }
    
    /**
     * Gets all service URLs.
     * 
     * @return map of service names to URLs
     */
    public Map<String, String> getAllServiceUrls() {
        Map<String, String> urls = new HashMap<>();
        services.forEach((name, starter) -> {
            if (starter.isRunning()) {
                urls.put(name, starter.getBaseUrl());
            }
        });
        return urls;
    }
    
    // Private methods for starting individual services
    
    private void startEnvironmentService(String postgresUrl, String postgresUsername, String postgresPassword, 
                                        String redisHost, int redisPort) {
        ServiceConfig config = ServiceConfig.createDefault(
            ENVIRONMENT_SERVICE, postgresUrl, postgresUsername, postgresPassword,
            redisHost, redisPort, null, null
        );
        
        // Add bootstrap configuration for ADMIN environment
        config.addProperty("sif.broker.bootstrap.enabled", "true");
        config.addProperty("sif.broker.bootstrap.admin.sessionToken", "test-admin-token");
        config.addProperty("sif.broker.bootstrap.admin.sharedSecret", "dGVzdC1hZG1pbi1zZWNyZXQ=");
        
        ServiceStarter starter = new EmbeddedServiceStarter(ENVIRONMENT_SERVICE);
        starter.start(config);
        services.put(ENVIRONMENT_SERVICE, starter);
    }
    
    private void startQueueService(String postgresUrl, String postgresUsername, String postgresPassword,
                                   String redisHost, int redisPort, String s3Endpoint, String s3Bucket) {
        ServiceConfig config = ServiceConfig.createDefault(
            QUEUE_SERVICE, postgresUrl, postgresUsername, postgresPassword,
            redisHost, redisPort, s3Endpoint, s3Bucket
        );
        
        // Configure filesystem storage for queue-service (temporarily for debugging)
        config.addProperty("message.storage.type", "filesystem");
        config.addProperty("message.storage.filesystem.path", "./data/test-messages");
        
        ServiceStarter starter = new EmbeddedServiceStarter(QUEUE_SERVICE);
        starter.start(config);
        services.put(QUEUE_SERVICE, starter);
    }
    
    private void startSubscriptionService(String postgresUrl, String postgresUsername, String postgresPassword,
                                         String redisHost, int redisPort) {
        ServiceConfig config = ServiceConfig.createDefault(
            SUBSCRIPTION_SERVICE, postgresUrl, postgresUsername, postgresPassword,
            redisHost, redisPort, null, null
        );
        
        // Configure queue-service URL for health check
        config.addProperty("queue.service.url", getServiceUrl(QUEUE_SERVICE));
        
        ServiceStarter starter = new EmbeddedServiceStarter(SUBSCRIPTION_SERVICE);
        starter.start(config);
        services.put(SUBSCRIPTION_SERVICE, starter);
    }
    
    private void startJobService(String postgresUrl, String postgresUsername, String postgresPassword,
                                String redisHost, int redisPort) {
        ServiceConfig config = ServiceConfig.createDefault(
            JOB_SERVICE, postgresUrl, postgresUsername, postgresPassword,
            redisHost, redisPort, null, null
        );
        
        ServiceStarter starter = new EmbeddedServiceStarter(JOB_SERVICE);
        starter.start(config);
        services.put(JOB_SERVICE, starter);
    }
    
    private void startRequestsConnector(String postgresUrl, String postgresUsername, String postgresPassword,
                                       String redisHost, int redisPort) {
        ServiceConfig config = ServiceConfig.createDefault(
            REQUESTS_CONNECTOR, postgresUrl, postgresUsername, postgresPassword,
            redisHost, redisPort, null, null
        );
        
        // Add service URLs for inter-service communication
        config.addProperty("environment.service.url", getServiceUrl(ENVIRONMENT_SERVICE));
        config.addProperty("queue.service.url", getServiceUrl(QUEUE_SERVICE));
        config.addProperty("subscription.service.url", getServiceUrl(SUBSCRIPTION_SERVICE));
        config.addProperty("responses.connector.url", getServiceUrl(RESPONSES_CONNECTOR));
        
        // Enable proxy worker for DELAYED_to_IMMEDIATE pattern
        config.addProperty("proxy.worker.enabled", "true");
        config.addProperty("proxy.worker.poll-interval", "500");  // Poll every 500ms for faster test execution
        
        // Configure shorter provider timeouts for integration tests
        config.addProperty("provider.timeout.connection", "5000");  // 5 seconds
        config.addProperty("provider.timeout.read", "5000");  // 5 seconds (shorter than WireMock's 10s delay)
        
        ServiceStarter starter = new EmbeddedServiceStarter(REQUESTS_CONNECTOR);
        starter.start(config);
        services.put(REQUESTS_CONNECTOR, starter);
    }
    
    private void startResponsesConnector(String postgresUrl, String postgresUsername, String postgresPassword,
                                        String redisHost, int redisPort) {
        ServiceConfig config = ServiceConfig.createDefault(
            RESPONSES_CONNECTOR, postgresUrl, postgresUsername, postgresPassword,
            redisHost, redisPort, null, null
        );
        
        // Add service URLs for inter-service communication
        config.addProperty("queue.service.url", getServiceUrl(QUEUE_SERVICE));
        
        ServiceStarter starter = new EmbeddedServiceStarter(RESPONSES_CONNECTOR);
        starter.start(config);
        services.put(RESPONSES_CONNECTOR, starter);
    }
    
    private void startEventsConnector(String postgresUrl, String postgresUsername, String postgresPassword,
                                     String redisHost, int redisPort) {
        ServiceConfig config = ServiceConfig.createDefault(
            EVENTS_CONNECTOR, postgresUrl, postgresUsername, postgresPassword,
            redisHost, redisPort, null, null
        );
        
        // Add service URLs for inter-service communication
        config.addProperty("subscription-service.url", getServiceUrl(SUBSCRIPTION_SERVICE));
        config.addProperty("queue-service.url", getServiceUrl(QUEUE_SERVICE));
        
        ServiceStarter starter = new EmbeddedServiceStarter(EVENTS_CONNECTOR);
        starter.start(config);
        services.put(EVENTS_CONNECTOR, starter);
    }
    
    private void startServicesConnector(String postgresUrl, String postgresUsername, String postgresPassword,
                                       String redisHost, int redisPort) {
        ServiceConfig config = ServiceConfig.createDefault(
            SERVICES_CONNECTOR, postgresUrl, postgresUsername, postgresPassword,
            redisHost, redisPort, null, null
        );
        
        // Add service URLs for inter-service communication
        config.addProperty("environment.service.url", getServiceUrl(ENVIRONMENT_SERVICE));
        config.addProperty("job.service.url", getServiceUrl(JOB_SERVICE));
        
        ServiceStarter starter = new EmbeddedServiceStarter(SERVICES_CONNECTOR);
        starter.start(config);
        services.put(SERVICES_CONNECTOR, starter);
    }
    
    private void startGateway(String postgresUrl, String postgresUsername, String postgresPassword,
                             String redisHost, int redisPort) {
        ServiceConfig config = ServiceConfig.createDefault(
            GATEWAY, null, null, null,
            redisHost, redisPort, null, null
        );
        
        // Allow bean definition overriding for custom RedisRateLimiter
        config.addProperty("spring.main.allow-bean-definition-overriding", "true");
        
        // Force reactive web application type (Spring Cloud Gateway requires WebFlux, not MVC)
        config.addProperty("spring.main.web-application-type", "reactive");
        
        // Disable rate limiting for integration tests
        config.addProperty("sif.broker.gateway.rate-limit.enabled", "false");
        
        // Add service URLs for routing (gateway uses environment-service.url, not sif.broker.services.environment-service.url)
        config.addProperty("environment-service.url", getServiceUrl(ENVIRONMENT_SERVICE));
        config.addProperty("sif.broker.services.requests-connector.url", getServiceUrl(REQUESTS_CONNECTOR));
        config.addProperty("sif.broker.services.responses-connector.url", getServiceUrl(RESPONSES_CONNECTOR));
        config.addProperty("sif.broker.services.events-connector.url", getServiceUrl(EVENTS_CONNECTOR));
        config.addProperty("sif.broker.services.services-connector.url", getServiceUrl(SERVICES_CONNECTOR));
        config.addProperty("sif.broker.services.queue-service.url", getServiceUrl(QUEUE_SERVICE));
        config.addProperty("sif.broker.services.subscription-service.url", getServiceUrl(SUBSCRIPTION_SERVICE));
        
        ServiceStarter starter = new EmbeddedServiceStarter(GATEWAY);
        starter.start(config);
        services.put(GATEWAY, starter);
    }
    
    private void stopService(String serviceName) {
        ServiceStarter service = services.get(serviceName);
        if (service != null && service.isRunning()) {
            log.info("Stopping service: {}", serviceName);
            service.stop();
        }
    }
    
    private void logServiceUrls() {
        log.info("Service URLs:");
        services.forEach((name, starter) -> {
            if (starter.isRunning()) {
                log.info("  - {}: {}", name, starter.getBaseUrl());
            }
        });
    }
}
