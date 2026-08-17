// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.repository;

import org.sifunity.broker.common.constant.JobState;
import org.sifunity.broker.jobservice.domain.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for Job entities.
 * 
 * Provides CRUD operations and custom query methods for job management.
 * All query methods follow Spring Data JPA naming conventions for automatic
 * query generation.
 * 
 * @see Job
 * @see JobState
 */
@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {
    
    /**
     * Find all jobs for a given environment, ordered by creation date descending (newest first).
     * 
     * This method supports the job listing requirement where consumers can view
     * all their jobs in reverse chronological order.
     * 
     * @param environmentId the environment ID to filter by
     * @return list of jobs ordered by createdAt descending
     */
    List<Job> findByEnvironmentIdOrderByCreatedAtDesc(UUID environmentId);
    
    /**
     * Find all jobs for a given environment with a specific state,
     * ordered by creation date descending.
     * 
     * This method supports filtering jobs by state (e.g., only INPROGRESS jobs).
     * 
     * @param environmentId the environment ID to filter by
     * @param state the job state to filter by
     * @return list of jobs matching the criteria, ordered by createdAt descending
     */
    List<Job> findByEnvironmentIdAndState(UUID environmentId, JobState state);
    
    /**
     * Find all jobs for a given environment with a specific service name,
     * ordered by creation date descending.
     * 
     * This method supports filtering jobs by service name to view jobs
     * for a specific functional service.
     * 
     * @param environmentId the environment ID to filter by
     * @param serviceName the service name to filter by
     * @return list of jobs matching the criteria, ordered by createdAt descending
     */
    List<Job> findByEnvironmentIdAndServiceName(UUID environmentId, String serviceName);
    
    /**
     * Find all jobs for a given environment with a specific state and service name,
     * ordered by creation date descending.
     * 
     * This method supports combined filtering by both state and service name,
     * allowing consumers to view specific subsets of jobs (e.g., all INPROGRESS
     * jobs for a particular service).
     * 
     * @param environmentId the environment ID to filter by
     * @param state the job state to filter by
     * @param serviceName the service name to filter by
     * @return list of jobs matching all criteria, ordered by createdAt descending
     */
    List<Job> findByEnvironmentIdAndStateAndServiceName(
            UUID environmentId, 
            JobState state, 
            String serviceName);
}
