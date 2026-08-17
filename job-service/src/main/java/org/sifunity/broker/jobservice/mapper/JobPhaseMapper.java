// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice.mapper;

import org.sifunity.broker.jobservice.domain.JobPhase;
import org.sifunity.broker.jobservice.dto.JobPhaseDTO;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between JobPhase entity and JobPhaseDTO.
 */
@Component
public class JobPhaseMapper {
    
    /**
     * Converts a JobPhase entity to a JobPhaseDTO.
     *
     * @param phase the JobPhase entity
     * @return the JobPhaseDTO
     */
    public JobPhaseDTO toDto(JobPhase phase) {
        if (phase == null) {
            return null;
        }
        
        return new JobPhaseDTO(
            phase.getId(),
            phase.getJobId(),
            phase.getPhaseName(),
            phase.getState(),
            phase.getCreatedAt(),
            phase.getUpdatedAt(),
            phase.getMetadata()
        );
    }
}
