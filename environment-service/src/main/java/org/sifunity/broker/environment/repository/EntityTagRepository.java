// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.EntityTag;
import org.sifunity.broker.environment.domain.EntityType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntityTagRepository extends JpaRepository<EntityTag, UUID> {

    List<EntityTag> findBySolutionId(UUID solutionId);

    List<EntityTag> findBySolutionIdAndEntityTypeAndEntityId(
            UUID solutionId, EntityType entityType, UUID entityId);

    List<EntityTag> findBySolutionIdAndTagKey(UUID solutionId, String tagKey);

    Optional<EntityTag> findBySolutionIdAndEntityTypeAndEntityIdAndTagKey(
            UUID solutionId, EntityType entityType, UUID entityId, String tagKey);

    boolean existsBySolutionIdAndEntityTypeAndEntityIdAndTagKey(
            UUID solutionId, EntityType entityType, UUID entityId, String tagKey);
}
