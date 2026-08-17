// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.ContextService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for ContextService entities.
 * 
 * <p>Provides data access operations for the context_services table including
 * standard CRUD operations and custom query methods for context service
 * management and lookup.</p>
 * 
 * <p>This repository supports the following key operations:</p>
 * <ul>
 *   <li>Finding context services by context UUID and service name for resolution</li>
 *   <li>Checking existence for duplicate validation</li>
 *   <li>Listing context services by context for management operations</li>
 *   <li>Listing context services by zone (joining through contexts table)</li>
 * </ul>
 * 
 * <p>The unique constraint on context_services is enforced at the database level
 * on the combination of (context_uuid, service_name).</p>
 * 
 * @see ContextService
 * @see Context
 */
@Repository
public interface ContextServiceRepository extends JpaRepository<ContextService, UUID> {

    /**
     * Finds a context service by context UUID and service name.
     * 
     * <p>This method is used to resolve a (contextUuid, serviceName) pair to
     * a specific context_service record. Essential for the resolveOrCreate
     * pattern used by AclService and ProviderService.</p>
     * 
     * @param contextUuid the context UUID to search for (must not be null)
     * @param serviceName the service name to search for (must not be null)
     * @return Optional containing the context service if found, empty otherwise
     */
    Optional<ContextService> findByContextUuidAndServiceName(UUID contextUuid, String serviceName);

    /**
     * Checks if a context service exists with the specified context UUID and service name.
     * 
     * <p>This method is used to enforce the uniqueness constraint on context services
     * during creation. More efficient than findBy... when only existence checking
     * is needed, as it doesn't load the full entity.</p>
     * 
     * @param contextUuid the context UUID to check (must not be null)
     * @param serviceName the service name to check (must not be null)
     * @return true if a context service with these parameters exists, false otherwise
     */
    boolean existsByContextUuidAndServiceName(UUID contextUuid, String serviceName);

    /**
     * Finds all context services within a specific context.
     * 
     * <p>This method is used to list all services registered in a given context
     * for management and display operations.</p>
     * 
     * @param contextUuid the context UUID to search for (must not be null)
     * @return List of context services within the context (empty list if none found)
     */
    List<ContextService> findByContextUuid(UUID contextUuid);

    /**
     * Finds a context service by context UUID, service name, and service type.
     * 
     * <p>This method is used to resolve a (contextUuid, serviceName, serviceType) triple
     * to a specific context_service record. Used by provider lookup when serviceType
     * filtering is required (e.g., distinguishing OBJECT from XQUERYTEMPLATE providers
     * on the same serviceName).</p>
     * 
     * @param contextUuid the context UUID to search for (must not be null)
     * @param serviceName the service name to search for (must not be null)
     * @param serviceType the service type to filter by (must not be null)
     * @return Optional containing the context service if found, empty otherwise
     */
    Optional<ContextService> findByContextUuidAndServiceNameAndServiceType(UUID contextUuid, String serviceName, String serviceType);

    /**
     * Finds all context services within a specific zone by joining through the contexts table.
     * 
     * <p>This method uses a JPQL subquery to find all context services whose
     * context belongs to the specified zone. This supports the zone-level
     * listing operation in the ContextServiceController.</p>
     * 
     * @param zoneUuid the zone UUID to search for (must not be null)
     * @return List of context services across all contexts in the zone (empty list if none found)
     */
    @Query("SELECT cs FROM ContextService cs WHERE cs.contextUuid IN " +
           "(SELECT c.id FROM Context c WHERE c.zoneUuid = :zoneUuid)")
    List<ContextService> findByZoneUuid(@Param("zoneUuid") UUID zoneUuid);
}
