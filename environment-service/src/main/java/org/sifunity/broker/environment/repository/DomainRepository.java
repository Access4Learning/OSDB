// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.Domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Domain entities.
 */
@Repository
public interface DomainRepository extends JpaRepository<Domain, UUID> {
    
    /**
     * Finds a domain by its domain ID.
     * 
     * @param domainId the domain ID to search for
     * @return Optional containing the domain if found, empty otherwise
     */
    Optional<Domain> findByDomainId(String domainId);
    
    /**
     * Checks if a domain with the given domain ID exists.
     * 
     * @param domainId the domain ID to check
     * @return true if a domain with this domain ID exists, false otherwise
     */
    boolean existsByDomainId(String domainId);
}
