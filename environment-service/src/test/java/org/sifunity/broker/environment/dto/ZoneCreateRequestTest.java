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
 * Unit tests for ZoneCreateRequest DTO.
 * 
 * Tests validation annotations and business rules for zone creation requests.
 */
@DisplayName("ZoneCreateRequest Tests")
class ZoneCreateRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Should create valid ZoneCreateRequest with required fields")
    void shouldCreateValidZoneCreateRequestWithRequiredFields() {
        // Given
        ZoneCreateRequest request = new ZoneCreateRequest("test-solution", "test-zone", "Test zone description");
        
        // When
        Set<ConstraintViolation<ZoneCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
        assertThat(request.zoneId()).isEqualTo("test-zone");
        assertThat(request.description()).isEqualTo("Test zone description");
    }

    @Test
    @DisplayName("Should create valid ZoneCreateRequest with null description")
    void shouldCreateValidZoneCreateRequestWithNullDescription() {
        // Given
        ZoneCreateRequest request = new ZoneCreateRequest("test-solution", "test-zone", null);
        
        // When
        Set<ConstraintViolation<ZoneCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
        assertThat(request.zoneId()).isEqualTo("test-zone");
        assertThat(request.description()).isNull();
    }

    @ParameterizedTest
    @DisplayName("Should accept valid zone IDs")
    @ValueSource(strings = {
        "zone1",
        "Zone1",
        "test-zone",
        "test_zone",
        "zone-123",
        "zone_123",
        "a",
        "A",
        "1",
        "zone1-test_2"
    })
    void shouldAcceptValidZoneIds(String zoneId) {
        // Given
        ZoneCreateRequest request = new ZoneCreateRequest("test-solution", zoneId, "Test description");
        
        // When
        Set<ConstraintViolation<ZoneCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @DisplayName("Should reject invalid zone IDs")
    @ValueSource(strings = {
        "-zone",      // starts with hyphen
        "_zone",      // starts with underscore
        "zone-",      // ends with hyphen (allowed but not recommended)
        "zone_",      // ends with underscore (allowed but not recommended)
        "zone space", // contains space
        "zone@test",  // contains special character
        "zone.test",  // contains dot
        "zone/test",  // contains slash
        "",           // empty string
        " ",          // whitespace only
        "zone\ntest", // contains newline
        "zone\ttest"  // contains tab
    })
    void shouldRejectInvalidZoneIds(String zoneId) {
        // Given
        ZoneCreateRequest request = new ZoneCreateRequest("test-solution", zoneId, "Test description");
        
        // When
        Set<ConstraintViolation<ZoneCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
            v.getPropertyPath().toString().equals("zoneId") &&
            (v.getMessage().contains("Zone ID must start with alphanumeric character") ||
             v.getMessage().contains("Zone ID is required"))
        );
    }

    @Test
    @DisplayName("Should reject null zone ID")
    void shouldRejectNullZoneId() {
        // Given
        ZoneCreateRequest request = new ZoneCreateRequest("test-solution", null, "Test description");
        
        // When
        Set<ConstraintViolation<ZoneCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
            v.getPropertyPath().toString().equals("zoneId") &&
            v.getMessage().contains("Zone ID is required")
        );
    }

    @Test
    @DisplayName("Should reject blank zone ID")
    void shouldRejectBlankZoneId() {
        // Given
        ZoneCreateRequest request = new ZoneCreateRequest("test-solution", "   ", "Test description");
        
        // When
        Set<ConstraintViolation<ZoneCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
            v.getPropertyPath().toString().equals("zoneId") &&
            v.getMessage().contains("Zone ID is required")
        );
    }

    @Test
    @DisplayName("Should handle empty description")
    void shouldHandleEmptyDescription() {
        // Given
        ZoneCreateRequest request = new ZoneCreateRequest("test-solution", "test-zone", "");
        
        // When
        Set<ConstraintViolation<ZoneCreateRequest>> violations = validator.validate(request);
        
        // Then
        assertThat(violations).isEmpty();
        assertThat(request.description()).isEmpty();
    }
}