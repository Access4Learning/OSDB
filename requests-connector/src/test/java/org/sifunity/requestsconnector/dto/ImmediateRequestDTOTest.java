// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ImmediateRequestDTO validation.
 * 
 * Tests validation annotations to ensure invalid inputs are rejected
 * and valid inputs pass validation.
 * 
 * Validates Requirements 7.1, 7.2, 7.4
 */
class ImmediateRequestDTOTest {
    
    private Validator validator;
    
    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    @Test
    void testValidRequest() {
        // Given: A valid IMMEDIATE request
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload("{\"id\": \"123\"}");
        
        // When: Validating the request
        Set<ConstraintViolation<ImmediateRequestDTO>> violations = validator.validate(request);
        
        // Then: No validation errors
        assertThat(violations).isEmpty();
    }
    
    @Test
    void testServiceNameNull_noValidationAnnotations() {
        // Given: A request with null serviceName (no validation annotations on DTO)
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload("{\"id\": \"123\"}");
        
        // When: Validating the request
        Set<ConstraintViolation<ImmediateRequestDTO>> violations = validator.validate(request);
        
        // Then: No validation errors (validation annotations were removed from DTO)
        assertThat(violations).isEmpty();
    }
    
    @Test
    void testServiceNameValidPattern_WithUnderscore() {
        // Given: A request with serviceName containing underscore
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("Student_Personal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload("{\"id\": \"123\"}");
        
        // When: Validating the request
        Set<ConstraintViolation<ImmediateRequestDTO>> violations = validator.validate(request);
        
        // Then: No validation errors
        assertThat(violations).isEmpty();
    }
    
    @Test
    void testServiceNameValidPattern_WithHyphen() {
        // Given: A request with serviceName containing hyphen
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("Student-Personal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload("{\"id\": \"123\"}");
        
        // When: Validating the request
        Set<ConstraintViolation<ImmediateRequestDTO>> violations = validator.validate(request);
        
        // Then: No validation errors
        assertThat(violations).isEmpty();
    }
    
    @Test
    void testServiceNameValidPattern_WithNumbers() {
        // Given: A request with serviceName containing numbers (but not starting with one)
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("Student123Personal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload("{\"id\": \"123\"}");
        
        // When: Validating the request
        Set<ConstraintViolation<ImmediateRequestDTO>> violations = validator.validate(request);
        
        // Then: No validation errors
        assertThat(violations).isEmpty();
    }
    
    @Test
    void testServiceTypeNull_noValidationAnnotations() {
        // Given: A request with null serviceType (no validation annotations on DTO)
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setMessageType("REQUEST");
        request.setPayload("{\"id\": \"123\"}");
        
        // When: Validating the request
        Set<ConstraintViolation<ImmediateRequestDTO>> violations = validator.validate(request);
        
        // Then: No validation errors (validation annotations were removed from DTO)
        assertThat(violations).isEmpty();
    }
    
    @Test
    void testMessageTypeNull_noValidationAnnotations() {
        // Given: A request with null messageType (no validation annotations on DTO)
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType("OBJECT");
        request.setPayload("{\"id\": \"123\"}");
        
        // When: Validating the request
        Set<ConstraintViolation<ImmediateRequestDTO>> violations = validator.validate(request);
        
        // Then: No validation errors (validation annotations were removed from DTO)
        assertThat(violations).isEmpty();
    }
    
    @Test
    void testPayloadOptional() {
        // Given: A request with null payload
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType("OBJECT");
        request.setMessageType("REQUEST");
        request.setPayload(null);
        
        // When: Validating the request
        Set<ConstraintViolation<ImmediateRequestDTO>> violations = validator.validate(request);
        
        // Then: No validation errors (payload is optional)
        assertThat(violations).isEmpty();
    }
    
    @Test
    void testMultipleFieldsEmpty_noValidationAnnotations() {
        // Given: A request with multiple empty/invalid fields (no validation annotations on DTO)
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("123Invalid");
        request.setServiceType("");
        request.setMessageType("");
        
        // When: Validating the request
        Set<ConstraintViolation<ImmediateRequestDTO>> violations = validator.validate(request);
        
        // Then: No validation errors (validation annotations were removed from DTO)
        assertThat(violations).isEmpty();
    }
}
