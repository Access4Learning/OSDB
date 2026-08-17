// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.environment.domain.AclEntry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for AclEntry entities.
 * 
 * <p>Provides data access operations for the acl_entries table including
 * standard CRUD operations and custom query methods for ACL management
 * and permission checking.</p>
 * 
 * <p>This repository supports the following key operations:</p>
 * <ul>
 *   <li>Finding ACL entries by environment ID for listing operations</li>
 *   <li>Finding specific ACL entries by environment, context_service_uuid, and access type</li>
 *   <li>Checking existence of ACL entries for uniqueness validation</li>
 *   <li>Supporting permission checks for authorization</li>
 * </ul>
 * 
 * <p>The unique constraint on ACL entries is enforced at the database level
 * on the combination of (environment_id, context_service_uuid, access_type).</p>
 * 
 * @see AclEntry
 * @see AccessType
 */
@Repository
public interface AclEntryRepository extends JpaRepository<AclEntry, UUID> {
    
    /**
     * Finds all ACL entries for a specific environment.
     * 
     * <p>This method is used to retrieve all permissions associated with
     * an environment for listing and management operations. Returns all
     * ACL entries regardless of their access level or type.</p>
     * 
     * @param environmentId the environment ID to search for (must not be null)
     * @return List of ACL entries for the environment (empty list if none found)
     */
    List<AclEntry> findByEnvironmentId(UUID environmentId);
    
    /**
     * Finds a specific ACL entry by environment, context_service_uuid, and access type.
     * 
     * <p>This method is used to locate a specific ACL entry using all the
     * parameters that form the unique constraint. Essential for permission
     * checking and duplicate validation during ACL creation.</p>
     * 
     * @param environmentId the environment ID (must not be null)
     * @param contextServiceUuid the context_service UUID FK referencing context_services(id) (must not be null)
     * @param accessType the access type (must not be null)
     * @return Optional containing the ACL entry if found, empty otherwise
     */
    Optional<AclEntry> findByEnvironmentIdAndContextServiceUuidAndAccessType(
        UUID environmentId,
        UUID contextServiceUuid,
        AccessType accessType
    );

    /**
     * Checks whether an environment holds ANY grant of the given access level on a
     * context_service, regardless of access type.
     *
     * <p>Answers "is this environment entitled to this object at all?" — used by the
     * response-destination entitlement check, where the specific access type cannot be known.
     * A delayed response is delivered to the queue of the consumer that issued the original
     * request, and that consumer may hold only {@code CREATE}, {@code UPDATE} or {@code DELETE}
     * rather than {@code QUERY} (the response to a create is still a response). Requiring a
     * specific type would therefore reject legitimate responses.</p>
     *
     * <p>Expressed as an existence check rather than fetching entries so the database answers it
     * with a single indexed lookup and no rows are materialised.</p>
     *
     * @param environmentId the environment ID (must not be null)
     * @param contextServiceUuid the context_service UUID FK referencing context_services(id) (must not be null)
     * @param accessLevel the access level that counts as entitlement, normally {@code APPROVED}
     * @return true if at least one matching ACL entry exists
     */
    boolean existsByEnvironmentIdAndContextServiceUuidAndAccessLevel(
        UUID environmentId,
        UUID contextServiceUuid,
        AccessLevel accessLevel
    );
    
    /**
     * Checks if an ACL entry exists with the specified parameters.
     * 
     * <p>This method is used to enforce the uniqueness constraint on ACL entries
     * during creation. More efficient than findBy... when only existence
     * checking is needed.</p>
     * 
     * @param environmentId the environment ID (must not be null)
     * @param contextServiceUuid the context_service UUID (must not be null)
     * @param accessType the access type (must not be null)
     * @return true if an ACL entry with these parameters exists, false otherwise
     */
    boolean existsByEnvironmentIdAndContextServiceUuidAndAccessType(
        UUID environmentId,
        UUID contextServiceUuid,
        AccessType accessType
    );

    /**
     * Finds all ACL entries for a batch of environment IDs.
     *
     * <p>This method is used for efficient batch loading of ACL entries
     * when building SIF 3.7 environment responses for list operations,
     * avoiding N+1 query problems.</p>
     *
     * @param environmentIds the collection of environment IDs to search for
     * @return List of ACL entries for all specified environments
     */
    List<AclEntry> findByEnvironmentIdIn(Collection<UUID> environmentIds);

    /**
     * Checks if any environment already provides a given context_service.
     *
     * <p>Per SIF 3.7, only one provider is allowed per context_service.
     * This method checks across all environments whether a PROVIDE entry already
     * exists for the given context_service_uuid.</p>
     *
     * @param contextServiceUuid the context_service UUID
     * @param accessType the access type (should be PROVIDE)
     * @return true if a provider already exists for this context_service
     */
    boolean existsByContextServiceUuidAndAccessType(
        UUID contextServiceUuid,
        AccessType accessType
    );

    /**
     * Finds all ACL entries for a specific environment with a given access type and access level.
     *
     * <p>Used to retrieve PROVIDE/APPROVED ACL entries for computing available
     * provider registration options (set difference with existing registrations).</p>
     *
     * @param environmentId the environment ID (must not be null)
     * @param accessType the access type to filter by (must not be null)
     * @param accessLevel the access level to filter by (must not be null)
     * @return List of matching ACL entries (empty list if none found)
     */
    List<AclEntry> findByEnvironmentIdAndAccessTypeAndAccessLevel(
        UUID environmentId,
        AccessType accessType,
        AccessLevel accessLevel
    );

    /**
     * Checks if any ACL entry exists for a context_service with a given access type and access level.
     *
     * <p>Used by the DELAYED routing fallback to check whether a PROVIDE ACL
     * (accessType=PROVIDE, accessLevel=APPROVED) exists for a given service/zone/context,
     * regardless of which environment holds the ACL.</p>
     *
     * @param contextServiceUuid the context_service UUID (must not be null)
     * @param accessType the access type to filter by (must not be null)
     * @param accessLevel the access level to filter by (must not be null)
     * @return true if a matching ACL entry exists, false otherwise
     */
    boolean existsByContextServiceUuidAndAccessTypeAndAccessLevel(
        UUID contextServiceUuid,
        AccessType accessType,
        AccessLevel accessLevel
    );

    /**
     * Finds an ACL entry for a context_service with a given access type and access level.
     *
     * <p>Used by the DELAYED routing fallback to find the provider environment
     * that holds the PROVIDE ACL, so messages can be routed to that provider's queue.</p>
     *
     * @param contextServiceUuid the context_service UUID (must not be null)
     * @param accessType the access type to filter by (must not be null)
     * @param accessLevel the access level to filter by (must not be null)
     * @return Optional containing the first matching ACL entry, empty if none found
     */
    Optional<AclEntry> findFirstByContextServiceUuidAndAccessTypeAndAccessLevel(
        UUID contextServiceUuid,
        AccessType accessType,
        AccessLevel accessLevel
    );
}
