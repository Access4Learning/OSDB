// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.JobState;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for StateTransitionValidator.
 */
@DisplayName("StateTransitionValidator")
class StateTransitionValidatorTest {
    
    private StateTransitionValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new StateTransitionValidator();
    }
    
    @Test
    @DisplayName("should allow transition from NOTSTARTED to INPROGRESS")
    void testValidTransition_NotStartedToInProgress() {
        boolean result = validator.isValidTransition(JobState.NOTSTARTED, JobState.INPROGRESS);
        assertThat(result).isTrue();
    }
    
    @Test
    @DisplayName("should allow transition from INPROGRESS to COMPLETED")
    void testValidTransition_InProgressToCompleted() {
        boolean result = validator.isValidTransition(JobState.INPROGRESS, JobState.COMPLETED);
        assertThat(result).isTrue();
    }
    
    @Test
    @DisplayName("should allow transition from INPROGRESS to FAILED")
    void testValidTransition_InProgressToFailed() {
        boolean result = validator.isValidTransition(JobState.INPROGRESS, JobState.FAILED);
        assertThat(result).isTrue();
    }
    
    @Test
    @DisplayName("should reject transition from NOTSTARTED to COMPLETED")
    void testInvalidTransition_NotStartedToCompleted() {
        boolean result = validator.isValidTransition(JobState.NOTSTARTED, JobState.COMPLETED);
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("should reject transition from NOTSTARTED to FAILED")
    void testInvalidTransition_NotStartedToFailed() {
        boolean result = validator.isValidTransition(JobState.NOTSTARTED, JobState.FAILED);
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("should reject transition from COMPLETED to any state")
    void testInvalidTransition_CompletedToAnyState() {
        assertThat(validator.isValidTransition(JobState.COMPLETED, JobState.NOTSTARTED)).isFalse();
        assertThat(validator.isValidTransition(JobState.COMPLETED, JobState.INPROGRESS)).isFalse();
        assertThat(validator.isValidTransition(JobState.COMPLETED, JobState.FAILED)).isFalse();
    }
    
    @Test
    @DisplayName("should reject transition from FAILED to any state")
    void testInvalidTransition_FailedToAnyState() {
        assertThat(validator.isValidTransition(JobState.FAILED, JobState.NOTSTARTED)).isFalse();
        assertThat(validator.isValidTransition(JobState.FAILED, JobState.INPROGRESS)).isFalse();
        assertThat(validator.isValidTransition(JobState.FAILED, JobState.COMPLETED)).isFalse();
    }
    
    @Test
    @DisplayName("should reject transition from INPROGRESS to NOTSTARTED")
    void testInvalidTransition_InProgressToNotStarted() {
        boolean result = validator.isValidTransition(JobState.INPROGRESS, JobState.NOTSTARTED);
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("should allow idempotent transitions (same state)")
    void testIdempotentTransitions() {
        assertThat(validator.isValidTransition(JobState.NOTSTARTED, JobState.NOTSTARTED)).isTrue();
        assertThat(validator.isValidTransition(JobState.INPROGRESS, JobState.INPROGRESS)).isTrue();
        assertThat(validator.isValidTransition(JobState.COMPLETED, JobState.COMPLETED)).isTrue();
        assertThat(validator.isValidTransition(JobState.FAILED, JobState.FAILED)).isTrue();
    }
    
    @Test
    @DisplayName("should throw exception when current state is null")
    void testIsValidTransition_NullCurrentState() {
        assertThatThrownBy(() -> validator.isValidTransition(null, JobState.INPROGRESS))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("States cannot be null");
    }
    
    @Test
    @DisplayName("should throw exception when new state is null")
    void testIsValidTransition_NullNewState() {
        assertThatThrownBy(() -> validator.isValidTransition(JobState.NOTSTARTED, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("States cannot be null");
    }
    
    @Test
    @DisplayName("should return correct valid next states for NOTSTARTED")
    void testGetValidNextStates_NotStarted() {
        Set<JobState> validStates = validator.getValidNextStates(JobState.NOTSTARTED);
        assertThat(validStates)
            .hasSize(1)
            .containsExactly(JobState.INPROGRESS);
    }
    
    @Test
    @DisplayName("should return correct valid next states for INPROGRESS")
    void testGetValidNextStates_InProgress() {
        Set<JobState> validStates = validator.getValidNextStates(JobState.INPROGRESS);
        assertThat(validStates)
            .hasSize(2)
            .containsExactlyInAnyOrder(JobState.COMPLETED, JobState.FAILED);
    }
    
    @Test
    @DisplayName("should return empty set for COMPLETED (terminal state)")
    void testGetValidNextStates_Completed() {
        Set<JobState> validStates = validator.getValidNextStates(JobState.COMPLETED);
        assertThat(validStates).isEmpty();
    }
    
    @Test
    @DisplayName("should return empty set for FAILED (terminal state)")
    void testGetValidNextStates_Failed() {
        Set<JobState> validStates = validator.getValidNextStates(JobState.FAILED);
        assertThat(validStates).isEmpty();
    }
    
    @Test
    @DisplayName("should throw exception when getting valid next states with null current state")
    void testGetValidNextStates_NullCurrentState() {
        assertThatThrownBy(() -> validator.getValidNextStates(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Current state cannot be null");
    }
}
