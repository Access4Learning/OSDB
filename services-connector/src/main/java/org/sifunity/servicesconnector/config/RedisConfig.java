// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.servicesconnector.dto.ProxyRequestMetadata;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for the Services Connector.
 * 
 * <p>Configures RedisTemplate for caching provider registry entries.
 * Uses String serializer for keys and Jackson JSON serializer for values
 * to enable distributed caching across multiple service instances.</p>
 * 
 * <p><strong>Requirements:</strong> 13.5</p>
 * 
 * @see ProviderRegistrationDTO
 * @see ProxyRequestMetadata
 * @see org.sifunity.servicesconnector.cache.ProviderRegistryCache
 */
@Configuration
public class RedisConfig {
    
    /**
     * Configures RedisTemplate for ProviderRegistrationDTO caching.
     * 
     * <p>Configuration details:</p>
     * <ul>
     *   <li>Key serializer: StringRedisSerializer for human-readable keys</li>
     *   <li>Value serializer: Jackson2JsonRedisSerializer for JSON serialization</li>
     *   <li>Supports distributed caching for horizontal scaling</li>
     * </ul>
     * 
     * @param connectionFactory the Redis connection factory
     * @param objectMapper the Jackson ObjectMapper for JSON serialization
     * @return configured RedisTemplate for ProviderRegistrationDTO
     */
    @Bean
    public RedisTemplate<String, ProviderRegistrationDTO> providerRegistryRedisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        
        RedisTemplate<String, ProviderRegistrationDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys (human-readable cache keys)
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use Jackson JSON serializer for values
        Jackson2JsonRedisSerializer<ProviderRegistrationDTO> valueSerializer = 
            new Jackson2JsonRedisSerializer<>(objectMapper, ProviderRegistrationDTO.class);
        
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        
        return template;
    }
    
    /**
     * Configures RedisTemplate for ProxyRequestMetadata storage.
     *
     * <p>Used by the {@link org.sifunity.servicesconnector.service.DelayedRequestHandler}
     * to store DELAYED_to_IMMEDIATE proxy request metadata in a Redis work queue.</p>
     *
     * @param connectionFactory the Redis connection factory
     * @param objectMapper the Jackson ObjectMapper for JSON serialization
     * @return configured RedisTemplate for ProxyRequestMetadata
     */
    @Bean
    public RedisTemplate<String, ProxyRequestMetadata> proxyRequestMetadataRedisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        
        RedisTemplate<String, ProxyRequestMetadata> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        Jackson2JsonRedisSerializer<ProxyRequestMetadata> valueSerializer =
            new Jackson2JsonRedisSerializer<>(objectMapper, ProxyRequestMetadata.class);
        
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        
        return template;
    }
}
