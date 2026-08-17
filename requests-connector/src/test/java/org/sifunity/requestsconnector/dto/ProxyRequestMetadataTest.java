// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.dto;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProxyRequestMetadata DTO.
 * 
 * Tests verify:
 * - Builder pattern construction
 * - Field accessors and mutators
 * - Lombok-generated methods (equals, hashCode, toString)
 * - Default values and null handling
 */
class ProxyRequestMetadataTest {
    
    @Test
    void testBuilderCreatesInstanceWithAllFields() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        UUID consumerQueueId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        String providerEndpoint = "http://provider.example.com/api/requests";
        Object requestEnvelope = new Object(); // Mock envelope
        Instant createdAt = Instant.now();
        int retryCount = 0;
        
        // Act
        ProxyRequestMetadata metadata = ProxyRequestMetadata.builder()
            .messageId(messageId)
            .consumerQueueId(consumerQueueId)
            .environmentId(environmentId)
            .environmentName(environmentName)
            .providerEndpoint(providerEndpoint)
            .requestEnvelope(requestEnvelope)
            .createdAt(createdAt)
            .retryCount(retryCount)
            .build();
        
        // Assert
        assertThat(metadata).isNotNull();
        assertThat(metadata.getMessageId()).isEqualTo(messageId);
        assertThat(metadata.getConsumerQueueId()).isEqualTo(consumerQueueId);
        assertThat(metadata.getEnvironmentId()).isEqualTo(environmentId);
        assertThat(metadata.getEnvironmentName()).isEqualTo(environmentName);
        assertThat(metadata.getProviderEndpoint()).isEqualTo(providerEndpoint);
        assertThat(metadata.getRequestEnvelope()).isEqualTo(requestEnvelope);
        assertThat(metadata.getCreatedAt()).isEqualTo(createdAt);
        assertThat(metadata.getRetryCount()).isEqualTo(retryCount);
    }
    
    @Test
    void testNoArgsConstructorCreatesInstance() {
        // Act
        ProxyRequestMetadata metadata = new ProxyRequestMetadata();
        
        // Assert
        assertThat(metadata).isNotNull();
        assertThat(metadata.getMessageId()).isNull();
        assertThat(metadata.getConsumerQueueId()).isNull();
        assertThat(metadata.getProviderEndpoint()).isNull();
        assertThat(metadata.getRequestEnvelope()).isNull();
        assertThat(metadata.getCreatedAt()).isNull();
        assertThat(metadata.getRetryCount()).isEqualTo(0); // int defaults to 0
    }
    
    @Test
    void testAllArgsConstructorCreatesInstance() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        UUID consumerQueueId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        String providerEndpoint = "http://provider.example.com/api/requests";
        Object requestEnvelope = new Object();
        Instant createdAt = Instant.now();
        int retryCount = 2;
        
        String domainId = "test-domain";
        String solutionId = "test-solution";
        
        // Act
        ProxyRequestMetadata metadata = new ProxyRequestMetadata(
            messageId,
            consumerQueueId,
            environmentId,
            environmentName,
            domainId,
            solutionId,
            "POST",
            null,
            "test/path",
            providerEndpoint,
            requestEnvelope,
            createdAt,
            retryCount
        );
        
        // Assert
        assertThat(metadata).isNotNull();
        assertThat(metadata.getMessageId()).isEqualTo(messageId);
        assertThat(metadata.getConsumerQueueId()).isEqualTo(consumerQueueId);
        assertThat(metadata.getEnvironmentId()).isEqualTo(environmentId);
        assertThat(metadata.getEnvironmentName()).isEqualTo(environmentName);
        assertThat(metadata.getDomainId()).isEqualTo(domainId);
        assertThat(metadata.getSolutionId()).isEqualTo(solutionId);
        assertThat(metadata.getProviderEndpoint()).isEqualTo(providerEndpoint);
        assertThat(metadata.getRequestEnvelope()).isEqualTo(requestEnvelope);
        assertThat(metadata.getCreatedAt()).isEqualTo(createdAt);
        assertThat(metadata.getRetryCount()).isEqualTo(retryCount);
    }
    
    @Test
    void testSettersModifyFields() {
        // Arrange
        ProxyRequestMetadata metadata = new ProxyRequestMetadata();
        UUID messageId = UUID.randomUUID();
        UUID consumerQueueId = UUID.randomUUID();
        String providerEndpoint = "http://provider.example.com/api/requests";
        Object requestEnvelope = new Object();
        Instant createdAt = Instant.now();
        int retryCount = 1;
        
        // Act
        metadata.setMessageId(messageId);
        metadata.setConsumerQueueId(consumerQueueId);
        metadata.setProviderEndpoint(providerEndpoint);
        metadata.setRequestEnvelope(requestEnvelope);
        metadata.setCreatedAt(createdAt);
        metadata.setRetryCount(retryCount);
        
        // Assert
        assertThat(metadata.getMessageId()).isEqualTo(messageId);
        assertThat(metadata.getConsumerQueueId()).isEqualTo(consumerQueueId);
        assertThat(metadata.getProviderEndpoint()).isEqualTo(providerEndpoint);
        assertThat(metadata.getRequestEnvelope()).isEqualTo(requestEnvelope);
        assertThat(metadata.getCreatedAt()).isEqualTo(createdAt);
        assertThat(metadata.getRetryCount()).isEqualTo(retryCount);
    }
    
    @Test
    void testRetryCountIncrement() {
        // Arrange
        ProxyRequestMetadata metadata = ProxyRequestMetadata.builder()
            .messageId(UUID.randomUUID())
            .consumerQueueId(UUID.randomUUID())
            .providerEndpoint("http://provider.example.com/api/requests")
            .requestEnvelope(new Object())
            .createdAt(Instant.now())
            .retryCount(0)
            .build();
        
        // Act - Simulate retry logic
        metadata.setRetryCount(metadata.getRetryCount() + 1);
        
        // Assert
        assertThat(metadata.getRetryCount()).isEqualTo(1);
        
        // Act - Another retry
        metadata.setRetryCount(metadata.getRetryCount() + 1);
        
        // Assert
        assertThat(metadata.getRetryCount()).isEqualTo(2);
    }
    
    @Test
    void testEqualsAndHashCode() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        UUID consumerQueueId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        String providerEndpoint = "http://provider.example.com/api/requests";
        Object requestEnvelope = new Object();
        Instant createdAt = Instant.now();
        int retryCount = 0;
        
        ProxyRequestMetadata metadata1 = ProxyRequestMetadata.builder()
            .messageId(messageId)
            .consumerQueueId(consumerQueueId)
            .environmentId(environmentId)
            .environmentName(environmentName)
            .providerEndpoint(providerEndpoint)
            .requestEnvelope(requestEnvelope)
            .createdAt(createdAt)
            .retryCount(retryCount)
            .build();
        
        ProxyRequestMetadata metadata2 = ProxyRequestMetadata.builder()
            .messageId(messageId)
            .consumerQueueId(consumerQueueId)
            .environmentId(environmentId)
            .environmentName(environmentName)
            .providerEndpoint(providerEndpoint)
            .requestEnvelope(requestEnvelope)
            .createdAt(createdAt)
            .retryCount(retryCount)
            .build();
        
        // Assert
        assertThat(metadata1).isEqualTo(metadata2);
        assertThat(metadata1.hashCode()).isEqualTo(metadata2.hashCode());
    }
    
    @Test
    void testNotEqualsWithDifferentMessageId() {
        // Arrange
        UUID consumerQueueId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        String providerEndpoint = "http://provider.example.com/api/requests";
        Object requestEnvelope = new Object();
        Instant createdAt = Instant.now();
        
        ProxyRequestMetadata metadata1 = ProxyRequestMetadata.builder()
            .messageId(UUID.randomUUID())
            .consumerQueueId(consumerQueueId)
            .environmentId(environmentId)
            .environmentName(environmentName)
            .providerEndpoint(providerEndpoint)
            .requestEnvelope(requestEnvelope)
            .createdAt(createdAt)
            .retryCount(0)
            .build();
        
        ProxyRequestMetadata metadata2 = ProxyRequestMetadata.builder()
            .messageId(UUID.randomUUID())
            .consumerQueueId(consumerQueueId)
            .environmentId(environmentId)
            .environmentName(environmentName)
            .providerEndpoint(providerEndpoint)
            .requestEnvelope(requestEnvelope)
            .createdAt(createdAt)
            .retryCount(0)
            .build();
        
        // Assert
        assertThat(metadata1).isNotEqualTo(metadata2);
    }
    
    @Test
    void testNotEqualsWithDifferentRetryCount() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        UUID consumerQueueId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        String providerEndpoint = "http://provider.example.com/api/requests";
        Object requestEnvelope = new Object();
        Instant createdAt = Instant.now();
        
        ProxyRequestMetadata metadata1 = ProxyRequestMetadata.builder()
            .messageId(messageId)
            .consumerQueueId(consumerQueueId)
            .environmentId(environmentId)
            .environmentName(environmentName)
            .providerEndpoint(providerEndpoint)
            .requestEnvelope(requestEnvelope)
            .createdAt(createdAt)
            .retryCount(0)
            .build();
        
        ProxyRequestMetadata metadata2 = ProxyRequestMetadata.builder()
            .messageId(messageId)
            .consumerQueueId(consumerQueueId)
            .environmentId(environmentId)
            .environmentName(environmentName)
            .providerEndpoint(providerEndpoint)
            .requestEnvelope(requestEnvelope)
            .createdAt(createdAt)
            .retryCount(1)
            .build();
        
        // Assert
        assertThat(metadata1).isNotEqualTo(metadata2);
    }
    
    @Test
    void testToStringContainsAllFields() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        UUID consumerQueueId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        String providerEndpoint = "http://provider.example.com/api/requests";
        Object requestEnvelope = new Object();
        Instant createdAt = Instant.now();
        int retryCount = 0;
        
        ProxyRequestMetadata metadata = ProxyRequestMetadata.builder()
            .messageId(messageId)
            .consumerQueueId(consumerQueueId)
            .environmentId(environmentId)
            .environmentName(environmentName)
            .providerEndpoint(providerEndpoint)
            .requestEnvelope(requestEnvelope)
            .createdAt(createdAt)
            .retryCount(retryCount)
            .build();
        
        // Act
        String toString = metadata.toString();
        
        // Assert
        assertThat(toString).contains("ProxyRequestMetadata");
        assertThat(toString).contains("messageId=" + messageId);
        assertThat(toString).contains("consumerQueueId=" + consumerQueueId);
        assertThat(toString).contains("environmentId=" + environmentId);
        assertThat(toString).contains("environmentName=" + environmentName);
        assertThat(toString).contains("domainId=");
        assertThat(toString).contains("solutionId=");
        assertThat(toString).contains("providerEndpoint=" + providerEndpoint);
        assertThat(toString).contains("retryCount=" + retryCount);
    }
    
    @Test
    void testBuilderWithPartialFields() {
        // Arrange & Act - Build with only some fields
        UUID messageId = UUID.randomUUID();
        String providerEndpoint = "http://provider.example.com/api/requests";
        
        ProxyRequestMetadata metadata = ProxyRequestMetadata.builder()
            .messageId(messageId)
            .providerEndpoint(providerEndpoint)
            .build();
        
        // Assert
        assertThat(metadata.getMessageId()).isEqualTo(messageId);
        assertThat(metadata.getProviderEndpoint()).isEqualTo(providerEndpoint);
        assertThat(metadata.getConsumerQueueId()).isNull();
        assertThat(metadata.getRequestEnvelope()).isNull();
        assertThat(metadata.getCreatedAt()).isNull();
        assertThat(metadata.getRetryCount()).isEqualTo(0);
    }
    
    @Test
    void testMaxRetryScenario() {
        // Arrange
        int maxRetries = 3;
        ProxyRequestMetadata metadata = ProxyRequestMetadata.builder()
            .messageId(UUID.randomUUID())
            .consumerQueueId(UUID.randomUUID())
            .providerEndpoint("http://provider.example.com/api/requests")
            .requestEnvelope(new Object())
            .createdAt(Instant.now())
            .retryCount(0)
            .build();
        
        // Act - Simulate retries until max
        for (int i = 0; i < maxRetries; i++) {
            metadata.setRetryCount(metadata.getRetryCount() + 1);
        }
        
        // Assert
        assertThat(metadata.getRetryCount()).isEqualTo(maxRetries);
        
        // Verify we've reached max retries
        assertThat(metadata.getRetryCount() >= maxRetries).isTrue();
    }
}
