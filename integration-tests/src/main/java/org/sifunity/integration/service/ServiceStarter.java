// SPDX-License-Identifier: MIT
package org.sifunity.integration.service;

import org.sifunity.integration.config.ServiceConfig;

/**
 * Interface for starting and managing microservices during integration tests.
 * 
 * <p>Implementations of this interface are responsible for:
 * <ul>
 *   <li>Starting a microservice with the provided configuration</li>
 *   <li>Stopping the microservice</li>
 *   <li>Checking if the service is healthy</li>
 *   <li>Providing the service's base URL for API calls</li>
 * </ul>
 * 
 * <p>Different implementations can use different strategies:
 * <ul>
 *   <li>Embedded Spring Boot: Start service in the same JVM</li>
 *   <li>Docker containers: Start service in a Docker container</li>
 *   <li>Process execution: Start service as a separate JVM process</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * ServiceStarter starter = new EmbeddedServiceStarter();
 * ServiceConfig config = ServiceConfig.builder()
 *     .serviceName("environment-service")
 *     .port(8081)
 *     .build();
 * 
 * starter.start(config);
 * 
 * if (starter.isHealthy()) {
 *     String baseUrl = starter.getBaseUrl();
 *     // Make API calls to baseUrl
 * }
 * 
 * starter.stop();
 * </pre>
 * 
 * @since 1.0.0
 */
public interface ServiceStarter {
    
    /**
     * Starts the microservice with the provided configuration.
     * 
     * <p>This method should:
     * <ol>
     *   <li>Start the service with the provided configuration</li>
     *   <li>Wait for the service to be ready (health check passes)</li>
     *   <li>Return once the service is healthy</li>
     * </ol>
     * 
     * <p>If the service fails to start or become healthy within a timeout,
     * this method should throw an exception.
     * 
     * @param config the service configuration
     * @throws RuntimeException if the service fails to start
     */
    void start(ServiceConfig config);
    
    /**
     * Stops the microservice.
     * 
     * <p>This method should:
     * <ol>
     *   <li>Gracefully shut down the service</li>
     *   <li>Release any resources (ports, threads, etc.)</li>
     *   <li>Return once the service is fully stopped</li>
     * </ol>
     * 
     * <p>If the service is not running, this method should do nothing.
     */
    void stop();
    
    /**
     * Checks if the microservice is healthy.
     * 
     * <p>This method should call the service's health check endpoint
     * (typically /actuator/health) and return true if the service
     * responds with a healthy status.
     * 
     * @return true if the service is healthy, false otherwise
     */
    boolean isHealthy();
    
    /**
     * Gets the port the service is listening on.
     * 
     * <p>If the service was configured with port 0 (random port),
     * this method returns the actual port that was assigned.
     * 
     * @return the service port
     * @throws IllegalStateException if the service is not running
     */
    int getPort();
    
    /**
     * Gets the base URL for making API calls to the service.
     * 
     * <p>The base URL includes the protocol, host, and port.
     * Example: http://localhost:8081
     * 
     * @return the base URL
     * @throws IllegalStateException if the service is not running
     */
    String getBaseUrl();
    
    /**
     * Gets the service name.
     * 
     * @return the service name
     */
    String getServiceName();
    
    /**
     * Checks if the service is currently running.
     * 
     * @return true if the service is running, false otherwise
     */
    boolean isRunning();
}
