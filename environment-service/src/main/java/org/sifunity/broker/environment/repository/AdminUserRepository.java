// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.AdminUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for AdminUser entities.
 */
@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    /**
     * Finds an admin user by username within a specific domain+solution scope.
     *
     * @param username the username to search for
     * @param domainId the domain UUID
     * @param solutionId the solution UUID
     * @return Optional containing the admin user if found, empty otherwise
     */
    Optional<AdminUser> findByUsernameAndDomain_IdAndSolution_Id(String username, UUID domainId, UUID solutionId);

    /**
     * Checks if an admin user with the given username exists within a specific domain+solution scope.
     *
     * @param username the username to check
     * @param domainId the domain UUID
     * @param solutionId the solution UUID
     * @return true if a user with this username exists in the scope, false otherwise
     */
    boolean existsByUsernameAndDomain_IdAndSolution_Id(String username, UUID domainId, UUID solutionId);

    /**
     * Finds all admin users within a specific domain+solution scope.
     *
     * @param domainId the domain UUID
     * @param solutionId the solution UUID
     * @return List of admin users in the domain+solution scope
     */
    List<AdminUser> findAllByDomain_IdAndSolution_Id(UUID domainId, UUID solutionId);
}
