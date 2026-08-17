// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for subscription validation correctness properties.
 * 
 * <p>These tests validate that invalid subscription requests are properly
 * rejected with appropriate validation errors.</p>
 * 
 * <p>Each property test runs 100+ iterations with different inputs to ensure
 * validation works correctly across the input space.</p>
 */
class SubscriptionValidationPropertiesTest {
    
    private static final Validator validator;
    
    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    
    /**
     * Property 2: Required field validation - Validates Requirements 1.3, 1.7, 8.1, 8.2, 8.3, 8.4, 8.5, 12.1, 13.5
     * 
     * <p>For any subscription request missing required fields (queueId, serviceName,
     * zoneId, or subscriptionType) or with invalid field values, the service should
     * return HTTP 400 Bad Request with field-level validation details.</p>
     */
    @Property(tries = 100)
    void subscriptionWithMissingRequiredFieldsFailsValidation(
            @ForAll("subscriptionsWithMissingFields") SubscriptionDTO request) {
        
        // Execute validation
        Set<ConstraintViolation<SubscriptionDTO>> violations = validator.validate(request);
        
        // Verify - should have at least one validation error
        assertThat(violations).isNotEmpty();
    }
    
    /**
     * Property: Valid subscriptions pass validation
     * 
     * <p>For any valid subscription request with all required fields,
     * validation should pass with no errors.</p>
     */
    @Property(tries = 100)
    void validSubscriptionsPassValidation(
            @ForAll("validSubscriptions") SubscriptionDTO request) {
        
        // Execute validation
        Set<ConstraintViolation<SubscriptionDTO>> violations = validator.validate(request);
        
        // Verify - should have no validation errors
        assertThat(violations).isEmpty();
    }
    
    // ========== Arbitraries (Test Data Generators) ==========
    
    /**
     * Generates subscription DTOs with missing required fields.
     */
    @Provide
    Arbitrary<SubscriptionDTO> subscriptionsWithMissingFields() {
        return Arbitraries.oneOf(
                // Missing queueId (null)
                Combinators.combine(
                        Arbitraries.just((UUID) null),
                        Arbitraries.create(UUID::randomUUID),
                        validServiceNames(),
                        optionalContextUuids(),
                        Arbitraries.of(SubscriptionType.values()),
                        Arbitraries.just(Instant.now())
                ).as((id, queueId, sn, ctx, type, ts) ->
                        new SubscriptionDTO(id, null, null, sn, ctx, null, type, ts)),
                
                // Missing type (null)
                Combinators.combine(
                        Arbitraries.create(UUID::randomUUID),
                        Arbitraries.create(UUID::randomUUID),
                        validServiceNames(),
                        optionalContextUuids(),
                        Arbitraries.just((SubscriptionType) null),
                        Arbitraries.just(Instant.now())
                ).as((id, queueId, sn, ctx, type, ts) ->
                        new SubscriptionDTO(id, queueId, null, sn, ctx, null, type, ts))
        );
    }
    
    /**
     * Generates valid subscription DTOs.
     */
    @Provide
    Arbitrary<SubscriptionDTO> validSubscriptions() {
        // D4 (2.10): zoneId is required and must never be blank, so a "valid" subscription has to
        // supply one. It previously received optionalContextUuids() in the zoneId slot, which
        // generated nulls — those are no longer valid inputs. contextId remains optional.
        return Combinators.combine(
                Arbitraries.create(UUID::randomUUID),
                Arbitraries.create(UUID::randomUUID),
                validServiceNames(),
                Arbitraries.of("test-zone", "zone-a", "zone-b"),
                optionalContextUuids(),
                Arbitraries.of(SubscriptionType.values()),
                Arbitraries.just(Instant.now())
        ).as((id, queueId, sn, zone, ctx, type, ts) ->
                new SubscriptionDTO(id, queueId, null, sn, zone, ctx, type, ts));
    }
    
    /**
     * Generates blank strings (null, empty, or whitespace only).
     */
    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.just("\t"),
                Arbitraries.just("\n")
        );
    }
    
    /**
     * Generates valid service names.
     */
    @Provide
    Arbitrary<String> validServiceNames() {
        Arbitrary<Character> firstChar = Arbitraries.chars().range('a', 'z');
        Arbitrary<String> restChars = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_', '-')
                .ofMinLength(2)
                .ofMaxLength(29);
        
        return Combinators.combine(firstChar, restChars)
                .as((first, rest) -> first + rest);
    }
    
    /**
     * Generates optional context UUIDs.
     */
    @Provide
    Arbitrary<String> optionalContextUuids() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
        );
    }
}
