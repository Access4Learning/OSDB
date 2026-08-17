// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.repository;

import org.sifunity.broker.jobservice.domain.JobPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for JobPhase entities.
 * 
 * Provides CRUD operations and custom query methods for job phase management.
 * All query methods follow Spring Data JPA naming conventions for automatic
 * query generation, except for the custom delete method which uses JPQL.
 * 
 * @see JobPhase
 */
@Repository
public interface JobPhaseRepository extends JpaRepository<JobPhase, UUID> {
    
    /**
     * Find all phases for a given job, ordered by creation date ascending (oldest first).
     * 
     * This method supports the phase listing requirement where consumers can view
     * all phases for a job in chronological order to track progress through
     * the job's lifecycle.
     * 
     * @param jobId the job ID to filter by
     * @return list of phases ordered by createdAt ascending
     */
    List<JobPhase> findByJobIdOrderByCreatedAtAsc(UUID jobId);
    
    /**
     * Check if a phase with the given name already exists for a job.
     * 
     * This method supports the duplicate phase name validation requirement,
     * ensuring that each phase name is unique within a job. This prevents
     * confusion and maintains clear phase identification.
     * 
     * @param jobId the job ID to check
     * @param phaseName the phase name to check for duplicates
     * @return true if a phase with this name exists for the job, false otherwise
     */
    boolean existsByJobIdAndPhaseName(UUID jobId, String phaseName);
    
    /**
     * Delete all phases associated with a specific job.
     * 
     * This method is used for cascade deletion when a job is deleted.
     * While the database foreign key constraint handles cascade deletion
     * automatically, this method provides explicit control for service-level
     * operations and testing scenarios.
     * 
     * The @Modifying annotation indicates this is a write operation that
     * modifies the database state.
     * 
     * @param jobId the job ID whose phases should be deleted
     */
    @Modifying
    @Query("DELETE FROM JobPhase p WHERE p.jobId = :jobId")
    void deleteByJobId(@Param("jobId") UUID jobId);
}
