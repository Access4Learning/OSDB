// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ResponseDTO.
 * Tests validation annotations and field handling.
 */
class ResponseDTOTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void shouldCreateResponseWithAllFields() {
        // Given
        String serviceName = "StudentPersonal";
        String serviceType = "OBJECT";
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", "12345");
        payload.put("name", "John Doe");

        // When
        ResponseDTO response = ResponseDTO.builder()
            .serviceName(serviceName)
            .serviceType(serviceType)
            .payload(payload)
            .build();

        // Then
        assertThat(response.getServiceName()).isEqualTo(serviceName);
        assertThat(response.getServiceType()).isEqualTo(serviceType);
        assertThat(response.getPayload()).isEqualTo(payload);
    }

    @Test
    void shouldValidateSuccessfullyWithValidData() {
        // Given
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("test", "data"))
            .build();

        // When
        Set<ConstraintViolation<ResponseDTO>> violations = validator.validate(response);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldFailValidationWhenServiceNameIsNull() {
        // Given
        ResponseDTO response = ResponseDTO.builder()
            .serviceName(null)
            .serviceType("OBJECT")
            .payload(Map.of("test", "data"))
            .build();

        // When
        Set<ConstraintViolation<ResponseDTO>> violations = validator.validate(response);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("Service name is required");
    }

    @Test
    void shouldFailValidationWhenServiceNameIsBlank() {
        // Given
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("  ")
            .serviceType("OBJECT")
            .payload(Map.of("test", "data"))
            .build();

        // When
        Set<ConstraintViolation<ResponseDTO>> violations = validator.validate(response);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("Service name is required");
    }

    @Test
    void shouldFailValidationWhenServiceTypeIsNull() {
        // Given
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType(null)
            .payload(Map.of("test", "data"))
            .build();

        // When
        Set<ConstraintViolation<ResponseDTO>> violations = validator.validate(response);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("Service type is required");
    }

    @Test
    void shouldFailValidationWhenServiceTypeIsBlank() {
        // Given
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("  ")
            .payload(Map.of("test", "data"))
            .build();

        // When
        Set<ConstraintViolation<ResponseDTO>> violations = validator.validate(response);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("Service type is required");
    }

    @Test
    void shouldFailValidationWhenPayloadIsNull() {
        // Given
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(null)
            .build();

        // When
        Set<ConstraintViolation<ResponseDTO>> violations = validator.validate(response);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("Payload is required");
    }

    @Test
    void shouldFailValidationWhenAllFieldsAreNull() {
        // Given
        ResponseDTO response = ResponseDTO.builder().build();

        // When
        Set<ConstraintViolation<ResponseDTO>> violations = validator.validate(response);

        // Then
        assertThat(violations).hasSize(3);
    }

    @Test
    void shouldSupportDifferentPayloadTypes() {
        // Given - Map payload
        ResponseDTO mapResponse = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("key", "value"))
            .build();

        // Given - String payload
        ResponseDTO stringResponse = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload("string payload")
            .build();

        // Given - Complex object payload
        Map<String, Object> complexPayload = new HashMap<>();
        complexPayload.put("student", Map.of("id", "123", "name", "John"));
        complexPayload.put("grades", java.util.List.of(85, 90, 95));
        ResponseDTO complexResponse = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(complexPayload)
            .build();

        // When
        Set<ConstraintViolation<ResponseDTO>> mapViolations = validator.validate(mapResponse);
        Set<ConstraintViolation<ResponseDTO>> stringViolations = validator.validate(stringResponse);
        Set<ConstraintViolation<ResponseDTO>> complexViolations = validator.validate(complexResponse);

        // Then
        assertThat(mapViolations).isEmpty();
        assertThat(stringViolations).isEmpty();
        assertThat(complexViolations).isEmpty();
    }

    @Test
    void shouldSupportBuilderPattern() {
        // When
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("test", "data"))
            .build();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getServiceName()).isEqualTo("StudentPersonal");
    }

    @Test
    void shouldSupportNoArgsConstructor() {
        // When
        ResponseDTO response = new ResponseDTO();
        response.setServiceName("StudentPersonal");
        response.setServiceType("OBJECT");
        response.setPayload(Map.of("test", "data"));

        // Then
        assertThat(response.getServiceName()).isEqualTo("StudentPersonal");
        assertThat(response.getServiceType()).isEqualTo("OBJECT");
        assertThat(response.getPayload()).isEqualTo(Map.of("test", "data"));
    }

    @Test
    void shouldSupportAllArgsConstructor() {
        // When
        ResponseDTO response = new ResponseDTO(
            "StudentPersonal",
            "OBJECT",
            Map.of("test", "data"),
            null
        );

        // Then
        assertThat(response.getServiceName()).isEqualTo("StudentPersonal");
        assertThat(response.getServiceType()).isEqualTo("OBJECT");
        assertThat(response.getPayload()).isEqualTo(Map.of("test", "data"));
    }
}
