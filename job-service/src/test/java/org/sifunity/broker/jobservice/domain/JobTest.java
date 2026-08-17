// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.domain;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.JobState;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Job entity.
 */
class JobTest {
    
    @Test
    void testJobCreation_WithRequiredFields_CreatesJob() {
        // Given
        UUID id = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String serviceName = "TestService";
        String zoneId = "zone1";
        JobState state = JobState.NOTSTARTED;
        Instant now = Instant.now();
        
        // When
        Job job = new Job(id, environmentId, serviceName, zoneId, state, now, now);
        
        // Then
        assertThat(job.getId()).isEqualTo(id);
        assertThat(job.getEnvironmentId()).isEqualTo(environmentId);
        assertThat(job.getServiceName()).isEqualTo(serviceName);
        assertThat(job.getZoneId()).isEqualTo(zoneId);
        assertThat(job.getState()).isEqualTo(state);
        assertThat(job.getCreatedAt()).isEqualTo(now);
        assertThat(job.getUpdatedAt()).isEqualTo(now);
    }
    
    @Test
    void testJobCreation_WithDefaultConstructor_CreatesEmptyJob() {
        // When
        Job job = new Job();
        
        // Then
        assertThat(job).isNotNull();
        assertThat(job.getId()).isNull();
        assertThat(job.getState()).isNull();
    }
    
    @Test
    void testSetters_UpdateJobFields_FieldsAreUpdated() {
        // Given
        Job job = new Job();
        UUID id = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String serviceName = "TestService";
        String zoneId = "zone1";
        String contextId = "context1";
        JobState state = JobState.INPROGRESS;
        Instant createdAt = Instant.now();
        Instant updatedAt = Instant.now();
        String currentPhase = "Phase1";
        String metadata = "{\"key\":\"value\"}";
        Integer version = 1;
        
        // When
        job.setId(id);
        job.setEnvironmentId(environmentId);
        job.setServiceName(serviceName);
        job.setZoneId(zoneId);
        job.setContextId(contextId);
        job.setState(state);
        job.setCreatedAt(createdAt);
        job.setUpdatedAt(updatedAt);
        job.setCurrentPhase(currentPhase);
        job.setMetadata(metadata);
        job.setVersion(version);
        
        // Then
        assertThat(job.getId()).isEqualTo(id);
        assertThat(job.getEnvironmentId()).isEqualTo(environmentId);
        assertThat(job.getServiceName()).isEqualTo(serviceName);
        assertThat(job.getZoneId()).isEqualTo(zoneId);
        assertThat(job.getContextId()).isEqualTo(contextId);
        assertThat(job.getState()).isEqualTo(state);
        assertThat(job.getCreatedAt()).isEqualTo(createdAt);
        assertThat(job.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(job.getCurrentPhase()).isEqualTo(currentPhase);
        assertThat(job.getMetadata()).isEqualTo(metadata);
        assertThat(job.getVersion()).isEqualTo(version);
    }
    
    @Test
    void testEquals_SameId_ReturnsTrue() {
        // Given
        UUID id = UUID.randomUUID();
        Job job1 = new Job();
        job1.setId(id);
        
        Job job2 = new Job();
        job2.setId(id);
        
        // When/Then
        assertThat(job1).isEqualTo(job2);
        assertThat(job1.hashCode()).isEqualTo(job2.hashCode());
    }
    
    @Test
    void testEquals_DifferentId_ReturnsFalse() {
        // Given
        Job job1 = new Job();
        job1.setId(UUID.randomUUID());
        
        Job job2 = new Job();
        job2.setId(UUID.randomUUID());
        
        // When/Then
        assertThat(job1).isNotEqualTo(job2);
    }
    
    @Test
    void testEquals_NullId_ReturnsFalse() {
        // Given
        Job job1 = new Job();
        Job job2 = new Job();
        
        // When/Then
        assertThat(job1).isNotEqualTo(job2);
    }
    
    @Test
    void testEquals_SameInstance_ReturnsTrue() {
        // Given
        Job job = new Job();
        job.setId(UUID.randomUUID());
        
        // When/Then
        assertThat(job).isEqualTo(job);
    }
    
    @Test
    void testEquals_Null_ReturnsFalse() {
        // Given
        Job job = new Job();
        job.setId(UUID.randomUUID());
        
        // When/Then
        assertThat(job).isNotEqualTo(null);
    }
    
    @Test
    void testEquals_DifferentClass_ReturnsFalse() {
        // Given
        Job job = new Job();
        job.setId(UUID.randomUUID());
        String other = "not a job";
        
        // When/Then
        assertThat(job).isNotEqualTo(other);
    }
    
    @Test
    void testToString_ContainsKeyFields() {
        // Given
        UUID id = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String serviceName = "TestService";
        String zoneId = "zone1";
        JobState state = JobState.NOTSTARTED;
        Instant now = Instant.now();
        
        Job job = new Job(id, environmentId, serviceName, zoneId, state, now, now);
        job.setCurrentPhase("Phase1");
        job.setVersion(1);
        
        // When
        String toString = job.toString();
        
        // Then
        assertThat(toString).contains("Job{");
        assertThat(toString).contains("id=" + id);
        assertThat(toString).contains("environmentId=" + environmentId);
        assertThat(toString).contains("serviceName='TestService'");
        assertThat(toString).contains("zoneId='zone1'");
        assertThat(toString).contains("state=NOTSTARTED");
        assertThat(toString).contains("currentPhase='Phase1'");
        assertThat(toString).contains("version=1");
    }
    
    @Test
    void testMetadata_JsonbColumn_StoresJsonString() {
        // Given
        Job job = new Job();
        String jsonMetadata = "{\"key\":\"value\",\"count\":42}";
        
        // When
        job.setMetadata(jsonMetadata);
        
        // Then
        assertThat(job.getMetadata()).isEqualTo(jsonMetadata);
    }
    
    @Test
    void testVersion_OptimisticLocking_VersionField() {
        // Given
        Job job = new Job();
        
        // When
        job.setVersion(0);
        
        // Then
        assertThat(job.getVersion()).isEqualTo(0);
        
        // When
        job.setVersion(1);
        
        // Then
        assertThat(job.getVersion()).isEqualTo(1);
    }
}
