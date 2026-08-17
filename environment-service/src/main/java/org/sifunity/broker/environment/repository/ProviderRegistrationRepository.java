// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.environment.domain.ProviderRegistration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for ProviderRegistration entities.
 * 
 * <p>Provides data access operations for the provider_registry table including
 * standard CRUD operations and custom query methods for provider service
 * discovery and management.</p>
 * 
 * <p>This repository supports the following key operations:</p>
 * <ul>
 *   <li>Finding provider registrations by context_service_uuid for service discovery</li>
 *   <li>Finding all provider registrations for an environment for management operations</li>
 *   <li>Checking existence of provider registrations for uniqueness validation</li>
 * </ul>
 * 
 * <p>The unique constraint on provider registrations is enforced at the database level
 * on the combination of (environment_id, context_service_uuid).</p>
 * 
 * @see ProviderRegistration
 */
@Repository
public interface ProviderRegistrationRepository extends JpaRepository<ProviderRegistration, UUID> {
    
    /**
     * Finds a provider registration by context service UUID.
     * 
     * <p>This method is the primary lookup mechanism for provider service discovery.
     * It locates the provider that can handle requests for a specific context service.
     * When multiple providers are registered for the same context service, the first
     * one found is returned.</p>
     * 
     * @param contextServiceUuid the context service UUID to search for (must not be null)
     * @return Optional containing the provider registration if found, empty otherwise
     */
    Optional<ProviderRegistration> findByContextServiceUuid(UUID contextServiceUuid);
    
    /**
     * Finds all provider registrations for a specific environment.
     * 
     * <p>This method is used to retrieve all services registered by a specific
     * provider environment for management and listing operations.</p>
     * 
     * @param environmentId the environment ID to search for (must not be null)
     * @return List of provider registrations for the environment (empty list if none found)
     */
    List<ProviderRegistration> findByEnvironmentId(UUID environmentId);
    
    /**
     * Checks if a provider registration exists with the specified environment and context service.
     * 
     * <p>This method is used to enforce the uniqueness constraint on provider registrations
     * during creation. It ensures that only one provider per environment can register for
     * a specific context service.</p>
     * 
     * @param environmentId the environment ID to scope the check to (must not be null)
     * @param contextServiceUuid the context service UUID to check (must not be null)
     * @return true if a provider registration with these parameters exists, false otherwise
     */
    boolean existsByEnvironmentIdAndContextServiceUuid(UUID environmentId, UUID contextServiceUuid);
    
    /**
     * Finds a provider registration by context service UUID and service type.
     * 
     * <p>This method supports filtered provider lookup when a serviceType is specified.
     * It ensures that XQUERYTEMPLATE lookups don't collide with OBJECT lookups
     * on the same serviceName by filtering on the service_type column.</p>
     * 
     * @param contextServiceUuid the context service UUID to search for (must not be null)
     * @param serviceType the service type to filter by (must not be null)
     * @return Optional containing the provider registration if found, empty otherwise
     */
    Optional<ProviderRegistration> findByContextServiceUuidAndServiceType(UUID contextServiceUuid, ServiceType serviceType);
}
