// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sifunity.broker.environment.domain.TagDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for TagDefinition entities.
 *
 * <p>Provides data access operations for the tag_definitions table including
 * standard CRUD operations and custom query methods for case-insensitive
 * name lookups and entity type filtering.</p>
 *
 * @see TagDefinition
 */
@Repository
public interface TagDefinitionRepository extends JpaRepository<TagDefinition, UUID> {

    /**
     * Finds a tag definition by its name, ignoring case.
     *
     * @param tagName the tag name to search for
     * @return Optional containing the tag definition if found
     */
    Optional<TagDefinition> findByTagNameIgnoreCase(String tagName);

    /**
     * Checks whether a tag definition exists with the given name, ignoring case.
     *
     * @param tagName the tag name to check
     * @return true if a definition exists with that name (case-insensitive)
     */
    boolean existsByTagNameIgnoreCase(String tagName);

    /**
     * Returns all tag definitions ordered alphabetically by tag name.
     *
     * @return list of all tag definitions sorted by tag_name ascending
     */
    List<TagDefinition> findAllByOrderByTagNameAsc();

    /**
     * Finds tag definitions whose applicable_entity_types array contains
     * the specified entity type. Uses a native query with PostgreSQL's
     * array contains operator since the column is a VARCHAR[].
     *
     * @param entityType the entity type to filter by
     * @return list of tag definitions applicable to the given entity type, ordered by tag_name
     */
    @Query(value = "SELECT * FROM tag_definitions WHERE applicable_entity_types @> ARRAY[CAST(:entityType AS VARCHAR)] ORDER BY tag_name ASC",
           nativeQuery = true)
    List<TagDefinition> findByApplicableEntityType(@Param("entityType") String entityType);
}
