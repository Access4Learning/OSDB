// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.XQueryTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for XQueryTemplate entities.
 *
 * <p>Provides data access operations for the xquery_templates table including
 * standard CRUD operations and custom query methods for Named Query template
 * management and lookup.</p>
 *
 * <p>This repository supports the following key operations:</p>
 * <ul>
 *   <li>Finding a template by queryToken within a solution for single-template retrieval</li>
 *   <li>Listing all templates for a solution</li>
 *   <li>Checking existence of a template by queryToken within a solution for uniqueness validation</li>
 *   <li>Deleting a template by queryToken within a solution</li>
 * </ul>
 *
 * <p>The unique constraint on templates is enforced at the database level
 * on the combination of (query_token, solution_uuid).</p>
 *
 * @see XQueryTemplate
 */
@Repository
public interface XQueryTemplateRepository extends JpaRepository<XQueryTemplate, UUID> {

    /**
     * Finds a template by query token within a specific solution.
     *
     * <p>This method is the primary lookup mechanism for retrieving a single
     * Named Query template by its URL-addressable token, scoped to a solution.
     * Used for GET-by-token and provider lookup operations.</p>
     *
     * @param queryToken the query token to search for (must not be null)
     * @param solutionUuid the solution UUID to scope the search to (must not be null)
     * @return Optional containing the template if found, empty otherwise
     */
    Optional<XQueryTemplate> findByQueryTokenAndSolutionUuid(String queryToken, UUID solutionUuid);

    /**
     * Finds all templates for a specific solution.
     *
     * <p>This method is used to list all Named Query templates registered
     * within a solution for collection retrieval operations.</p>
     *
     * @param solutionUuid the solution UUID to search for (must not be null)
     * @return List of templates for the solution (empty list if none found)
     */
    List<XQueryTemplate> findAllBySolutionUuid(UUID solutionUuid);

    /**
     * Checks if a template exists with the specified query token within a solution.
     *
     * <p>This method is used to enforce the uniqueness constraint on templates
     * during creation. More efficient than findBy... when only existence
     * checking is needed.</p>
     *
     * @param queryToken the query token to check (must not be null)
     * @param solutionUuid the solution UUID to scope the check to (must not be null)
     * @return true if a template with this token exists in the solution, false otherwise
     */
    boolean existsByQueryTokenAndSolutionUuid(String queryToken, UUID solutionUuid);

    /**
     * Deletes a template by query token within a specific solution.
     *
     * <p>This method removes a Named Query template identified by its token,
     * scoped to a solution. Used by the delete operation to remove templates
     * by their URL-addressable token rather than internal UUID.</p>
     *
     * @param queryToken the query token of the template to delete (must not be null)
     * @param solutionUuid the solution UUID to scope the deletion to (must not be null)
     */
    void deleteByQueryTokenAndSolutionUuid(String queryToken, UUID solutionUuid);
}
