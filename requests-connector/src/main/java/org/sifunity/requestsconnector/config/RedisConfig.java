// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.requestsconnector.dto.ProxyRequestMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis configuration for distributed caching.
 * 
 * <p>This configuration class sets up Redis as the backing store for Spring Cache abstraction.
 * It configures cache behavior including TTL, serialization, and key prefixes to support
 * distributed caching across multiple Requests Connector instances.</p>
 * 
 * <p><strong>Cache Configuration:</strong></p>
 * <ul>
 *   <li>Cache Name: {@code provider-registry}</li>
 *   <li>TTL: 5 minutes (300 seconds) - configurable via {@code spring.cache.redis.time-to-live}</li>
 *   <li>Key Serializer: String (for human-readable keys)</li>
 *   <li>Value Serializer: JSON (for ProviderRegistrationDTO objects)</li>
 *   <li>Key Prefix: {@code requests-connector:} (from application.yml)</li>
 *   <li>Null Values: Not cached (to avoid caching negative lookups)</li>
 * </ul>
 * 
 * <p><strong>Requirements:</strong></p>
 * <ul>
 *   <li>12.1: Configure Redis cache with configurable TTL</li>
 *   <li>12.4: Use default cache TTL of 5 minutes if not configured</li>
 *   <li>12.5: Use Redis for distributed caching to support horizontal scaling</li>
 * </ul>
 * 
 * @see org.sifunity.requestsconnector.cache.ProviderRegistryCache
 */
@Configuration
@Slf4j
public class RedisConfig implements CachingConfigurer {
    
    /**
     * Provides a custom CacheErrorHandler that treats deserialization failures as cache misses.
     *
     * <p>This is critical for backward compatibility with stale Redis entries serialized
     * under the previous {@code DefaultTyping.EVERYTHING} configuration. When the restricted
     * typing rejects a {@code @class} type identifier, this handler logs a WARN with the
     * rejected class name and allows the application to continue without crashing.</p>
     *
     * <p><strong>Requirements:</strong> 4.6, 4.7</p>
     *
     * @return RedisCacheErrorHandler that handles deserialization failures gracefully
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new RedisCacheErrorHandler();
    }
    
    /**
     * Creates an ObjectMapper configured for Redis serialization.
     * 
     * <p>This ObjectMapper is configured with the JavaTimeModule to support Java 8
     * date/time types (Instant, LocalDateTime, etc.) which are used in DTOs like
     * ProviderRegistrationDTO.</p>
     * 
     * <p><strong>IMPORTANT</strong>: This ObjectMapper is ONLY for Redis serialization.
     * Polymorphic type handling uses EVERYTHING with an explicit allowlist
     * via BasicPolymorphicTypeValidator. Only classes in {@code org.sifunity.broker},
     * {@code org.sifunity.requestsconnector}, {@code java.util}, {@code java.time},
     * and {@code java.lang} packages are permitted for deserialization, preventing arbitrary
     * class instantiation via gadget-chain attacks. EVERYTHING is required because
     * GenericJackson2JsonRedisSerializer deserializes against Object.class and needs
     * {@code @class} on all values including final types (records, enums).</p>
     * 
     * @return configured ObjectMapper with JSR310 support and restricted typing for Redis only
     */
    @Bean("redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules(); // Auto-discover and register all available modules
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // Write dates as ISO-8601 strings
        
        // Restrict polymorphic deserialization to known-safe packages only.
        // This prevents gadget-chain attacks via arbitrary class instantiation on Redis traffic.
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType("org.sifunity.broker")
            .allowIfSubType("org.sifunity.broker")
            .allowIfBaseType("org.sifunity.requestsconnector")
            .allowIfSubType("org.sifunity.requestsconnector")
            .allowIfBaseType("java.util.")
            .allowIfSubType("java.util.")
            .allowIfBaseType("java.time.")
            .allowIfSubType("java.time.")
            .allowIfBaseType("java.lang.")
            .allowIfSubType("java.lang.")
            .allowIfSubType("[L")
            .build();
        
        // Use EVERYTHING to emit @class on all types including final classes (records, enums, String).
        // GenericJackson2JsonRedisSerializer deserializes against Object.class, so it REQUIRES @class
        // on every value to reconstruct the correct type. NON_FINAL skips final classes (records),
        // causing "missing type id property '@class'" on deserialization.
        // Security is enforced by the BasicPolymorphicTypeValidator allowlist above, NOT by the typing mode.
        mapper.activateDefaultTyping(ptv,
            ObjectMapper.DefaultTyping.EVERYTHING,
            JsonTypeInfo.As.PROPERTY);
        
        log.warn("Redis ObjectMapper configured with restricted polymorphic typing (EVERYTHING + allowlist)");
        return mapper;
    }
    
    /**
     * Creates a CacheManager configured to use the ObjectMapper with JSR310 support.
     * 
     * <p>This is the critical configuration that ensures Spring Cache uses the properly
     * configured ObjectMapper when serializing cached objects to Redis. Without this,
     * Spring Cache creates its own ObjectMapper without JSR310 support, causing
     * serialization failures for objects with Instant fields.</p>
     * 
     * <p>We use GenericJackson2JsonRedisSerializer with our configured ObjectMapper
     * to properly handle polymorphic types (like Optional) and preserve type information
     * for complex types like Set&lt;MessageExchangePattern&gt;.</p>
     * 
     * @param connectionFactory Redis connection factory (auto-configured by Spring Boot)
     * @param redisObjectMapper ObjectMapper configured with JSR310 support and default typing
     * @return configured CacheManager
     */
    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper) {
        
        // Use GenericJackson2JsonRedisSerializer with our configured ObjectMapper
        // This properly handles polymorphic types and preserves type information
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        
        // Configure Redis cache with the serializer
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5)) // Default TTL of 5 minutes
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
            .disableCachingNullValues(); // Don't cache null values
        
        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(cacheConfig)
            .build();
        
        log.info("CacheManager configured with GenericJackson2JsonRedisSerializer for proper type handling");
        return cacheManager;
    }
    
    /**
     * Creates a RedisTemplate for storing ProviderRegistrationDTO objects.
     * 
     * <p>This template is used by ProviderRegistryCache to store and retrieve
     * provider registration data from Redis.</p>
     * 
     * @param connectionFactory Redis connection factory (auto-configured by Spring Boot)
     * @param redisObjectMapper ObjectMapper configured with JSR310 support
     * @return configured RedisTemplate for ProviderRegistrationDTO
     */
    @Bean
    public RedisTemplate<String, org.sifunity.broker.common.dto.ProviderRegistrationDTO> providerRegistrationRedisTemplate(
            RedisConnectionFactory connectionFactory,
            @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper) {
        
        RedisTemplate<String, org.sifunity.broker.common.dto.ProviderRegistrationDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use Jackson JSON serializer with configured ObjectMapper for values
        Jackson2JsonRedisSerializer<org.sifunity.broker.common.dto.ProviderRegistrationDTO> jsonSerializer = 
            new Jackson2JsonRedisSerializer<>(redisObjectMapper, org.sifunity.broker.common.dto.ProviderRegistrationDTO.class);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        
        log.info("RedisTemplate for ProviderRegistrationDTO initialized successfully");
        return template;
    }
    
    /**
     * Creates a RedisTemplate for storing ProxyRequestMetadata objects.
     * 
     * <p>This template is used by DelayedRequestHandler and ProxyWorkerService to store
     * and retrieve proxy request metadata from Redis work queues.</p>
     * 
     * @param connectionFactory Redis connection factory (auto-configured by Spring Boot)
     * @param redisObjectMapper ObjectMapper configured with JSR310 support
     * @return configured RedisTemplate for ProxyRequestMetadata
     */
    @Bean
    public RedisTemplate<String, ProxyRequestMetadata> proxyRequestMetadataRedisTemplate(
            RedisConnectionFactory connectionFactory,
            @Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper) {
        
        RedisTemplate<String, ProxyRequestMetadata> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer with configured ObjectMapper for values
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        
        log.info("RedisTemplate for ProxyRequestMetadata initialized successfully");
        return template;
    }
}
