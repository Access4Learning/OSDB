// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.builder;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.dto.EnvironmentDTO;
import org.sifunity.broker.common.dto.QueueDTO;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TestBuilders.
 * 
 * <p>These tests verify that the builder classes correctly construct DTOs
 * with both default and custom values, and that the fluent API works as expected.</p>
 */
class TestBuildersTest {
    
    @Test
    void environmentBuilder_shouldCreateWithDefaults() {
        // When
        EnvironmentDTO env = TestBuilders.environment().build();
        
        // Then
        assertThat(env).isNotNull();
        assertThat(env.id()).isNotNull();
        assertThat(env.applicationName()).isEqualTo("TestApp");
        assertThat(env.sessionToken()).isNotNull();
        assertThat(env.createdAt()).isNotNull();
        assertThat(env.expiresAt()).isNull();
        assertThat(env.defaultZoneId()).isEqualTo("default");
        assertThat(env.type()).isEqualTo(EnvironmentType.BROKERED);
    }
    
    @Test
    void environmentBuilder_shouldAllowCustomValues() {
        // Given
        UUID customId = UUID.randomUUID();
        String customName = "CustomApp";
        String customToken = "custom-token-123";
        Instant customCreated = Instant.parse("2024-01-01T00:00:00Z");
        Instant customExpires = Instant.parse("2024-12-31T23:59:59Z");
        String customZone = "zone-1";
        String customContext = "context-1";
        EnvironmentType customType = EnvironmentType.BROKERED;
        
        // When
        EnvironmentDTO env = TestBuilders.environment()
            .id(customId)
            .applicationName(customName)
            .sessionToken(customToken)
            .createdAt(customCreated)
            .expiresAt(customExpires)
            .defaultZoneId(customZone)
            .type(customType)
            .build();
        
        // Then
        assertThat(env.id()).isEqualTo(customId);
        assertThat(env.applicationName()).isEqualTo(customName);
        assertThat(env.sessionToken()).isEqualTo(customToken);
        assertThat(env.createdAt()).isEqualTo(customCreated);
        assertThat(env.expiresAt()).isEqualTo(customExpires);
        assertThat(env.defaultZoneId()).isEqualTo(customZone);
        assertThat(env.type()).isEqualTo(customType);
    }
    
    @Test
    void environmentBuilder_shouldSupportPartialCustomization() {
        // Given
        String customName = "PartialApp";
        EnvironmentType customType = EnvironmentType.BROKERED;
        
        // When
        EnvironmentDTO env = TestBuilders.environment()
            .applicationName(customName)
            .type(customType)
            .build();
        
        // Then
        assertThat(env.applicationName()).isEqualTo(customName);
        assertThat(env.type()).isEqualTo(customType);
        // Other fields should have defaults
        assertThat(env.id()).isNotNull();
        assertThat(env.sessionToken()).isNotNull();
        assertThat(env.defaultZoneId()).isEqualTo("default");
    }
    
    @Test
    void queueBuilder_shouldCreateWithDefaults() {
        // When
        QueueDTO queue = TestBuilders.queue().build();
        
        // Then
        assertThat(queue).isNotNull();
        assertThat(queue.id()).isNotNull();
        assertThat(queue.name()).isEqualTo("test-queue");
        assertThat(queue.ownerId()).isNotNull();
        assertThat(queue.created()).isNotNull();
        assertThat(queue.maxRetries()).isEqualTo(3);
        assertThat(queue.retryDelayMs()).isEqualTo(5000L);
        assertThat(queue.messageCount()).isEqualTo(0L);
    }
    
    @Test
    void queueBuilder_shouldAllowCustomValues() {
        // Given
        UUID customId = UUID.randomUUID();
        String customName = "custom-queue";
        UUID customOwnerId = UUID.randomUUID();
        Instant customCreated = Instant.parse("2024-01-01T00:00:00Z");
        Integer customRetries = 5;
        Long customDelay = 10000L;
        Long customCount = 42L;
        
        // When
        QueueDTO queue = TestBuilders.queue()
            .id(customId)
            .name(customName)
            .ownerId(customOwnerId)
            .createdAt(customCreated)
            .maxRetries(customRetries)
            .retryDelayMs(customDelay)
            .messageCount(customCount)
            .build();
        
        // Then
        assertThat(queue.id()).isEqualTo(customId);
        assertThat(queue.name()).isEqualTo(customName);
        assertThat(queue.ownerId()).isEqualTo(customOwnerId);
        assertThat(queue.created()).isEqualTo(customCreated);
        assertThat(queue.maxRetries()).isEqualTo(customRetries);
        assertThat(queue.retryDelayMs()).isEqualTo(customDelay);
        assertThat(queue.messageCount()).isEqualTo(customCount);
    }
    
    @Test
    void queueBuilder_shouldSupportPartialCustomization() {
        // Given
        String customName = "partial-queue";
        Integer customRetries = 10;
        
        // When
        QueueDTO queue = TestBuilders.queue()
            .name(customName)
            .maxRetries(customRetries)
            .build();
        
        // Then
        assertThat(queue.name()).isEqualTo(customName);
        assertThat(queue.maxRetries()).isEqualTo(customRetries);
        // Other fields should have defaults
        assertThat(queue.id()).isNotNull();
        assertThat(queue.ownerId()).isNotNull();
        assertThat(queue.retryDelayMs()).isEqualTo(5000L);
        assertThat(queue.messageCount()).isEqualTo(0L);
    }
    
    @Test
    void builders_shouldSupportFluentChaining() {
        // When - demonstrating fluent API
        EnvironmentDTO env = TestBuilders.environment()
            .applicationName("ChainedApp")
            .type(EnvironmentType.BROKERED)
            .defaultZoneId("zone-1")
            .build();
        
        QueueDTO queue = TestBuilders.queue()
            .name("chained-queue")
            .maxRetries(5)
            .retryDelayMs(3000L)
            .build();
        
        // Then
        assertThat(env).isNotNull();
        assertThat(queue).isNotNull();
    }
    
    @Test
    void builders_shouldCreateIndependentInstances() {
        // When - creating multiple instances
        EnvironmentDTO env1 = TestBuilders.environment().build();
        EnvironmentDTO env2 = TestBuilders.environment().build();
        
        QueueDTO queue1 = TestBuilders.queue().build();
        QueueDTO queue2 = TestBuilders.queue().build();
        
        // Then - instances should be different
        assertThat(env1.id()).isNotEqualTo(env2.id());
        assertThat(queue1.id()).isNotEqualTo(queue2.id());
    }
    
    @Test
    void staticFactoryMethods_shouldReturnBuilderInstances() {
        // When
        TestBuilders.EnvironmentDTOBuilder envBuilder = TestBuilders.environment();
        TestBuilders.QueueDTOBuilder queueBuilder = TestBuilders.queue();
        
        // Then
        assertThat(envBuilder).isNotNull();
        assertThat(queueBuilder).isNotNull();
        assertThat(envBuilder).isInstanceOf(TestBuilders.EnvironmentDTOBuilder.class);
        assertThat(queueBuilder).isInstanceOf(TestBuilders.QueueDTOBuilder.class);
    }
}
