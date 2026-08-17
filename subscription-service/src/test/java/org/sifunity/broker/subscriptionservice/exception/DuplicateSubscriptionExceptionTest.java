// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DuplicateSubscriptionException.
 * <p>
 * Verifies that the exception is properly configured with the correct error code,
 * HTTP status, and message handling.
 * </p>
 */
class DuplicateSubscriptionExceptionTest {
    
    @Test
    void shouldCreateExceptionWithMessage() {
        // Given
        String message = "Subscription already exists for this queue and topic";
        
        // When
        DuplicateSubscriptionException exception = new DuplicateSubscriptionException(message);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("SUBSCRIPTION_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }
    
    @Test
    void shouldExtendSifBrokerException() {
        // Given
        DuplicateSubscriptionException exception = 
            new DuplicateSubscriptionException("Test message");
        
        // Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    @Test
    void shouldHaveCorrectErrorCode() {
        // Given
        DuplicateSubscriptionException exception = 
            new DuplicateSubscriptionException("Test message");
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("SUBSCRIPTION_001");
    }
    
    @Test
    void shouldHaveConflictHttpStatus() {
        // Given
        DuplicateSubscriptionException exception = 
            new DuplicateSubscriptionException("Test message");
        
        // Then
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getHttpStatus().value()).isEqualTo(409);
    }
    
    @Test
    void shouldPreserveMessageContent() {
        // Given
        String detailedMessage = "Subscription already exists: queueId=123, serviceName=StudentPersonal, zoneId=district-1, contextId=school-1, type=EVENT";
        
        // When
        DuplicateSubscriptionException exception = new DuplicateSubscriptionException(detailedMessage);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(detailedMessage);
    }
}
