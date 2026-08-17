// SPDX-License-Identifier: MIT
package org.sifunity.integration.config;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration model for microservice startup during integration tests.
 * 
 * <p>This class contains all the configuration needed to start a microservice
 * in the test environment, including:
 * <ul>
 *   <li>Service identification (name)</li>
 *   <li>Network configuration (port)</li>
 *   <li>Database connection (PostgreSQL)</li>
 *   <li>Cache connection (Redis)</li>
 *   <li>Storage connection (S3/LocalStack)</li>
 *   <li>Additional service-specific properties</li>
 * </ul>
 * 
 * <p>Use the builder pattern to create instances:
 * <pre>
 * ServiceConfig config = ServiceConfig.builder()
 *     .serviceName("environment-service")
 *     .port(8081)
 *     .postgresUrl("jdbc:postgresql://localhost:5432/test")
 *     .redisHost("localhost")
 *     .redisPort(6379)
 *     .s3Endpoint("http://localhost:4566")
 *     .s3Bucket("test-bucket")
 *     .build();
 * </pre>
 * 
 * @since 1.0.0
 */
@Data
@Builder
public class ServiceConfig {
    
    /**
     * The name of the service (e.g., "environment-service", "queue-service").
     */
    private String serviceName;
    
    /**
     * The port the service should listen on.
     * Use 0 for random available port.
     */
    @Builder.Default
    private int port = 0;
    
    /**
     * The PostgreSQL JDBC URL.
     * Example: jdbc:postgresql://localhost:5432/sif_broker_test
     */
    private String postgresUrl;
    
    /**
     * The PostgreSQL username.
     */
    private String postgresUsername;
    
    /**
     * The PostgreSQL password.
     */
    private String postgresPassword;
    
    /**
     * The Redis host.
     */
    private String redisHost;
    
    /**
     * The Redis port.
     */
    private int redisPort;
    
    /**
     * The S3 endpoint URL (LocalStack).
     * Example: http://localhost:4566
     */
    private String s3Endpoint;
    
    /**
     * The S3 bucket name.
     */
    private String s3Bucket;
    
    /**
     * The S3 region.
     */
    @Builder.Default
    private String s3Region = "us-east-1";
    
    /**
     * The S3 access key (for LocalStack).
     */
    @Builder.Default
    private String s3AccessKey = "test";
    
    /**
     * The S3 secret key (for LocalStack).
     */
    @Builder.Default
    private String s3SecretKey = "test";
    
    /**
     * Additional service-specific properties.
     * These will be passed to the service as Spring Boot properties.
     */
    @Builder.Default
    private Map<String, String> additionalProperties = new HashMap<>();
    
    /**
     * Adds an additional property to the configuration.
     * 
     * @param key the property key
     * @param value the property value
     * @return this config instance for chaining
     */
    public ServiceConfig addProperty(String key, String value) {
        if (additionalProperties == null) {
            additionalProperties = new HashMap<>();
        }
        additionalProperties.put(key, value);
        return this;
    }
    
    /**
     * Adds multiple additional properties to the configuration.
     * 
     * @param properties the properties to add
     * @return this config instance for chaining
     */
    public ServiceConfig addProperties(Map<String, String> properties) {
        if (additionalProperties == null) {
            additionalProperties = new HashMap<>();
        }
        additionalProperties.putAll(properties);
        return this;
    }
    
    /**
     * Converts this configuration to a map of Spring Boot properties.
     * 
     * @return map of property keys to values
     */
    public Map<String, Object> toSpringProperties() {
        Map<String, Object> properties = new HashMap<>();
        
        // Server configuration
        properties.put("server.port", String.valueOf(port));
        properties.put("spring.application.name", serviceName);
        
        // Database configuration
        if (postgresUrl != null) {
            properties.put("spring.datasource.url", postgresUrl);
        }
        if (postgresUsername != null) {
            properties.put("spring.datasource.username", postgresUsername);
        }
        if (postgresPassword != null) {
            properties.put("spring.datasource.password", postgresPassword);
        }
        
        // Hibernate and driver configuration - prevent auto-detection in child JVM
        // Only add when service uses a database (gateway is reactive, no JPA)
        if (postgresUrl != null) {
            properties.put("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        }
        
        // Redis configuration
        if (redisHost != null) {
            properties.put("spring.data.redis.host", redisHost);
        }
        if (redisPort > 0) {
            properties.put("spring.data.redis.port", String.valueOf(redisPort));
        }
        
        // S3 configuration
        if (s3Endpoint != null) {
            properties.put("sif.broker.storage.s3.endpoint", s3Endpoint);
        }
        if (s3Bucket != null) {
            properties.put("message.storage.s3.bucket", s3Bucket);
        }
        if (s3Region != null) {
            properties.put("message.storage.s3.region", s3Region);
        }
        if (s3AccessKey != null) {
            properties.put("sif.broker.storage.s3.access-key", s3AccessKey);
        }
        if (s3SecretKey != null) {
            properties.put("sif.broker.storage.s3.secret-key", s3SecretKey);
        }
        
        // Additional properties
        if (additionalProperties != null) {
            properties.putAll(additionalProperties);
        }
        
        return properties;
    }
    
    /**
     * Creates a default configuration for a service using container connection details.
     * 
     * @param serviceName the service name
     * @param postgresUrl the PostgreSQL JDBC URL
     * @param postgresUsername the PostgreSQL username
     * @param postgresPassword the PostgreSQL password
     * @param redisHost the Redis host
     * @param redisPort the Redis port
     * @param s3Endpoint the S3 endpoint URL
     * @param s3Bucket the S3 bucket name
     * @return a new ServiceConfig instance
     */
    public static ServiceConfig createDefault(
        String serviceName,
        String postgresUrl,
        String postgresUsername,
        String postgresPassword,
        String redisHost,
        int redisPort,
        String s3Endpoint,
        String s3Bucket
    ) {
        // Assign fixed ports for each service to avoid port conflicts
        int servicePort = getDefaultPortForService(serviceName);
        
        return ServiceConfig.builder()
            .serviceName(serviceName)
            .port(servicePort)
            .postgresUrl(postgresUrl)
            .postgresUsername(postgresUsername)
            .postgresPassword(postgresPassword)
            .redisHost(redisHost)
            .redisPort(redisPort)
            .s3Endpoint(s3Endpoint)
            .s3Bucket(s3Bucket)
            .build();
    }
    
    /**
     * Gets the default port for a service.
     * Uses fixed ports to avoid conflicts and make debugging easier.
     * 
     * @param serviceName the service name
     * @return the default port for the service
     */
    private static int getDefaultPortForService(String serviceName) {
        return switch (serviceName) {
            case "environment-service" -> 18081;
            case "queue-service" -> 18082;
            case "subscription-service" -> 18083;
            case "job-service" -> 18084;
            case "requests-connector" -> 18085;
            case "responses-connector" -> 18086;
            case "events-connector" -> 18087;
            case "services-connector" -> 18088;
            case "gateway" -> 18080;
            default -> 0; // Random port for unknown services
        };
    }
}
