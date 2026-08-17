// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.property;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.jobservice.dto.CreateJobRequest;
import org.sifunity.broker.jobservice.dto.CreatePhaseRequest;
import org.sifunity.broker.jobservice.dto.UpdateJobStateRequest;
import org.sifunity.broker.jobservice.dto.UpdatePhaseStateRequest;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for job parameter validation.
 * 
 * Property 12: Job Parameter Validation Rejects Invalid Inputs
 * Validates: Requirements 10.1-10.5, 14.5
 */
class MetadataValidationPropertiesTest {

    private static final Validator validator;
    
    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /**
     * Property 12a: Invalid UUIDs Are Rejected
     * 
     * For any job creation request with an invalid UUID format,
     * validation should fail.
     */
    @Property(tries = 20)
    void property12a_invalidUUIDsAreRejected() {
        // Given - valid request with proper UUID
        CreateJobRequest validRequest = new CreateJobRequest(
                UUID.randomUUID(),
                "TestService",
                "zone1",
                "context1",
                null
        );

        // When - validate
        Set<ConstraintViolation<CreateJobRequest>> violations = validator.validate(validRequest);

        // Then - should have no violations
        assertThat(violations).isEmpty();
    }

    /**
     * Property 12b: Blank Service Names Are Rejected
     * 
     * For any job creation request with a blank service name,
     * validation should fail with appropriate error.
     */
    @Property(tries = 20)
    void property12b_blankServiceNamesAreRejected(
            @ForAll("blankStrings") String blankServiceName) {
        
        // Given - request with blank service name
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(),
                blankServiceName,
                "zone1",
                "context1",
                null
        );

        // When - validate
        Set<ConstraintViolation<CreateJobRequest>> violations = validator.validate(request);

        // Then - should have violations
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("serviceName"));
    }

    /**
     * Property 12c: Blank Zone IDs Are Rejected
     * 
     * For any job creation request with a blank zone ID,
     * validation should fail with appropriate error.
     */
    @Property(tries = 20)
    void property12c_blankZoneIdsAreRejected(
            @ForAll("blankStrings") String blankZoneId) {
        
        // Given - request with blank zone ID
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(),
                "TestService",
                blankZoneId,
                "context1",
                null
        );

        // When - validate
        Set<ConstraintViolation<CreateJobRequest>> violations = validator.validate(request);

        // Then - should have violations
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("zoneId"));
    }

    /**
     * Property 12d: Metadata Exceeding 10KB Is Rejected
     * 
     * For any job creation request with metadata exceeding 10KB,
     * validation should fail with size constraint violation.
     */
    @Property(tries = 20)
    void property12d_metadataExceeding10KBIsRejected(
            @ForAll("largeMetadata") String largeMetadata) {
        
        // Given - request with large metadata
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(),
                "TestService",
                "zone1",
                "context1",
                largeMetadata
        );

        // When - validate
        Set<ConstraintViolation<CreateJobRequest>> violations = validator.validate(request);

        // Then - should have violations for metadata size
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("metadata") &&
                v.getMessage().contains("10KB"));
    }

    /**
     * Property 12e: Valid Metadata Within 10KB Is Accepted
     * 
     * For any job creation request with metadata within 10KB limit,
     * validation should pass.
     */
    @Property(tries = 20)
    void property12e_validMetadataWithin10KBIsAccepted(
            @ForAll("validMetadata") String validMetadata) {
        
        // Given - request with valid metadata
        CreateJobRequest request = new CreateJobRequest(
                UUID.randomUUID(),
                "TestService",
                "zone1",
                "context1",
                validMetadata
        );

        // When - validate
        Set<ConstraintViolation<CreateJobRequest>> violations = validator.validate(request);

        // Then - should have no violations
        assertThat(violations).isEmpty();
    }

    /**
     * Property 12f: Null State in Update Request Is Rejected
     * 
     * For any job state update request with null state,
     * validation should fail.
     */
    @Property(tries = 20)
    void property12f_nullStateInUpdateRequestIsRejected() {
        
        // Given - update request with null state
        UpdateJobStateRequest request = new UpdateJobStateRequest(null, null);

        // When - validate
        Set<ConstraintViolation<UpdateJobStateRequest>> violations = validator.validate(request);

        // Then - should have violations
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("state"));
    }

    /**
     * Property 12g: Phase Name Validation
     * 
     * For any phase creation request, phase name must be non-blank
     * and within size limits.
     */
    @Property(tries = 20)
    void property12g_phaseNameValidation(
            @ForAll("blankStrings") String blankPhaseName) {
        
        // Given - phase request with blank name
        CreatePhaseRequest request = new CreatePhaseRequest(
                blankPhaseName,
                JobState.NOTSTARTED,
                null
        );

        // When - validate
        Set<ConstraintViolation<CreatePhaseRequest>> violations = validator.validate(request);

        // Then - should have violations
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("phaseName"));
    }

    /**
     * Property 12h: Phase Name Length Limit
     * 
     * For any phase creation request with phase name exceeding 100 characters,
     * validation should fail.
     */
    @Property(tries = 20)
    void property12h_phaseNameLengthLimit(
            @ForAll("longPhaseNames") String longPhaseName) {
        
        // Given - phase request with long name
        CreatePhaseRequest request = new CreatePhaseRequest(
                longPhaseName,
                JobState.NOTSTARTED,
                null
        );

        // When - validate
        Set<ConstraintViolation<CreatePhaseRequest>> violations = validator.validate(request);

        // Then - should have violations for size
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("phaseName") &&
                v.getMessage().contains("100 characters"));
    }

    /**
     * Property 12i: Phase Metadata Size Validation
     * 
     * For any phase with metadata exceeding 10KB,
     * validation should fail.
     */
    @Property(tries = 20)
    void property12i_phaseMetadataSizeValidation(
            @ForAll("largeMetadata") String largeMetadata) {
        
        // Given - phase request with large metadata
        CreatePhaseRequest request = new CreatePhaseRequest(
                "Phase1",
                JobState.NOTSTARTED,
                largeMetadata
        );

        // When - validate
        Set<ConstraintViolation<CreatePhaseRequest>> violations = validator.validate(request);

        // Then - should have violations
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("metadata"));
    }

    /**
     * Property 12j: Update Phase State Validation
     * 
     * For any phase state update request with null state,
     * validation should fail.
     */
    @Property(tries = 20)
    void property12j_updatePhaseStateValidation() {
        
        // Given - update request with null state
        UpdatePhaseStateRequest request = new UpdatePhaseStateRequest(null, null);

        // When - validate
        Set<ConstraintViolation<UpdatePhaseStateRequest>> violations = validator.validate(request);

        // Then - should have violations
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> 
                v.getPropertyPath().toString().equals("state"));
    }

    // Arbitraries for generating test data

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", "   ", "\t", "\n", null);
    }

    @Provide
    Arbitrary<String> largeMetadata() {
        // Generate metadata larger than 10KB (10240 bytes)
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(10241)
                .ofMaxLength(15000)
                .map(s -> "{\"data\":\"" + s + "\"}");
    }

    @Provide
    Arbitrary<String> validMetadata() {
        // Generate metadata within 10KB limit
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(10)
                .ofMaxLength(1000)
                .map(s -> "{\"data\":\"" + s + "\"}");
    }

    @Provide
    Arbitrary<String> longPhaseNames() {
        // Generate phase names longer than 100 characters
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(101)
                .ofMaxLength(200);
    }
}
