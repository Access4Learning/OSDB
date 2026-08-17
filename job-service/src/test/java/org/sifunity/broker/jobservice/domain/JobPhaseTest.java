// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.domain;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.JobState;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the JobPhase entity.
 */
class JobPhaseTest {
    
    @Test
    void testJobPhaseCreation_WithRequiredFields_CreatesJobPhase() {
        // Given
        UUID id = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String phaseName = "TestPhase";
        JobState state = JobState.NOTSTARTED;
        Instant now = Instant.now();
        
        // When
        JobPhase phase = new JobPhase(id, jobId, phaseName, state, now, now);
        
        // Then
        assertThat(phase.getId()).isEqualTo(id);
        assertThat(phase.getJobId()).isEqualTo(jobId);
        assertThat(phase.getPhaseName()).isEqualTo(phaseName);
        assertThat(phase.getState()).isEqualTo(state);
        assertThat(phase.getCreatedAt()).isEqualTo(now);
        assertThat(phase.getUpdatedAt()).isEqualTo(now);
    }
    
    @Test
    void testJobPhaseCreation_WithDefaultConstructor_CreatesEmptyJobPhase() {
        // When
        JobPhase phase = new JobPhase();
        
        // Then
        assertThat(phase).isNotNull();
        assertThat(phase.getId()).isNull();
        assertThat(phase.getJobId()).isNull();
        assertThat(phase.getPhaseName()).isNull();
        assertThat(phase.getState()).isNull();
    }
    
    @Test
    void testSetters_UpdateJobPhaseFields_FieldsAreUpdated() {
        // Given
        JobPhase phase = new JobPhase();
        UUID id = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String phaseName = "TestPhase";
        JobState state = JobState.INPROGRESS;
        Instant createdAt = Instant.now();
        Instant updatedAt = Instant.now();
        String metadata = "{\"key\":\"value\"}";
        
        // When
        phase.setId(id);
        phase.setJobId(jobId);
        phase.setPhaseName(phaseName);
        phase.setState(state);
        phase.setCreatedAt(createdAt);
        phase.setUpdatedAt(updatedAt);
        phase.setMetadata(metadata);
        
        // Then
        assertThat(phase.getId()).isEqualTo(id);
        assertThat(phase.getJobId()).isEqualTo(jobId);
        assertThat(phase.getPhaseName()).isEqualTo(phaseName);
        assertThat(phase.getState()).isEqualTo(state);
        assertThat(phase.getCreatedAt()).isEqualTo(createdAt);
        assertThat(phase.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(phase.getMetadata()).isEqualTo(metadata);
    }
    
    @Test
    void testEquals_SameId_ReturnsTrue() {
        // Given
        UUID id = UUID.randomUUID();
        JobPhase phase1 = new JobPhase();
        phase1.setId(id);
        
        JobPhase phase2 = new JobPhase();
        phase2.setId(id);
        
        // When/Then
        assertThat(phase1).isEqualTo(phase2);
        assertThat(phase1.hashCode()).isEqualTo(phase2.hashCode());
    }
    
    @Test
    void testEquals_DifferentId_ReturnsFalse() {
        // Given
        JobPhase phase1 = new JobPhase();
        phase1.setId(UUID.randomUUID());
        
        JobPhase phase2 = new JobPhase();
        phase2.setId(UUID.randomUUID());
        
        // When/Then
        assertThat(phase1).isNotEqualTo(phase2);
    }
    
    @Test
    void testEquals_NullId_ReturnsFalse() {
        // Given
        JobPhase phase1 = new JobPhase();
        JobPhase phase2 = new JobPhase();
        
        // When/Then
        assertThat(phase1).isNotEqualTo(phase2);
    }
    
    @Test
    void testEquals_SameInstance_ReturnsTrue() {
        // Given
        JobPhase phase = new JobPhase();
        phase.setId(UUID.randomUUID());
        
        // When/Then
        assertThat(phase).isEqualTo(phase);
    }
    
    @Test
    void testEquals_Null_ReturnsFalse() {
        // Given
        JobPhase phase = new JobPhase();
        phase.setId(UUID.randomUUID());
        
        // When/Then
        assertThat(phase).isNotEqualTo(null);
    }
    
    @Test
    void testEquals_DifferentClass_ReturnsFalse() {
        // Given
        JobPhase phase = new JobPhase();
        phase.setId(UUID.randomUUID());
        String other = "not a job phase";
        
        // When/Then
        assertThat(phase).isNotEqualTo(other);
    }
    
    @Test
    void testToString_ContainsKeyFields() {
        // Given
        UUID id = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String phaseName = "TestPhase";
        JobState state = JobState.NOTSTARTED;
        Instant now = Instant.now();
        
        JobPhase phase = new JobPhase(id, jobId, phaseName, state, now, now);
        
        // When
        String toString = phase.toString();
        
        // Then
        assertThat(toString).contains("JobPhase{");
        assertThat(toString).contains("id=" + id);
        assertThat(toString).contains("jobId=" + jobId);
        assertThat(toString).contains("phaseName='TestPhase'");
        assertThat(toString).contains("state=NOTSTARTED");
    }
    
    @Test
    void testMetadata_JsonbColumn_StoresJsonString() {
        // Given
        JobPhase phase = new JobPhase();
        String jsonMetadata = "{\"key\":\"value\",\"count\":42}";
        
        // When
        phase.setMetadata(jsonMetadata);
        
        // Then
        assertThat(phase.getMetadata()).isEqualTo(jsonMetadata);
    }
    
    @Test
    void testPhaseName_MaxLength100_StoresName() {
        // Given
        JobPhase phase = new JobPhase();
        String longPhaseName = "A".repeat(100);
        
        // When
        phase.setPhaseName(longPhaseName);
        
        // Then
        assertThat(phase.getPhaseName()).isEqualTo(longPhaseName);
        assertThat(phase.getPhaseName()).hasSize(100);
    }
    
    @Test
    void testJobId_ForeignKeyReference_StoresJobId() {
        // Given
        JobPhase phase = new JobPhase();
        UUID jobId = UUID.randomUUID();
        
        // When
        phase.setJobId(jobId);
        
        // Then
        assertThat(phase.getJobId()).isEqualTo(jobId);
    }
}
