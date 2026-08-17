// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for custom exception classes.
 * <p>
 * Tests verify that each exception:
 * - Has the correct error code
 * - Has the correct HTTP status
 * - Properly stores the message
 * - Properly stores the cause (when provided)
 * </p>
 */
class CustomExceptionsTest {
    
    @Test
    void jobNotFoundException_ShouldHaveCorrectErrorCodeAndStatus() {
        // Given
        String message = "Job not found: 123e4567-e89b-12d3-a456-426614174000";
        
        // When
        JobNotFoundException exception = new JobNotFoundException(message);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("JOB_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(message);
    }
    
    @Test
    void jobNotFoundException_WithCause_ShouldStoreCause() {
        // Given
        String message = "Job not found";
        Throwable cause = new RuntimeException("Database error");
        
        // When
        JobNotFoundException exception = new JobNotFoundException(message, cause);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("JOB_001");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void phaseNotFoundException_ShouldHaveCorrectErrorCodeAndStatus() {
        // Given
        String message = "Phase not found: 123e4567-e89b-12d3-a456-426614174000";
        
        // When
        PhaseNotFoundException exception = new PhaseNotFoundException(message);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("JOB_002");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(message);
    }
    
    @Test
    void phaseNotFoundException_WithCause_ShouldStoreCause() {
        // Given
        String message = "Phase not found";
        Throwable cause = new RuntimeException("Database error");
        
        // When
        PhaseNotFoundException exception = new PhaseNotFoundException(message, cause);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("JOB_002");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void invalidStateTransitionException_ShouldHaveCorrectErrorCodeAndStatus() {
        // Given
        String message = "Invalid transition from NOTSTARTED to COMPLETED";
        
        // When
        InvalidStateTransitionException exception = new InvalidStateTransitionException(message);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("JOB_003");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo(message);
    }
    
    @Test
    void invalidStateTransitionException_WithCause_ShouldStoreCause() {
        // Given
        String message = "Invalid state transition";
        Throwable cause = new IllegalStateException("State machine error");
        
        // When
        InvalidStateTransitionException exception = new InvalidStateTransitionException(message, cause);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("JOB_003");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void jobInProgressException_ShouldHaveCorrectErrorCodeAndStatus() {
        // Given
        String message = "Cannot delete job in INPROGRESS state";
        
        // When
        JobInProgressException exception = new JobInProgressException(message);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("JOB_004");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getMessage()).isEqualTo(message);
    }
    
    @Test
    void jobInProgressException_WithCause_ShouldStoreCause() {
        // Given
        String message = "Cannot delete job";
        Throwable cause = new IllegalStateException("Job is active");
        
        // When
        JobInProgressException exception = new JobInProgressException(message, cause);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("JOB_004");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void duplicatePhaseException_ShouldHaveCorrectErrorCodeAndStatus() {
        // Given
        String message = "Phase already exists: initialization";
        
        // When
        DuplicatePhaseException exception = new DuplicatePhaseException(message);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("JOB_005");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getMessage()).isEqualTo(message);
    }
    
    @Test
    void duplicatePhaseException_WithCause_ShouldStoreCause() {
        // Given
        String message = "Duplicate phase name";
        Throwable cause = new RuntimeException("Unique constraint violation");
        
        // When
        DuplicatePhaseException exception = new DuplicatePhaseException(message, cause);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("JOB_005");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void allExceptions_ShouldExtendSifBrokerException() {
        // Verify inheritance hierarchy
        assertThat(new JobNotFoundException("test"))
            .isInstanceOf(org.sifunity.broker.common.exception.SifBrokerException.class);
        
        assertThat(new PhaseNotFoundException("test"))
            .isInstanceOf(org.sifunity.broker.common.exception.SifBrokerException.class);
        
        assertThat(new InvalidStateTransitionException("test"))
            .isInstanceOf(org.sifunity.broker.common.exception.SifBrokerException.class);
        
        assertThat(new JobInProgressException("test"))
            .isInstanceOf(org.sifunity.broker.common.exception.SifBrokerException.class);
        
        assertThat(new DuplicatePhaseException("test"))
            .isInstanceOf(org.sifunity.broker.common.exception.SifBrokerException.class);
    }
}
