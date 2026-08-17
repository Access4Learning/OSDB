// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ContextCreateRequest DTO.
 * 
 * Tests validation annotations and business rules for context creation requests.
 */
@DisplayName("ContextCreateRequest Tests")
class ContextCreateRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Should create valid ContextCreateRequest with required fields")
    void shouldCreateValidContextCreateRequestWithRequiredFields() {
        // Given
        ContextCreateRequest request = new ContextCreateRequest("test-context", "Test context description");
        
        // When
        Set<ConstraintViolation<ContextCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
        assertThat(request.contextId()).isEqualTo("test-context");
        assertThat(request.description()).isEqualTo("Test context description");
    }

    @Test
    @DisplayName("Should create valid ContextCreateRequest with null description")
    void shouldCreateValidContextCreateRequestWithNullDescription() {
        // Given
        ContextCreateRequest request = new ContextCreateRequest("test-context", null);
        
        // When
        Set<ConstraintViolation<ContextCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
        assertThat(request.contextId()).isEqualTo("test-context");
        assertThat(request.description()).isNull();
    }

    @ParameterizedTest
    @DisplayName("Should accept valid context IDs")
    @ValueSource(strings = {
        "context1",
        "Context1",
        "test-context",
        "test_context",
        "context-123",
        "context_123",
        "a",
        "A",
        "1",
        "context1-test_2"
    })
    void shouldAcceptValidContextIds(String contextId) {
        // Given
        ContextCreateRequest request = new ContextCreateRequest(contextId, "Test description");
        
        // When
        Set<ConstraintViolation<ContextCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @DisplayName("Should reject invalid context IDs")
    @ValueSource(strings = {
        "-context",      // starts with hyphen
        "_context",      // starts with underscore
        "context-",      // ends with hyphen (allowed but not recommended)
        "context_",      // ends with underscore (allowed but not recommended)
        "context space", // contains space
        "context@test",  // contains special character
        "context.test",  // contains dot
        "context/test",  // contains slash
        "",              // empty string
        " ",             // whitespace only
        "context\ntest", // contains newline
        "context\ttest"  // contains tab
    })
    void shouldRejectInvalidContextIds(String contextId) {
        // Given
        ContextCreateRequest request = new ContextCreateRequest(contextId, "Test description");
        
        // When
        Set<ConstraintViolation<ContextCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
            v.getPropertyPath().toString().equals("contextId") &&
            (v.getMessage().contains("Context ID must start with alphanumeric character") ||
             v.getMessage().contains("Context ID is required"))
        );
    }

    @Test
    @DisplayName("Should reject null context ID")
    void shouldRejectNullContextId() {
        // Given
        ContextCreateRequest request = new ContextCreateRequest(null, "Test description");
        
        // When
        Set<ConstraintViolation<ContextCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
            v.getPropertyPath().toString().equals("contextId") &&
            v.getMessage().contains("Context ID is required")
        );
    }

    @Test
    @DisplayName("Should reject blank context ID")
    void shouldRejectBlankContextId() {
        // Given
        ContextCreateRequest request = new ContextCreateRequest("   ", "Test description");
        
        // When
        Set<ConstraintViolation<ContextCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
            v.getPropertyPath().toString().equals("contextId") &&
            v.getMessage().contains("Context ID is required")
        );
    }

    @Test
    @DisplayName("Should handle empty description")
    void shouldHandleEmptyDescription() {
        // Given
        ContextCreateRequest request = new ContextCreateRequest("test-context", "");
        
        // When
        Set<ConstraintViolation<ContextCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
        assertThat(request.description()).isEmpty();
    }
}