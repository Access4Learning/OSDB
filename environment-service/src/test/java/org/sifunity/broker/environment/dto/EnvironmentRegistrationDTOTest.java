// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for environment registration DTOs.
 * 
 * <p>Tests validation annotations and basic functionality of
 * EnvironmentRegistrationRequest and EnvironmentRegistrationResponse records.</p>
 */
class EnvironmentRegistrationDTOTest {
    
    private Validator validator;
    
    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    @Test
    void environmentRegistrationRequest_validData_passesValidation() {
        // Given
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestApplication", EnvironmentType.BROKERED, new DefaultZoneDTO("default"), null, null));
        
        // When
        Set<ConstraintViolation<EnvironmentRegistrationRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
        assertThat(request.environment().consumerName()).isEqualTo("TestApplication");
        assertThat(request.environment().type()).isEqualTo(EnvironmentType.BROKERED);
        assertThat(request.environment().defaultZoneId()).isEqualTo("default");
    }
    
    @Test
    void environmentRegistrationRequest_nullApplicationName_failsValidation() {
        // Given
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(null, EnvironmentType.BROKERED, new DefaultZoneDTO("default"), null, null));
        
        // When
        Set<ConstraintViolation<EnvironmentRegistrationRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).hasSize(1);
        // D2: the body field is consumerName, so this is the message the constraint now carries.
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Consumer name is required");
    }
    
    @Test
    void environmentRegistrationRequest_blankApplicationName_failsValidation() {
        // Given
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(" ", EnvironmentType.BROKERED, new DefaultZoneDTO("default"), null, null));
        
        // When
        Set<ConstraintViolation<EnvironmentRegistrationRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).hasSize(1);
        // D2: the body field is consumerName, so this is the message the constraint now carries.
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Consumer name is required");
    }
    
    @Test
    void environmentRegistrationRequest_nullType_failsValidation() {
        // Given
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestApplication", null, new DefaultZoneDTO("default"), null, null));
        
        // When
        Set<ConstraintViolation<EnvironmentRegistrationRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Environment type is required");
    }
    
    @Test
    void environmentRegistrationRequest_nullDefaultZoneId_passesValidation() {
        // Given — defaultZoneId is now optional (no @NotBlank annotation)
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestApplication", EnvironmentType.BROKERED, null, null, null));
        
        // When
        Set<ConstraintViolation<EnvironmentRegistrationRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
    }
    
    @Test
    void environmentRegistrationRequest_blankDefaultZoneId_passesValidation() {
        // Given — defaultZoneId is now optional (no @NotBlank annotation)
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestApplication", EnvironmentType.BROKERED, new DefaultZoneDTO(""), null, null));
        
        // When
        Set<ConstraintViolation<EnvironmentRegistrationRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
    }
    
    @Test
    void environmentRegistrationRequest_nullDefaultContextId_passesValidation() {
        // Given
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestApplication", EnvironmentType.BROKERED, new DefaultZoneDTO("default"), null, null));
        
        // When
        Set<ConstraintViolation<EnvironmentRegistrationRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
    }
    
    @Test
    void environmentRegistrationResponse_validData_passesValidation() {
        // Given
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        EnvironmentRegistrationResponse response = new EnvironmentRegistrationResponse(
            id, "TestApplication", UUID.randomUUID().toString(), "base64EncodedSharedSecret==", createdAt, "default", EnvironmentType.BROKERED
        , null);
        
        // When
        Set<ConstraintViolation<EnvironmentRegistrationResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).isEmpty();
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.applicationName()).isEqualTo("TestApplication");
        assertThat(response.sessionToken()).isNotBlank();
        assertThat(response.sharedSecret()).isEqualTo("base64EncodedSharedSecret==");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.defaultZoneId()).isEqualTo("default");
        assertThat(response.type()).isEqualTo(EnvironmentType.BROKERED);
    }
    
    @Test
    void environmentRegistrationResponse_nullId_failsValidation() {
        // Given
        EnvironmentRegistrationResponse response = new EnvironmentRegistrationResponse(
            null, "TestApplication", UUID.randomUUID().toString(), "sharedSecret", Instant.now(), "default", EnvironmentType.BROKERED
        , null);
        
        // When
        Set<ConstraintViolation<EnvironmentRegistrationResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("id");
    }
    
    @Test
    void environmentRegistrationResponse_nullDefaultContextId_passesValidation() {
        // Given
        EnvironmentRegistrationResponse response = new EnvironmentRegistrationResponse(
            UUID.randomUUID(), "TestApplication", UUID.randomUUID().toString(), "sharedSecret", Instant.now(), "default", EnvironmentType.BROKERED
        , null);
        
        // When
        Set<ConstraintViolation<EnvironmentRegistrationResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).isEmpty();
    }
    
    @Test
    void environmentRegistrationRequest_recordEquality_worksCorrectly() {
        // Given
        EnvironmentRegistrationRequest request1 = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestApp", EnvironmentType.BROKERED, new DefaultZoneDTO("default"), null, null));
        EnvironmentRegistrationRequest request2 = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestApp", EnvironmentType.BROKERED, new DefaultZoneDTO("default"), null, null));
        EnvironmentRegistrationRequest request3 = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("DifferentApp", EnvironmentType.BROKERED, new DefaultZoneDTO("default"), null, null));
        
        // Then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1).isNotEqualTo(request3);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }
    
    @Test
    void environmentRegistrationResponse_recordEquality_worksCorrectly() {
        // Given
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        EnvironmentRegistrationResponse response1 = new EnvironmentRegistrationResponse(
            id, "TestApp", "token", "secret", createdAt, "default", EnvironmentType.BROKERED
        , null);
        EnvironmentRegistrationResponse response2 = new EnvironmentRegistrationResponse(
            id, "TestApp", "token", "secret", createdAt, "default", EnvironmentType.BROKERED
        , null);
        EnvironmentRegistrationResponse response3 = new EnvironmentRegistrationResponse(
            UUID.randomUUID(), "TestApp", "token", "secret", createdAt, "default", EnvironmentType.BROKERED
        , null);
        
        // Then
        assertThat(response1).isEqualTo(response2);
        assertThat(response1).isNotEqualTo(response3);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }
}