// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.ProvisionRequest;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for ProvisionRequest entities.
 *
 * <p>Provides data access operations for the provision_requests table including
 * standard CRUD operations and custom query methods for provision request
 * management.</p>
 *
 * @see ProvisionRequest
 */
@Repository
public interface ProvisionRequestRepository extends JpaRepository<ProvisionRequest, UUID> {

    /**
     * Finds all provision requests for a specific environment.
     *
     * <p>This method is used to retrieve all provision requests associated
     * with an environment, supporting listing and management operations.</p>
     *
     * @param environmentId the environment ID to search for (must not be null)
     * @return List of provision requests for the environment (empty list if none found)
     */
    List<ProvisionRequest> findByEnvironmentId(UUID environmentId);
}
