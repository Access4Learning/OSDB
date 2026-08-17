// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.builder;

import org.sifunity.broker.common.dto.*;
import org.sifunity.broker.common.constant.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Test builders for creating DTO instances with sensible defaults.
 * 
 * <p>These builders provide a fluent API for constructing test DTOs,
 * making test code more readable and maintainable. Each builder includes
 * sensible default values so tests only need to specify the fields they
 * care about.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * EnvironmentDTO env = TestBuilders.environment()
 *     .applicationName("MyApp")
 *     .type(EnvironmentType.BROKERED)
 *     .build();
 * </pre>
 */
public class TestBuilders {
    
    /**
     * Builder for creating EnvironmentDTO instances with sensible defaults.
     * 
     * <p>Default values:</p>
     * <ul>
     *   <li>id: Random UUID</li>
     *   <li>applicationName: "TestApp"</li>
     *   <li>sessionToken: Random UUID string</li>
     *   <li>createdAt: Current timestamp</li>
     *   <li>expiresAt: null</li>
     *   <li>defaultZoneId: "default"</li>
     *   <li>type: CONSUMER</li>
     * </ul>
     */
    public static class EnvironmentDTOBuilder {
        private UUID id = UUID.randomUUID();
        private String applicationName = "TestApp";
        private String sessionToken = UUID.randomUUID().toString();
        private Instant createdAt = Instant.now();
        private Instant expiresAt = null;
        private String defaultZoneId = "default";
        private EnvironmentType type = EnvironmentType.BROKERED;
        
        /**
         * Sets the environment ID.
         * 
         * @param id the environment ID
         * @return this builder for method chaining
         */
        public EnvironmentDTOBuilder id(UUID id) {
            this.id = id;
            return this;
        }
        
        /**
         * Sets the application name.
         * 
         * @param name the application name
         * @return this builder for method chaining
         */
        public EnvironmentDTOBuilder applicationName(String name) {
            this.applicationName = name;
            return this;
        }
        
        /**
         * Sets the session token.
         * 
         * @param token the session token
         * @return this builder for method chaining
         */
        public EnvironmentDTOBuilder sessionToken(String token) {
            this.sessionToken = token;
            return this;
        }
        
        /**
         * Sets the creation timestamp.
         * 
         * @param createdAt the creation timestamp
         * @return this builder for method chaining
         */
        public EnvironmentDTOBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        /**
         * Sets the expiration timestamp.
         * 
         * @param expiresAt the expiration timestamp
         * @return this builder for method chaining
         */
        public EnvironmentDTOBuilder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }
        
        /**
         * Sets the default zone ID.
         * 
         * @param zoneId the default zone ID
         * @return this builder for method chaining
         */
        public EnvironmentDTOBuilder defaultZoneId(String zoneId) {
            this.defaultZoneId = zoneId;
            return this;
        }
        
        /**
         * Sets the environment type.
         * 
         * @param type the environment type
         * @return this builder for method chaining
         */
        public EnvironmentDTOBuilder type(EnvironmentType type) {
            this.type = type;
            return this;
        }
        
        /**
         * Builds the EnvironmentDTO instance.
         * 
         * @return a new EnvironmentDTO with the configured values
         */
        public EnvironmentDTO build() {
            return new EnvironmentDTO(id, applicationName, sessionToken, createdAt, 
                                     expiresAt, defaultZoneId, type, null);
        }
    }
    
    /**
     * Builder for creating QueueDTO instances with sensible defaults.
     * 
     * <p>Default values:</p>
     * <ul>
     *   <li>id: Random UUID</li>
     *   <li>name: "test-queue"</li>
     *   <li>ownerId: Random UUID</li>
     *   <li>createdAt: Current timestamp</li>
     *   <li>maxRetries: 3</li>
     *   <li>retryDelayMs: 5000</li>
     *   <li>messageCount: 0</li>
     * </ul>
     */
    public static class QueueDTOBuilder {
        private UUID id = UUID.randomUUID();
        private String name = "test-queue";
        private UUID ownerId = UUID.randomUUID();
        private String polling = "IMMEDIATE";
        private String queueUri = null;
        private Integer maxConcurrentConnections = 1;
        private Instant created = Instant.now();
        private Instant lastAccessed = null;
        private Instant lastModified = null;
        private Integer maxRetries = 3;
        private Long retryDelayMs = 5000L;
        private Long messageCount = 0L;
        
        public QueueDTOBuilder id(UUID id) {
            this.id = id;
            return this;
        }
        
        public QueueDTOBuilder name(String name) {
            this.name = name;
            return this;
        }
        
        public QueueDTOBuilder ownerId(UUID ownerId) {
            this.ownerId = ownerId;
            return this;
        }
        
        public QueueDTOBuilder polling(String polling) {
            this.polling = polling;
            return this;
        }
        
        public QueueDTOBuilder queueUri(String queueUri) {
            this.queueUri = queueUri;
            return this;
        }
        
        public QueueDTOBuilder maxConcurrentConnections(Integer maxConcurrentConnections) {
            this.maxConcurrentConnections = maxConcurrentConnections;
            return this;
        }
        
        public QueueDTOBuilder created(Instant created) {
            this.created = created;
            return this;
        }
        
        /**
         * @deprecated Use {@link #created(Instant)} instead
         */
        @Deprecated
        public QueueDTOBuilder createdAt(Instant createdAt) {
            this.created = createdAt;
            return this;
        }
        
        public QueueDTOBuilder lastAccessed(Instant lastAccessed) {
            this.lastAccessed = lastAccessed;
            return this;
        }
        
        public QueueDTOBuilder lastModified(Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }
        
        public QueueDTOBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public QueueDTOBuilder retryDelayMs(Long retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
            return this;
        }
        
        public QueueDTOBuilder messageCount(Long messageCount) {
            this.messageCount = messageCount;
            return this;
        }
        
        /**
         * Builds the QueueDTO instance.
         * 
         * @return a new QueueDTO with the configured values
         */
        public QueueDTO build() {
            return new QueueDTO(id, name, ownerId, polling, queueUri, maxConcurrentConnections,
                               created, lastAccessed, lastModified, maxRetries, retryDelayMs, messageCount);
        }
    }
    
    /**
     * Creates a new EnvironmentDTOBuilder with default values.
     * 
     * @return a new EnvironmentDTOBuilder instance
     */
    public static EnvironmentDTOBuilder environment() {
        return new EnvironmentDTOBuilder();
    }
    
    /**
     * Creates a new QueueDTOBuilder with default values.
     * 
     * @return a new QueueDTOBuilder instance
     */
    public static QueueDTOBuilder queue() {
        return new QueueDTOBuilder();
    }
}
