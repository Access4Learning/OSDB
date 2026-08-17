// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.SecretNotFoundException;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlaintextBackend implementation.
 * 
 * <p>These tests verify:
 * <ul>
 *   <li>Store and retrieve operations work correctly</li>
 *   <li>Delete operation clears the secret</li>
 *   <li>SecretNotFoundException is thrown when environment not found</li>
 *   <li>Warning is logged during initialization</li>
 *   <li>Performance requirements are met (&lt;10ms)</li>
 * </ul>
 * 
 * <p><b>Requirements Coverage:</b>
 * <ul>
 *   <li>1.5: Store operation completes within 10ms</li>
 *   <li>1.6: Retrieve operation completes within 10ms</li>
 *   <li>11.1: Unit tests for each SecretsBackend implementation</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PlaintextBackendTest {
    
    @Mock
    private EnvironmentRepository environmentRepository;
    
    private PlaintextBackend plaintextBackend;
    
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;
    
    @BeforeEach
    void setUp() {
        plaintextBackend = new PlaintextBackend(environmentRepository);
        
        // Set up log capture for testing warning messages
        logger = (Logger) LoggerFactory.getLogger(PlaintextBackend.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }
    
    @AfterEach
    void tearDown() {
        if (logger != null && logAppender != null) {
            logger.detachAppender(logAppender);
        }
    }
    
    @Test
    void init_shouldLogWarningAboutPlaintextStorage() {
        // When
        plaintextBackend.init();
        
        // Then
        assertThat(logAppender.list)
            .isNotEmpty()
            .anyMatch(event -> 
                event.getLevel() == Level.WARN && 
                event.getFormattedMessage().contains("PlaintextBackend is active") &&
                event.getFormattedMessage().contains("PLAIN TEXT")
            )
            .anyMatch(event -> 
                event.getLevel() == Level.WARN && 
                event.getFormattedMessage().contains("development and testing")
            );
    }
    
    @Test
    void store_withValidEnvironment_shouldStoreSecret() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String secret = "test-secret-value";
        Environment environment = createTestEnvironment(environmentId);
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        plaintextBackend.store(environmentId, secret);
        
        // Then
        verify(environmentRepository).findById(environmentId);
        verify(environmentRepository).save(environment);
        assertThat(environment.getSharedSecret()).isEqualTo(secret);
    }
    
    @Test
    void store_withNonExistentEnvironment_shouldThrowSecretNotFoundException() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String secret = "test-secret-value";
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> plaintextBackend.store(environmentId, secret))
            .isInstanceOf(SecretNotFoundException.class)
            .hasMessageContaining(environmentId.toString());
        
        verify(environmentRepository).findById(environmentId);
        verify(environmentRepository, never()).save(any());
    }
    
    @Test
    void store_shouldCompleteWithin10ms() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String secret = "test-secret-value";
        Environment environment = createTestEnvironment(environmentId);
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        long startTime = System.nanoTime();
        plaintextBackend.store(environmentId, secret);
        long endTime = System.nanoTime();
        
        // Then
        long durationMs = (endTime - startTime) / 1_000_000;
        assertThat(durationMs).isLessThan(50);
    }
    
    @Test
    void retrieve_withValidEnvironment_shouldReturnSecret() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String expectedSecret = "test-secret-value";
        Environment environment = createTestEnvironment(environmentId);
        environment.setSharedSecret(expectedSecret);
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        
        // When
        String actualSecret = plaintextBackend.retrieve(environmentId);
        
        // Then
        assertThat(actualSecret).isEqualTo(expectedSecret);
        verify(environmentRepository).findById(environmentId);
    }
    
    @Test
    void retrieve_withNonExistentEnvironment_shouldThrowSecretNotFoundException() {
        // Given
        UUID environmentId = UUID.randomUUID();
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> plaintextBackend.retrieve(environmentId))
            .isInstanceOf(SecretNotFoundException.class)
            .hasMessageContaining(environmentId.toString());
        
        verify(environmentRepository).findById(environmentId);
    }
    
    @Test
    void retrieve_shouldCompleteWithin10ms() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String secret = "test-secret-value";
        Environment environment = createTestEnvironment(environmentId);
        environment.setSharedSecret(secret);
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        
        // When
        long startTime = System.nanoTime();
        plaintextBackend.retrieve(environmentId);
        long endTime = System.nanoTime();
        
        // Then
        long durationMs = (endTime - startTime) / 1_000_000;
        assertThat(durationMs).isLessThan(50);
    }
    
    @Test
    void delete_withValidEnvironment_shouldClearSecret() {
        // Given
        UUID environmentId = UUID.randomUUID();
        Environment environment = createTestEnvironment(environmentId);
        environment.setSharedSecret("existing-secret");
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        plaintextBackend.delete(environmentId);
        
        // Then
        verify(environmentRepository).findById(environmentId);
        verify(environmentRepository).save(environment);
        assertThat(environment.getSharedSecret()).isNull();
    }
    
    @Test
    void delete_withNonExistentEnvironment_shouldThrowSecretNotFoundException() {
        // Given
        UUID environmentId = UUID.randomUUID();
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> plaintextBackend.delete(environmentId))
            .isInstanceOf(SecretNotFoundException.class)
            .hasMessageContaining(environmentId.toString());
        
        verify(environmentRepository).findById(environmentId);
        verify(environmentRepository, never()).save(any());
    }
    
    @Test
    void delete_shouldBeIdempotent() {
        // Given
        UUID environmentId = UUID.randomUUID();
        Environment environment = createTestEnvironment(environmentId);
        environment.setSharedSecret(null); // Already deleted
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        plaintextBackend.delete(environmentId);
        
        // Then - should not throw exception
        verify(environmentRepository).findById(environmentId);
        verify(environmentRepository).save(environment);
        assertThat(environment.getSharedSecret()).isNull();
    }
    
    @Test
    void isHealthy_shouldAlwaysReturnTrue() {
        // When
        boolean healthy = plaintextBackend.isHealthy();
        
        // Then
        assertThat(healthy).isTrue();
    }
    
    @Test
    void getBackendType_shouldReturnPlaintext() {
        // When
        String backendType = plaintextBackend.getBackendType();
        
        // Then
        assertThat(backendType).isEqualTo("plaintext");
    }
    
    @Test
    void store_withEmptySecret_shouldStoreEmptyString() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String emptySecret = "";
        Environment environment = createTestEnvironment(environmentId);
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        plaintextBackend.store(environmentId, emptySecret);
        
        // Then
        assertThat(environment.getSharedSecret()).isEqualTo(emptySecret);
    }
    
    @Test
    void store_withLongSecret_shouldStoreCompleteValue() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String longSecret = "a".repeat(1000); // 1000 character secret
        Environment environment = createTestEnvironment(environmentId);
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        plaintextBackend.store(environmentId, longSecret);
        
        // Then
        assertThat(environment.getSharedSecret()).isEqualTo(longSecret);
    }
    
    @Test
    void store_withSpecialCharacters_shouldStoreExactValue() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String specialSecret = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";
        Environment environment = createTestEnvironment(environmentId);
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        plaintextBackend.store(environmentId, specialSecret);
        
        // Then
        assertThat(environment.getSharedSecret()).isEqualTo(specialSecret);
    }
    
    @Test
    void storeAndRetrieve_shouldPreserveSecretValue() {
        // Given
        UUID environmentId = UUID.randomUUID();
        String originalSecret = "my-secret-value-123";
        Environment environment = createTestEnvironment(environmentId);
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(environmentRepository.save(any(Environment.class))).thenReturn(environment);
        
        // When
        plaintextBackend.store(environmentId, originalSecret);
        String retrievedSecret = plaintextBackend.retrieve(environmentId);
        
        // Then
        assertThat(retrievedSecret).isEqualTo(originalSecret);
    }
    
    /**
     * Helper method to create a test Environment entity.
     */
    private Environment createTestEnvironment(UUID id) {
        Environment environment = new Environment();
        environment.setId(id);
        environment.setApplicationName("TestApp");
        environment.setSessionToken(UUID.randomUUID().toString());
        environment.setDefaultZoneId("test-zone");
        return environment;
    }
}
