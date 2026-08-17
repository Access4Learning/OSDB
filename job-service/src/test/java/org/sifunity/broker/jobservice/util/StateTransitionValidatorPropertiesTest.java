// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.util;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.JobState;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for StateTransitionValidator.
 * 
 * <p>These tests verify universal properties that should hold true across all
 * valid state transitions, ensuring the state machine behaves correctly for
 * all possible inputs.</p>
 */
@Label("StateTransitionValidator Properties")
class StateTransitionValidatorPropertiesTest {
    
    private final StateTransitionValidator validator = new StateTransitionValidator();
    
    /**
     * Property 2: Valid State Transitions Are Accepted
     * 
     * <p>For any job or phase in a given state, transitioning to a valid next state
     * (NOTSTARTED→INPROGRESS, INPROGRESS→COMPLETED, INPROGRESS→FAILED) should succeed.</p>
     * 
     * <p><strong>Validates: Requirements 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 5.1, 5.2, 5.3, 5.4</strong></p>
     */
    @Property(tries = 100)
    @Label("Property 2: Valid State Transitions Are Accepted")
    void validStateTransitionsAreAccepted(@ForAll("validStateTransitions") StateTransition transition) {
        // When: Checking if the transition is valid
        boolean isValid = validator.isValidTransition(transition.fromState(), transition.toState());
        
        // Then: The transition should be accepted
        assertThat(isValid)
            .as("Transition from %s to %s should be valid", 
                transition.fromState(), transition.toState())
            .isTrue();
    }
    
    /**
     * Property 3: Invalid State Transitions Are Rejected
     * 
     * <p>For any job or phase, attempting an invalid state transition
     * (NOTSTARTED→COMPLETED, NOTSTARTED→FAILED, COMPLETED→any, FAILED→any, 
     * INPROGRESS→NOTSTARTED) should fail.</p>
     * 
     * <p><strong>Validates: Requirements 2.5, 3.4, 3.5, 5.5</strong></p>
     */
    @Property(tries = 100)
    @Label("Property 3: Invalid State Transitions Are Rejected")
    void invalidStateTransitionsAreRejected(@ForAll("invalidStateTransitions") StateTransition transition) {
        // When: Checking if the transition is valid
        boolean isValid = validator.isValidTransition(transition.fromState(), transition.toState());
        
        // Then: The transition should be rejected
        assertThat(isValid)
            .as("Transition from %s to %s should be invalid", 
                transition.fromState(), transition.toState())
            .isFalse();
    }
    
    /**
     * Property 4: Terminal States Cannot Transition
     * 
     * <p>For any job or phase in COMPLETED or FAILED state, attempting any state
     * transition (except to itself) should be rejected.</p>
     * 
     * <p><strong>Validates: Requirements 2.6, 3.6, 3.7, 5.6</strong></p>
     */
    @Property(tries = 100)
    @Label("Property 4: Terminal States Cannot Transition")
    void terminalStatesCannotTransition(
            @ForAll("terminalStates") JobState terminalState,
            @ForAll("nonTerminalStates") JobState targetState) {
        
        // When: Attempting to transition from a terminal state to a different state
        boolean isValid = validator.isValidTransition(terminalState, targetState);
        
        // Then: The transition should be rejected
        assertThat(isValid)
            .as("Terminal state %s should not transition to %s", 
                terminalState, targetState)
            .isFalse();
    }
    
    /**
     * Property: Idempotent Transitions Are Always Valid
     * 
     * <p>For any state, transitioning to the same state should always be valid
     * (idempotent operation).</p>
     */
    @Property(tries = 100)
    @Label("Idempotent Transitions Are Always Valid")
    void idempotentTransitionsAreAlwaysValid(@ForAll("allStates") JobState state) {
        // When: Attempting to transition to the same state
        boolean isValid = validator.isValidTransition(state, state);
        
        // Then: The transition should be valid (idempotent)
        assertThat(isValid)
            .as("Idempotent transition for state %s should be valid", state)
            .isTrue();
    }
    
    /**
     * Property: Valid Next States Are Consistent With Transition Validation
     * 
     * <p>For any state, the set of valid next states returned by getValidNextStates()
     * should be consistent with isValidTransition() results.</p>
     */
    @Property(tries = 100)
    @Label("Valid Next States Are Consistent With Transition Validation")
    void validNextStatesAreConsistentWithTransitionValidation(@ForAll("allStates") JobState currentState) {
        // Given: The set of valid next states
        Set<JobState> validNextStates = validator.getValidNextStates(currentState);
        
        // When/Then: For each possible target state
        for (JobState targetState : JobState.values()) {
            boolean isInValidSet = validNextStates.contains(targetState);
            boolean isValidTransition = validator.isValidTransition(currentState, targetState);
            
            // If not idempotent, the results should match
            if (!currentState.equals(targetState)) {
                assertThat(isValidTransition)
                    .as("Transition validation for %s→%s should match valid next states set", 
                        currentState, targetState)
                    .isEqualTo(isInValidSet);
            }
        }
    }
    
    /**
     * Property: Terminal States Have No Valid Next States
     * 
     * <p>For any terminal state (COMPLETED or FAILED), the set of valid next states
     * should be empty.</p>
     */
    @Property(tries = 100)
    @Label("Terminal States Have No Valid Next States")
    void terminalStatesHaveNoValidNextStates(@ForAll("terminalStates") JobState terminalState) {
        // When: Getting valid next states for a terminal state
        Set<JobState> validNextStates = validator.getValidNextStates(terminalState);
        
        // Then: The set should be empty
        assertThat(validNextStates)
            .as("Terminal state %s should have no valid next states", terminalState)
            .isEmpty();
    }
    
    /**
     * Property: Non-Terminal States Have At Least One Valid Next State
     * 
     * <p>For any non-terminal state (NOTSTARTED or INPROGRESS), the set of valid
     * next states should not be empty.</p>
     */
    @Property(tries = 100)
    @Label("Non-Terminal States Have At Least One Valid Next State")
    void nonTerminalStatesHaveValidNextStates(@ForAll("nonTerminalStates") JobState nonTerminalState) {
        // When: Getting valid next states for a non-terminal state
        Set<JobState> validNextStates = validator.getValidNextStates(nonTerminalState);
        
        // Then: The set should not be empty
        assertThat(validNextStates)
            .as("Non-terminal state %s should have at least one valid next state", nonTerminalState)
            .isNotEmpty();
    }
    
    // ========== Arbitraries (Generators) ==========
    
    /**
     * Generates all valid state transitions.
     */
    @Provide
    Arbitrary<StateTransition> validStateTransitions() {
        return Arbitraries.of(
            // NOTSTARTED → INPROGRESS
            new StateTransition(JobState.NOTSTARTED, JobState.INPROGRESS),
            // INPROGRESS → COMPLETED
            new StateTransition(JobState.INPROGRESS, JobState.COMPLETED),
            // INPROGRESS → FAILED
            new StateTransition(JobState.INPROGRESS, JobState.FAILED)
        );
    }
    
    /**
     * Generates all invalid state transitions (excluding idempotent transitions).
     */
    @Provide
    Arbitrary<StateTransition> invalidStateTransitions() {
        return Arbitraries.of(
            // NOTSTARTED → COMPLETED (must go through INPROGRESS)
            new StateTransition(JobState.NOTSTARTED, JobState.COMPLETED),
            // NOTSTARTED → FAILED (must go through INPROGRESS)
            new StateTransition(JobState.NOTSTARTED, JobState.FAILED),
            // INPROGRESS → NOTSTARTED (cannot go backwards)
            new StateTransition(JobState.INPROGRESS, JobState.NOTSTARTED),
            // COMPLETED → NOTSTARTED (terminal state)
            new StateTransition(JobState.COMPLETED, JobState.NOTSTARTED),
            // COMPLETED → INPROGRESS (terminal state)
            new StateTransition(JobState.COMPLETED, JobState.INPROGRESS),
            // COMPLETED → FAILED (terminal state)
            new StateTransition(JobState.COMPLETED, JobState.FAILED),
            // FAILED → NOTSTARTED (terminal state)
            new StateTransition(JobState.FAILED, JobState.NOTSTARTED),
            // FAILED → INPROGRESS (terminal state)
            new StateTransition(JobState.FAILED, JobState.INPROGRESS),
            // FAILED → COMPLETED (terminal state)
            new StateTransition(JobState.FAILED, JobState.COMPLETED)
        );
    }
    
    /**
     * Generates terminal states (COMPLETED or FAILED).
     */
    @Provide
    Arbitrary<JobState> terminalStates() {
        return Arbitraries.of(JobState.COMPLETED, JobState.FAILED);
    }
    
    /**
     * Generates non-terminal states (NOTSTARTED or INPROGRESS).
     */
    @Provide
    Arbitrary<JobState> nonTerminalStates() {
        return Arbitraries.of(JobState.NOTSTARTED, JobState.INPROGRESS);
    }
    
    /**
     * Generates all possible job states.
     */
    @Provide
    Arbitrary<JobState> allStates() {
        return Arbitraries.of(JobState.values());
    }
    
    // ========== Helper Classes ==========
    
    /**
     * Represents a state transition from one state to another.
     */
    record StateTransition(JobState fromState, JobState toState) {
        @Override
        public String toString() {
            return fromState + " → " + toState;
        }
    }
}
