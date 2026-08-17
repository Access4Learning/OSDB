// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DuplicateDomainException.
 * <p>
 * Verifies that the exception is properly configured with the correct error code,
 * HTTP status, and message handling.
 * </p>
 */
class DuplicateDomainExceptionTest {
    
    @Test
    void shouldCreateExceptionWithDomainId() {
        // Given
        String domainId = "test-domain-1";
        
        // When
        DuplicateDomainException exception = new DuplicateDomainException(domainId);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo("Domain with ID 'test-domain-1' already exists");
        assertThat(exception.getErrorCode()).isEqualTo("DOMAIN_002");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }
    
    @Test
    void shouldExtendSifBrokerException() {
        // Given
        DuplicateDomainException exception = new DuplicateDomainException("test-domain");
        
        // Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    @Test
    void shouldHaveCorrectErrorCode() {
        // Given
        DuplicateDomainException exception = new DuplicateDomainException("test-domain");
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo("DOMAIN_002");
    }
    
    @Test
    void shouldHaveConflictHttpStatus() {
        // Given
        DuplicateDomainException exception = new DuplicateDomainException("test-domain");
        
        // Then
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getHttpStatus().value()).isEqualTo(409);
    }
    
    @Test
    void shouldIncludeDomainIdInMessage() {
        // Given
        String domainId = "sample-district";
        
        // When
        DuplicateDomainException exception = new DuplicateDomainException(domainId);
        
        // Then
        assertThat(exception.getMessage()).contains(domainId);
        assertThat(exception.getMessage()).contains("already exists");
    }
}
