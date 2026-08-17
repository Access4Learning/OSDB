// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.repository;

import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.subscriptionservice.domain.AclEntryRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only repository interface for looking up ACL entries.
 *
 * <p>The subscription-service uses this repository to resolve ACL entries
 * during subscription creation — either by direct ID lookup or by matching
 * on service name, zone/context identifiers, and access type.</p>
 *
 * <p>Queries join through the context_services table since acl_entries now
 * references context_services via context_service_uuid.</p>
 *
 * <p>This is a lightweight read-only interface. The environment-service
 * remains the owner of ACL entry lifecycle.</p>
 */
@Repository
public interface AclEntryLookupRepository extends JpaRepository<AclEntryRef, UUID> {

    /**
     * Finds an ACL entry matching the given service, context UUID, and access type.
     * Joins through context_services to match on serviceName and contextUuid.
     *
     * @param serviceName the service name
     * @param contextUuid the context UUID
     * @param accessType the required access type (SUBSCRIBE for EVENT, PROVIDE for DELAYED_REQUEST)
     * @return the matching ACL entry, or empty if none found
     */
    @Query("SELECT a FROM AclEntryRef a " +
           "JOIN ContextServiceRef cs ON a.contextServiceUuid = cs.id " +
           "WHERE cs.serviceName = :serviceName " +
           "AND cs.contextUuid = :contextUuid " +
           "AND a.accessType = :accessType")
    Optional<AclEntryRef> findByServiceNameAndContextUuidAndAccessType(
        @Param("serviceName") String serviceName,
        @Param("contextUuid") UUID contextUuid,
        @Param("accessType") AccessType accessType
    );

    /**
     * Finds an ACL entry by joining through context_services, contexts, zones,
     * solutions, and domains to match on string-based domainId/solutionId/zoneId/contextId
     * identifiers (SIF 3.7 spec fields).
     *
     * <p>Used during subscription creation when the caller provides serviceName,
     * zoneId, and contextId strings instead of an explicit ACL entry ID.</p>
     *
     * <p><strong>Tenant isolation:</strong> zoneId is unique only within a solution and
     * contextId only within a zone, so the join MUST be scoped by the owning tenant.
     * The domainId + solutionId from the request path
     * ({@code /{domainId}/{solutionId}/subscriptions}) constrain the join to the
     * correct solution (uk_domain_uuid_solution → uk_solution_uuid_zone →
     * uk_zone_uuid_context), guaranteeing at most one match and preventing
     * cross-tenant ACL leakage / NonUniqueResultException.</p>
     *
     * @param serviceName the service name
     * @param domainId the domain identifier string (request path)
     * @param solutionId the solution identifier string (request path)
     * @param zoneId the zone identifier string
     * @param contextId the context identifier string
     * @param accessType the required access type
     * @return the matching ACL entry, or empty if none found
     */
    @Query("SELECT a FROM AclEntryRef a " +
           "JOIN ContextServiceRef cs ON a.contextServiceUuid = cs.id " +
           "JOIN ContextRef c ON cs.contextUuid = c.id " +
           "JOIN ZoneRef z ON c.zone.id = z.id " +
           "JOIN SolutionRef sol ON z.solutionUuid = sol.id " +
           "JOIN DomainRef d ON sol.domainUuid = d.id " +
           "WHERE cs.serviceName = :serviceName " +
           "AND a.accessType = :accessType " +
           "AND d.domainId = :domainId " +
           "AND sol.solutionId = :solutionId " +
           "AND z.zoneId = :zoneId " +
           "AND c.contextId = :contextId")
    Optional<AclEntryRef> findByServiceNameAndZoneContextAndAccessType(
        @Param("serviceName") String serviceName,
        @Param("domainId") String domainId,
        @Param("solutionId") String solutionId,
        @Param("zoneId") String zoneId,
        @Param("contextId") String contextId,
        @Param("accessType") AccessType accessType
    );
}
