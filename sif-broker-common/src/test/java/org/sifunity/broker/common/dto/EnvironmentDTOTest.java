// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EnvironmentDTO.
 * Tests immutability, equals/hashCode, toString, and validation annotations.
 */
class EnvironmentDTOTest {

    private ObjectMapper objectMapper;
    private Validator validator;

    @BeforeEach
    void setUp() {
        // Configure ObjectMapper for JSON serialization tests
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Set up validator for validation annotation tests
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void testImmutability() {
        // DTOs using Lombok @Value are immutable by design (compile-time enforcement)
        // All fields are final and there are no setters
        // This test verifies the DTO can be created and accessed
        UUID id = UUID.randomUUID();
        String appName = "TestApp";
        String token = "test-token";
        Instant created = Instant.now();
        String zoneId = "default";
        EnvironmentType type = EnvironmentType.BROKERED;

        EnvironmentDTO dto = new EnvironmentDTO(id, appName, token, created, null, zoneId, type, null);

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.applicationName()).isEqualTo(appName);
        assertThat(dto.sessionToken()).isEqualTo(token);
        assertThat(dto.createdAt()).isEqualTo(created);
        assertThat(dto.defaultZoneId()).isEqualTo(zoneId);
        assertThat(dto.type()).isEqualTo(type);
    }

    @Test
    void testEqualsAndHashCode_identicalValues() {
        UUID id = UUID.randomUUID();
        String appName = "TestApp";
        String token = "test-token";
        Instant created = Instant.parse("2024-01-01T00:00:00Z");
        String zoneId = "default";
        EnvironmentType type = EnvironmentType.BROKERED;

        EnvironmentDTO dto1 = new EnvironmentDTO(id, appName, token, created, null, zoneId, type, null);
        EnvironmentDTO dto2 = new EnvironmentDTO(id, appName, token, created, null, zoneId, type, null);

        // Test equals
        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto2).isEqualTo(dto1);
        
        // Test hashCode
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_differentValues() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Instant created = Instant.now();

        EnvironmentDTO dto1 = new EnvironmentDTO(id1, "App1", "token1", created, null, "zone1", EnvironmentType.BROKERED, null);
        EnvironmentDTO dto2 = new EnvironmentDTO(id2, "App2", "token2", created, null, "zone2", EnvironmentType.BROKERED, null);

        // Test equals
        assertThat(dto1).isNotEqualTo(dto2);
        
        // hashCode may differ (not required to be different, but typically is)
        assertThat(dto1.hashCode()).isNotEqualTo(dto2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_reflexive() {
        EnvironmentDTO dto = new EnvironmentDTO(
            UUID.randomUUID(), "TestApp", "token", Instant.now(), 
            null, "default", EnvironmentType.BROKERED, null
        );

        assertThat(dto).isEqualTo(dto);
    }

    @Test
    void testEqualsAndHashCode_symmetric() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2024-01-01T00:00:00Z");
        
        EnvironmentDTO dto1 = new EnvironmentDTO(id, "TestApp", "token", created, null, "default", EnvironmentType.BROKERED, null);
        EnvironmentDTO dto2 = new EnvironmentDTO(id, "TestApp", "token", created, null, "default", EnvironmentType.BROKERED, null);

        assertThat(dto1.equals(dto2)).isEqualTo(dto2.equals(dto1));
    }

    @Test
    void testEqualsAndHashCode_transitive() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2024-01-01T00:00:00Z");
        
        EnvironmentDTO dto1 = new EnvironmentDTO(id, "TestApp", "token", created, null, "default", EnvironmentType.BROKERED, null);
        EnvironmentDTO dto2 = new EnvironmentDTO(id, "TestApp", "token", created, null, "default", EnvironmentType.BROKERED, null);
        EnvironmentDTO dto3 = new EnvironmentDTO(id, "TestApp", "token", created, null, "default", EnvironmentType.BROKERED, null);

        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto2).isEqualTo(dto3);
        assertThat(dto1).isEqualTo(dto3);
    }

    @Test
    void testEqualsAndHashCode_nullComparison() {
        EnvironmentDTO dto = new EnvironmentDTO(
            UUID.randomUUID(), "TestApp", "token", Instant.now(), 
            null, "default", EnvironmentType.BROKERED, null
        );

        assertThat(dto).isNotEqualTo(null);
    }

    @Test
    void testToString() {
        UUID id = UUID.randomUUID();
        String appName = "TestApp";
        String token = "test-token";
        Instant created = Instant.now();
        String zoneId = "default";
        EnvironmentType type = EnvironmentType.BROKERED;

        EnvironmentDTO dto = new EnvironmentDTO(id, appName, token, created, null, zoneId, type, null);
        String toString = dto.toString();

        // Verify toString contains key field values
        assertThat(toString).contains("EnvironmentDTO");
        assertThat(toString).contains(id.toString());
        assertThat(toString).contains(appName);
        assertThat(toString).contains(token);
        assertThat(toString).contains(zoneId);
        assertThat(toString).contains(type.toString());
    }

    @Test
    void testValidation_validDTO() {
        EnvironmentDTO dto = new EnvironmentDTO(
            UUID.randomUUID(), 
            "TestApp", 
            "test-token", 
            Instant.now(), 
            null, 
            "default", 
            EnvironmentType.BROKERED,
            null
        );

        Set<ConstraintViolation<EnvironmentDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void testValidation_nullId() {
        EnvironmentDTO dto = new EnvironmentDTO(
            null,  // Invalid: id is @NotNull
            "TestApp", 
            "test-token", 
            Instant.now(), 
            null, 
            "default", 
            EnvironmentType.BROKERED,
            null
        );

        Set<ConstraintViolation<EnvironmentDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("id");
    }

    @Test
    void testValidation_blankApplicationName() {
        EnvironmentDTO dto = new EnvironmentDTO(
            UUID.randomUUID(), 
            "",  // Invalid: applicationName is @NotBlank
            "test-token", 
            Instant.now(), 
            null, 
            "default", 
            EnvironmentType.BROKERED,
            null
        );

        Set<ConstraintViolation<EnvironmentDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("applicationName");
    }

    @Test
    void testValidation_blankSessionToken() {
        EnvironmentDTO dto = new EnvironmentDTO(
            UUID.randomUUID(), 
            "TestApp", 
            "",  // Invalid: sessionToken is @NotBlank
            Instant.now(), 
            null, 
            "default", 
            EnvironmentType.BROKERED,
            null
        );

        Set<ConstraintViolation<EnvironmentDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("sessionToken");
    }

    @Test
    void testValidation_nullCreatedAt() {
        EnvironmentDTO dto = new EnvironmentDTO(
            UUID.randomUUID(), 
            "TestApp", 
            "test-token", 
            null,  // Invalid: createdAt is @NotNull
            null, 
            "default", 
            EnvironmentType.BROKERED,
            null
        );

        Set<ConstraintViolation<EnvironmentDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("createdAt");
    }

    @Test
    void testValidation_blankDefaultZoneId() {
        EnvironmentDTO dto = new EnvironmentDTO(
            UUID.randomUUID(), 
            "TestApp", 
            "test-token", 
            Instant.now(), 
            null, 
            "",  // Invalid: defaultZoneId is @NotBlank
            EnvironmentType.BROKERED,
            null
        );

        Set<ConstraintViolation<EnvironmentDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("defaultZoneId");
    }

    @Test
    void testValidation_nullType() {
        EnvironmentDTO dto = new EnvironmentDTO(
            UUID.randomUUID(), 
            "TestApp", 
            "test-token", 
            Instant.now(), 
            null, 
            "default", 
            null,  // Invalid: type is @NotNull
            null
        );

        Set<ConstraintViolation<EnvironmentDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("type");
    }

    @Test
    void testValidation_optionalFieldsCanBeNull() {
        EnvironmentDTO dto = new EnvironmentDTO(
            UUID.randomUUID(), 
            "TestApp", 
            "test-token", 
            Instant.now(), 
            null,  // expiresAt is optional
            "default", 
            EnvironmentType.BROKERED,
            null  // solutionId is optional
        );

        Set<ConstraintViolation<EnvironmentDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }
}
