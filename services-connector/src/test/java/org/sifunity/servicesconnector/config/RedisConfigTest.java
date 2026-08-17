// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Unit tests for RedisConfig.
 * 
 * <p>Tests the Redis configuration for provider registry caching,
 * verifying that serializers are properly configured.</p>
 * 
 * <p><strong>Requirements:</strong> 13.5</p>
 */
class RedisConfigTest {
    
    private RedisConfig redisConfig;
    private RedisConnectionFactory connectionFactory;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        redisConfig = new RedisConfig();
        connectionFactory = mock(RedisConnectionFactory.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JavaTimeModule for Instant
    }
    
    @Test
    void providerRegistryRedisTemplate_shouldBeConfigured() {
        // When
        RedisTemplate<String, ProviderRegistrationDTO> template = 
            redisConfig.providerRegistryRedisTemplate(connectionFactory, objectMapper);
        
        // Then
        assertThat(template).isNotNull();
        assertThat(template.getConnectionFactory()).isEqualTo(connectionFactory);
    }
    
    @Test
    void providerRegistryRedisTemplate_shouldUseStringSerializerForKeys() {
        // When
        RedisTemplate<String, ProviderRegistrationDTO> template = 
            redisConfig.providerRegistryRedisTemplate(connectionFactory, objectMapper);
        
        // Then
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
    }
    
    @Test
    void providerRegistryRedisTemplate_shouldUseJacksonSerializerForValues() {
        // When
        RedisTemplate<String, ProviderRegistrationDTO> template = 
            redisConfig.providerRegistryRedisTemplate(connectionFactory, objectMapper);
        
        // Then
        assertThat(template.getValueSerializer()).isInstanceOf(Jackson2JsonRedisSerializer.class);
        assertThat(template.getHashValueSerializer()).isInstanceOf(Jackson2JsonRedisSerializer.class);
    }
    
    @Test
    void providerRegistryRedisTemplate_shouldSerializeAndDeserializeProviderRegistrationDTO() throws Exception {
        // Given
        RedisTemplate<String, ProviderRegistrationDTO> template = 
            redisConfig.providerRegistryRedisTemplate(connectionFactory, objectMapper);
        
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DataExport",
                "default",
                "production",
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY))))),
                Instant.now()
        );
        
        // When: Serialize
        @SuppressWarnings("unchecked")
        byte[] serialized = ((org.springframework.data.redis.serializer.RedisSerializer<ProviderRegistrationDTO>) 
            template.getValueSerializer()).serialize(provider);
        
        // Then: Should serialize successfully
        assertThat(serialized).isNotNull();
        assertThat(serialized.length).isGreaterThan(0);
        
        // When: Deserialize
        Object deserializedObj = template.getValueSerializer().deserialize(serialized);
        
        // Then: Should deserialize to equal object
        assertThat(deserializedObj).isInstanceOf(ProviderRegistrationDTO.class);
        ProviderRegistrationDTO deserialized = (ProviderRegistrationDTO) deserializedObj;
        assertThat(deserialized).isEqualTo(provider);
        assertThat(deserialized.id()).isEqualTo(provider.id());
        assertThat(deserialized.environmentId()).isEqualTo(provider.environmentId());
        assertThat(deserialized.serviceName()).isEqualTo(provider.serviceName());
        assertThat(deserialized.zoneId()).isEqualTo(provider.zoneId());
        assertThat(deserialized.contextId()).isEqualTo(provider.contextId());
        assertThat(ProtocolDTO.locationOf(deserialized.endPoint())).isEqualTo(ProtocolDTO.locationOf(provider.endPoint()));
        assertThat(deserialized.serviceType()).isEqualTo(provider.serviceType());
        assertThat(deserialized.providerName()).isEqualTo(provider.providerName());
        assertThat(deserialized.supportedRequestTypes().supportedRequestType()).isEqualTo(provider.supportedRequestTypes().supportedRequestType());
        assertThat(deserialized.registeredAt()).isEqualTo(provider.registeredAt());
    }
    
    @Test
    void providerRegistryRedisTemplate_shouldSerializeKeyAsString() throws Exception {
        // Given
        RedisTemplate<String, ProviderRegistrationDTO> template = 
            redisConfig.providerRegistryRedisTemplate(connectionFactory, objectMapper);
        
        String key = "provider:DataExport:default:production";
        
        // When
        @SuppressWarnings("unchecked")
        byte[] serialized = ((org.springframework.data.redis.serializer.RedisSerializer<String>) 
            template.getKeySerializer()).serialize(key);
        
        // Then
        assertThat(serialized).isNotNull();
        Object deserializedObj = template.getKeySerializer().deserialize(serialized);
        assertThat(deserializedObj).isInstanceOf(String.class);
        assertThat(deserializedObj).isEqualTo(key);
    }
    
    @Test
    void providerRegistryRedisTemplate_shouldHandleNullContextId() throws Exception {
        // Given
        RedisTemplate<String, ProviderRegistrationDTO> template = 
            redisConfig.providerRegistryRedisTemplate(connectionFactory, objectMapper);
        
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DataExport",
                "default",
                null,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(
                new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY))),
                new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.QUERY)))
            )),
                Instant.now()
        );
        
        // When: Serialize and deserialize
        @SuppressWarnings("unchecked")
        byte[] serialized = ((org.springframework.data.redis.serializer.RedisSerializer<ProviderRegistrationDTO>) 
            template.getValueSerializer()).serialize(provider);
        Object deserializedObj = template.getValueSerializer().deserialize(serialized);
        
        // Then: Should handle null contextId correctly
        assertThat(deserializedObj).isInstanceOf(ProviderRegistrationDTO.class);
        ProviderRegistrationDTO deserialized = (ProviderRegistrationDTO) deserializedObj;
        assertThat(deserialized).isEqualTo(provider);
        assertThat(deserialized.contextId()).isNull();
    }
    
    @Test
    void providerRegistryRedisTemplate_shouldHandleMultipleSupportedRequestTypes() throws Exception {
        // Given
        RedisTemplate<String, ProviderRegistrationDTO> template = 
            redisConfig.providerRegistryRedisTemplate(connectionFactory, objectMapper);
        
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DataExport",
                "default",
                "production",
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com"),
                ServiceType.FUNCTIONAL,
                SupportedRequestsDTO.of(List.of(
                new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))),
                new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY)))
            )),
                Instant.now()
        );
        
        // When: Serialize and deserialize
        @SuppressWarnings("unchecked")
        byte[] serialized = ((org.springframework.data.redis.serializer.RedisSerializer<ProviderRegistrationDTO>) 
            template.getValueSerializer()).serialize(provider);
        Object deserializedObj = template.getValueSerializer().deserialize(serialized);
        
        // Then: Should preserve all request types
        assertThat(deserializedObj).isInstanceOf(ProviderRegistrationDTO.class);
        ProviderRegistrationDTO deserialized = (ProviderRegistrationDTO) deserializedObj;
        assertThat(deserialized.supportedRequestTypes().supportedRequestType()).hasSize(2);
        assertThat(deserialized.supportedRequestTypes().supportedRequestType())
            .anyMatch(srt -> srt.requestType() == RequestType.IMMEDIATE)
            .anyMatch(srt -> srt.requestType() == RequestType.DELAYED);
        assertThat(deserialized.serviceType()).isEqualTo(ServiceType.FUNCTIONAL);
        assertThat(deserialized.providerName()).isEqualTo(null);
    }
}
