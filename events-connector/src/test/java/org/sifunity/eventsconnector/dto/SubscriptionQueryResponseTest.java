// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SubscriptionQueryResponse DTO.
 * Validates construction, validation annotations, and helper methods.
 */
@DisplayName("SubscriptionQueryResponse Tests")
class SubscriptionQueryResponseTest {
    
    private Validator validator;
    
    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    @Test
    @DisplayName("Should create response with valid subscriptions list")
    void shouldCreateResponseWithValidSubscriptionsList() {
        // Given
        List<SubscriptionDTO> subscriptions = List.of(
            new SubscriptionDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT,
                Instant.now()
            )
        );
        
        // When
        SubscriptionQueryResponse response = SubscriptionQueryResponse.of(subscriptions);
        
        // Then
        assertThat(response.asList()).isEqualTo(subscriptions);
        assertThat(response.isEmpty()).isFalse();
        assertThat(response.size()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("Should create empty response when subscriptions list is empty")
    void shouldCreateEmptyResponseWhenSubscriptionsListIsEmpty() {
        // Given
        List<SubscriptionDTO> subscriptions = List.of();
        
        // When
        SubscriptionQueryResponse response = SubscriptionQueryResponse.of(subscriptions);
        
        // Then
        assertThat(response.asList()).isEmpty();
        assertThat(response.isEmpty()).isTrue();
        assertThat(response.size()).isZero();
    }
    
    @Test
    @DisplayName("Should default to empty list when subscriptions is null")
    void shouldDefaultToEmptyListWhenSubscriptionsIsNull() {
        // When
        SubscriptionQueryResponse response = SubscriptionQueryResponse.of(null);
        
        // Then
        assertThat(response.asList()).isNotNull();
        assertThat(response.asList()).isEmpty();
        assertThat(response.isEmpty()).isTrue();
        assertThat(response.size()).isZero();
    }
    
    @Test
    @DisplayName("Should create empty response using factory method")
    void shouldCreateEmptyResponseUsingFactoryMethod() {
        // When
        SubscriptionQueryResponse response = SubscriptionQueryResponse.empty();
        
        // Then
        assertThat(response.asList()).isEmpty();
        assertThat(response.isEmpty()).isTrue();
        assertThat(response.size()).isZero();
    }
    
    @Test
    @DisplayName("Should pass validation with valid subscriptions")
    void shouldPassValidationWithValidSubscriptions() {
        // Given
        List<SubscriptionDTO> subscriptions = List.of(
            new SubscriptionDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "StudentPersonal", "test-zone", (String) null, SubscriptionType.EVENT,
                Instant.now()
            )
        );
        SubscriptionQueryResponse response = SubscriptionQueryResponse.of(subscriptions);
        
        // When
        Set<ConstraintViolation<SubscriptionQueryResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).isEmpty();
    }
    
    @Test
    @DisplayName("Should pass validation with empty subscriptions list")
    void shouldPassValidationWithEmptySubscriptionsList() {
        // Given
        SubscriptionQueryResponse response = SubscriptionQueryResponse.empty();
        
        // When
        Set<ConstraintViolation<SubscriptionQueryResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).isEmpty();
    }
    
    @Test
    @DisplayName("Should handle multiple subscriptions correctly")
    void shouldHandleMultipleSubscriptionsCorrectly() {
        // Given
        List<SubscriptionDTO> subscriptions = List.of(
            new SubscriptionDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT,
                Instant.now()
            ),
            new SubscriptionDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT,
                Instant.now()
            ),
            new SubscriptionDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT,
                Instant.now()
            )
        );
        
        // When
        SubscriptionQueryResponse response = SubscriptionQueryResponse.of(subscriptions);
        
        // Then
        assertThat(response.asList()).hasSize(3);
        assertThat(response.isEmpty()).isFalse();
        assertThat(response.size()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("Should fail validation when subscriptions contain invalid DTO")
    void shouldFailValidationWhenSubscriptionsContainInvalidDTO() {
        // Given - Create a list with an invalid SubscriptionDTO (missing required fields)
        List<SubscriptionDTO> subscriptions = new ArrayList<>();
        subscriptions.add(new SubscriptionDTO(
            UUID.randomUUID(),
            null, // Invalid: queueId is null (@NotNull)
            null,
            "StudentPersonal",
            (String) null, (String) null,
            null, // Invalid: messageType is null (@NotNull)
            Instant.now()
        ));
        SubscriptionQueryResponse response = SubscriptionQueryResponse.of(subscriptions);
        
        // When
        Set<ConstraintViolation<SubscriptionQueryResponse>> violations = validator.validate(response);
        
        // Then
        assertThat(violations).isNotEmpty();
    }
    
    @Test
    @DisplayName("Should correctly report isEmpty for various scenarios")
    void shouldCorrectlyReportIsEmptyForVariousScenarios() {
        // Empty list
        assertThat(SubscriptionQueryResponse.of(List.of()).isEmpty()).isTrue();
        
        // Null (converted to empty)
        assertThat(SubscriptionQueryResponse.of(null).isEmpty()).isTrue();
        
        // Non-empty list
        List<SubscriptionDTO> subscriptions = List.of(
            new SubscriptionDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT,
                Instant.now()
            )
        );
        assertThat(SubscriptionQueryResponse.of(subscriptions).isEmpty()).isFalse();
    }
    
    @Test
    @DisplayName("Should correctly report size for various scenarios")
    void shouldCorrectlyReportSizeForVariousScenarios() {
        // Empty list
        assertThat(SubscriptionQueryResponse.of(List.of()).size()).isZero();
        
        // Null (converted to empty)
        assertThat(SubscriptionQueryResponse.of(null).size()).isZero();
        
        // Single subscription
        List<SubscriptionDTO> singleSubscription = List.of(
            new SubscriptionDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT,
                Instant.now()
            )
        );
        assertThat(SubscriptionQueryResponse.of(singleSubscription).size()).isEqualTo(1);
        
        // Multiple subscriptions
        List<SubscriptionDTO> multipleSubscriptions = List.of(
            new SubscriptionDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT,
                Instant.now()
            ),
            new SubscriptionDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null, "SchoolInfo", (String) null, (String) null, SubscriptionType.EVENT,
                Instant.now()
            )
        );
        assertThat(SubscriptionQueryResponse.of(multipleSubscriptions).size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should default to empty wrapper when constructor receives null subscriptions")
    void shouldDefaultToEmptyWrapperWhenConstructorReceivesNull() {
        // When - directly construct with null wrapper
        SubscriptionQueryResponse response = new SubscriptionQueryResponse(null);

        // Then
        assertThat(response.subscriptions()).isNotNull();
        assertThat(response.asList()).isEmpty();
        assertThat(response.isEmpty()).isTrue();
        assertThat(response.size()).isZero();
    }

    @Test
    @DisplayName("Should default inner wrapper subscription list to empty when null")
    void shouldDefaultInnerWrapperSubscriptionListToEmptyWhenNull() {
        // When - construct wrapper with null subscription list
        SubscriptionQueryResponse.SubscriptionsWrapper wrapper =
            new SubscriptionQueryResponse.SubscriptionsWrapper(null);

        // Then
        assertThat(wrapper.subscription()).isNotNull();
        assertThat(wrapper.subscription()).isEmpty();
    }

    @Test
    @DisplayName("Should preserve subscription list in inner wrapper when provided")
    void shouldPreserveSubscriptionListInInnerWrapper() {
        // Given
        List<SubscriptionDTO> subscriptions = List.of(
            new SubscriptionDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "StudentPersonal", (String) null, (String) null, SubscriptionType.EVENT,
                Instant.now()
            )
        );

        // When
        SubscriptionQueryResponse.SubscriptionsWrapper wrapper =
            new SubscriptionQueryResponse.SubscriptionsWrapper(subscriptions);

        // Then
        assertThat(wrapper.subscription()).hasSize(1);
        assertThat(wrapper.subscription()).isEqualTo(subscriptions);
    }
}


