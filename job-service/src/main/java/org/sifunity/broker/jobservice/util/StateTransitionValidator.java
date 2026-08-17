// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.util;

import org.sifunity.broker.common.constant.JobState;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Validator for job and phase state transitions.
 * 
 * <p>This utility enforces the valid state transition rules for jobs and phases:
 * <ul>
 *   <li>NOTSTARTED → INPROGRESS</li>
 *   <li>INPROGRESS → COMPLETED</li>
 *   <li>INPROGRESS → FAILED</li>
 * </ul>
 * 
 * <p>Terminal states (COMPLETED and FAILED) cannot transition to any other state.</p>
 * 
 * <p>This validator is used by both JobService and JobPhaseService to ensure
 * state transitions follow the defined lifecycle rules.</p>
 */
@Component
public class StateTransitionValidator {
    
    /**
     * Map of valid state transitions.
     * Key: current state
     * Value: set of valid next states
     */
    private static final Map<JobState, Set<JobState>> VALID_TRANSITIONS = Map.of(
        JobState.NOTSTARTED, Set.of(JobState.INPROGRESS),
        JobState.INPROGRESS, Set.of(JobState.COMPLETED, JobState.FAILED),
        JobState.COMPLETED, Set.of(),
        JobState.FAILED, Set.of()
    );
    
    /**
     * Checks if a state transition is valid.
     * 
     * <p>A transition is valid if:
     * <ul>
     *   <li>The current state and new state are the same (idempotent), OR</li>
     *   <li>The new state is in the set of valid next states for the current state</li>
     * </ul>
     * 
     * @param currentState the current state of the job or phase
     * @param newState the desired new state
     * @return true if the transition is valid, false otherwise
     * @throws IllegalArgumentException if either state is null
     */
    public boolean isValidTransition(JobState currentState, JobState newState) {
        if (currentState == null || newState == null) {
            throw new IllegalArgumentException("States cannot be null");
        }
        
        // Idempotent transitions are always valid
        if (currentState == newState) {
            return true;
        }
        
        return VALID_TRANSITIONS.get(currentState).contains(newState);
    }
    
    /**
     * Gets the set of valid next states for a given current state.
     * 
     * <p>This method is useful for:
     * <ul>
     *   <li>Displaying available state transitions to users</li>
     *   <li>Generating test cases for valid transitions</li>
     *   <li>Validating business logic that depends on state transitions</li>
     * </ul>
     * 
     * @param currentState the current state of the job or phase
     * @return an immutable set of valid next states (may be empty for terminal states)
     * @throws IllegalArgumentException if currentState is null
     */
    public Set<JobState> getValidNextStates(JobState currentState) {
        if (currentState == null) {
            throw new IllegalArgumentException("Current state cannot be null");
        }
        
        return VALID_TRANSITIONS.get(currentState);
    }
}
