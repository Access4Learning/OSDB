// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.mapper;

import org.sifunity.broker.jobservice.domain.Job;
import org.sifunity.broker.jobservice.dto.JobDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Job entity and JobDTO.
 */
@Component
public class JobMapper {
    
    /**
     * Converts a Job entity to a JobDTO.
     *
     * @param job the Job entity
     * @return the JobDTO
     */
    public JobDTO toDto(Job job) {
        if (job == null) {
            return null;
        }
        
        return new JobDTO(
            job.getId(),
            job.getEnvironmentId(),
            job.getServiceName(),
            job.getZoneId(),
            job.getContextId(),
            job.getState(),
            job.getCreatedAt(),
            job.getUpdatedAt(),
            job.getCurrentPhase(),
            job.getMetadata()
        );
    }
}
