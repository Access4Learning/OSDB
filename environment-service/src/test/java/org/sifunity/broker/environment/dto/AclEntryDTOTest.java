// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ACL entry DTOs.
 * 
 * <p>Tests validation annotations and basic functionality of
 * AclEntryCreateRequest and AclEntryUpdateRequest records.</p>
 */
class AclEntryDTOTest {
    
    private Validator validator;
    
    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    // AclEntryCreateRequest Tests
    
    @Test
    void aclEntryCreateRequest_validData_passesValidation() {
        // Given
        AclEntryCreateRequest request = new AclEntryCreateRequest(
            UUID.randomUUID(),
            "StudentPersonal",
            "DEFAULT", "DEFAULT",
            AccessType.PROVIDE,
            AccessLevel.APPROVED
        );
        
        // When
        Set<ConstraintViolation<AclEntryCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
        assertThat(request.environmentId()).isNotNull();
        assertThat(request.serviceName()).isEqualTo("StudentPersonal");
        assertThat(request.zoneId()).isEqualTo("DEFAULT");
        assertThat(request.contextId()).isEqualTo("DEFAULT");
        assertThat(request.accessType()).isEqualTo(AccessType.PROVIDE);
        assertThat(request.accessLevel()).isEqualTo(AccessLevel.APPROVED);
    }
    
    @Test
    void aclEntryCreateRequest_nullZoneId_failsValidation() {
        // Given - zoneId and contextId are @NotBlank so null should fail
        AclEntryCreateRequest request = new AclEntryCreateRequest(
            UUID.randomUUID(),
            "StudentPersonal",
            null, null,
            AccessType.QUERY,
            AccessLevel.REQUESTED
        );
        
        // When
        Set<ConstraintViolation<AclEntryCreateRequest>> violations = validator.validate(request);
        
        // Then - should have 2 violations: zoneId and contextId
        assertThat(violations).hasSize(2);
        assertThat(request.zoneId()).isNull();
        assertThat(request.contextId()).isNull();
    }
    
    @Test
    void aclEntryCreateRequest_nullEnvironmentId_failsValidation() {
        // Given
        AclEntryCreateRequest request = new AclEntryCreateRequest(
            null,
            "StudentPersonal",
            "DEFAULT", "DEFAULT",
            AccessType.PROVIDE,
            AccessLevel.APPROVED
        );
        
        // When
        Set<ConstraintViolation<AclEntryCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Environment ID is required");
    }
    
    @Test
    void aclEntryCreateRequest_nullServiceName_failsValidation() {
        // Given
        AclEntryCreateRequest request = new AclEntryCreateRequest(
            UUID.randomUUID(),
            null, "DEFAULT", "DEFAULT",
            AccessType.PROVIDE,
            AccessLevel.APPROVED
        );
        
        // When
        Set<ConstraintViolation<AclEntryCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Service name is required");
    }
    
    @Test
    void aclEntryCreateRequest_blankServiceName_failsValidation() {
        // Given
        AclEntryCreateRequest request = new AclEntryCreateRequest(
            UUID.randomUUID(),
            "   ",
            "DEFAULT", "DEFAULT",
            AccessType.PROVIDE,
            AccessLevel.APPROVED
        );
        
        // When
        Set<ConstraintViolation<AclEntryCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Service name is required");
    }
    
    @Test
    void aclEntryCreateRequest_nullAccessType_failsValidation() {
        // Given
        AclEntryCreateRequest request = new AclEntryCreateRequest(
            UUID.randomUUID(),
            "StudentPersonal",
            "DEFAULT", "DEFAULT",
            null,
            AccessLevel.APPROVED
        );
        
        // When
        Set<ConstraintViolation<AclEntryCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Access type is required");
    }
    
    @Test
    void aclEntryCreateRequest_nullAccessLevel_failsValidation() {
        // Given
        AclEntryCreateRequest request = new AclEntryCreateRequest(
            UUID.randomUUID(),
            "StudentPersonal",
            "DEFAULT", "DEFAULT",
            AccessType.PROVIDE,
            null
        );
        
        // When
        Set<ConstraintViolation<AclEntryCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Access level is required");
    }
    
    @Test
    void aclEntryCreateRequest_allAccessTypes_passValidation() {
        // Test all access types are valid
        for (AccessType accessType : AccessType.values()) {
            // Given
            AclEntryCreateRequest request = new AclEntryCreateRequest(
                UUID.randomUUID(),
                "StudentPersonal",
            "DEFAULT", "DEFAULT",
            accessType,
                AccessLevel.APPROVED
            );
            
            // When
            Set<ConstraintViolation<AclEntryCreateRequest>> violations = validator.validate(request);
            
            // Then
            assertThat(violations).isEmpty();
            assertThat(request.accessType()).isEqualTo(accessType);
        }
    }
    
    @Test
    void aclEntryCreateRequest_allAccessLevels_passValidation() {
        // Test all access levels are valid
        for (AccessLevel accessLevel : AccessLevel.values()) {
            // Given
            AclEntryCreateRequest request = new AclEntryCreateRequest(
                UUID.randomUUID(),
                "StudentPersonal",
            "DEFAULT", "DEFAULT",
            AccessType.PROVIDE,
                accessLevel
            );
            
            // When
            Set<ConstraintViolation<AclEntryCreateRequest>> violations = validator.validate(request);
            
            // Then
            assertThat(violations).isEmpty();
            assertThat(request.accessLevel()).isEqualTo(accessLevel);
        }
    }
    
    @Test
    void aclEntryCreateRequest_recordEquality_worksCorrectly() {
        // Given
        UUID environmentId = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();
        AclEntryCreateRequest request1 = new AclEntryCreateRequest(
            environmentId,
            "StudentPersonal",
            "DEFAULT", "DEFAULT",
            AccessType.PROVIDE,
            AccessLevel.APPROVED
        );
        AclEntryCreateRequest request2 = new AclEntryCreateRequest(
            environmentId,
            "StudentPersonal",
            "DEFAULT", "DEFAULT",
            AccessType.PROVIDE,
            AccessLevel.APPROVED
        );
        AclEntryCreateRequest request3 = new AclEntryCreateRequest(
            environmentId,
            "StudentPersonal",
            "DEFAULT", "DEFAULT",
            AccessType.QUERY,  // Different access type
            AccessLevel.APPROVED
        );
        
        // Then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1).isNotEqualTo(request3);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }
    
    // AclEntryUpdateRequest Tests
    
    @Test
    void aclEntryUpdateRequest_validData_passesValidation() {
        // Given
        AclEntryUpdateRequest request = new AclEntryUpdateRequest(AccessLevel.REJECTED);
        
        // When
        Set<ConstraintViolation<AclEntryUpdateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
        assertThat(request.accessLevel()).isEqualTo(AccessLevel.REJECTED);
    }
    
    @Test
    void aclEntryUpdateRequest_nullAccessLevel_failsValidation() {
        // Given
        AclEntryUpdateRequest request = new AclEntryUpdateRequest(null);
        
        // When
        Set<ConstraintViolation<AclEntryUpdateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("Access level is required");
    }
    
    @Test
    void aclEntryUpdateRequest_allAccessLevels_passValidation() {
        // Test all access levels are valid
        for (AccessLevel accessLevel : AccessLevel.values()) {
            // Given
            AclEntryUpdateRequest request = new AclEntryUpdateRequest(accessLevel);
            
            // When
            Set<ConstraintViolation<AclEntryUpdateRequest>> violations = validator.validate(request);
            
            // Then
            assertThat(violations).isEmpty();
            assertThat(request.accessLevel()).isEqualTo(accessLevel);
        }
    }
    
    @Test
    void aclEntryUpdateRequest_recordEquality_worksCorrectly() {
        // Given
        AclEntryUpdateRequest request1 = new AclEntryUpdateRequest(AccessLevel.REQUESTED);
        AclEntryUpdateRequest request2 = new AclEntryUpdateRequest(AccessLevel.REQUESTED);
        AclEntryUpdateRequest request3 = new AclEntryUpdateRequest(AccessLevel.APPROVED);
        
        // Then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1).isNotEqualTo(request3);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }
    
    @Test
    void aclEntryUpdateRequest_toString_containsAccessLevel() {
        // Given
        AclEntryUpdateRequest request = new AclEntryUpdateRequest(AccessLevel.APPROVED);
        
        // When
        String toString = request.toString();
        
        // Then
        assertThat(toString).contains("APPROVED");
        assertThat(toString).contains("AclEntryUpdateRequest");
    }
    
    @Test
    void aclEntryCreateRequest_toString_containsAllFields() {
        // Given
        UUID environmentId = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();
        AclEntryCreateRequest request = new AclEntryCreateRequest(
            environmentId,
            "StudentPersonal",
            "DEFAULT", "DEFAULT",
            AccessType.PROVIDE,
            AccessLevel.APPROVED
        );
        
        // When
        String toString = request.toString();
        
        // Then
        assertThat(toString).contains("StudentPersonal");
        assertThat(toString).contains("DEFAULT");
        assertThat(toString).contains("PROVIDE");
        assertThat(toString).contains("APPROVED");
        assertThat(toString).contains("AclEntryCreateRequest");
    }

    @Test
    void aclEntryCreateRequest_toString_containsNullContextUuid() {
        // Given
        UUID environmentId = UUID.randomUUID();
        AclEntryCreateRequest request = new AclEntryCreateRequest(
            environmentId,
            "StudentPersonal",
            "DEFAULT", "DEFAULT",
            AccessType.PROVIDE,
            AccessLevel.APPROVED
        );
        
        // When
        String toString = request.toString();
        
        // Then
        assertThat(toString).contains("AclEntryCreateRequest");
        assertThat(toString).contains("zoneId=DEFAULT");
    }
}