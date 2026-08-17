// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.domain.TagDefinition;
import org.sifunity.broker.environment.dto.TagDefinitionCreateRequest;
import org.sifunity.broker.environment.dto.TagDefinitionDTO;
import org.sifunity.broker.environment.dto.TagDefinitionUpdateRequest;
import org.sifunity.broker.environment.exception.DuplicateTagDefinitionException;
import org.sifunity.broker.environment.exception.TagDefinitionNotFoundException;
import org.sifunity.broker.environment.repository.TagDefinitionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing tag definitions — the governance layer that enforces
 * naming consistency and optional value constraints on entity tags.
 */
@Service
@Transactional(readOnly = true)
public class TagDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(TagDefinitionService.class);

    private final TagDefinitionRepository tagDefinitionRepository;

    public TagDefinitionService(TagDefinitionRepository tagDefinitionRepository) {
        this.tagDefinitionRepository = tagDefinitionRepository;
    }

    /**
     * Creates a new tag definition after validating uniqueness and entity type values.
     *
     * @param request the creation request
     * @return DTO of the persisted tag definition
     * @throws DuplicateTagDefinitionException if a definition with the same name exists (case-insensitive)
     * @throws IllegalArgumentException if any applicableEntityTypes value is not a valid EntityType
     */
    @Transactional
    public TagDefinitionDTO createTagDefinition(TagDefinitionCreateRequest request) {
        log.debug("Creating tag definition with name: {}", request.tagName());

        if (tagDefinitionRepository.existsByTagNameIgnoreCase(request.tagName())) {
            log.warn("Attempted to create duplicate tag definition: {}", request.tagName());
            throw new DuplicateTagDefinitionException(request.tagName());
        }

        validateEntityTypes(request.applicableEntityTypes());

        TagDefinition entity = new TagDefinition();
        entity.setTagName(request.tagName());
        entity.setTagDescription(request.tagDescription());
        entity.setApplicableEntityTypes(request.applicableEntityTypes());
        entity.setAllowedValues(request.allowedValues());
        entity.setCreatedBy(request.createdBy());

        TagDefinition saved = tagDefinitionRepository.save(entity);
        log.info("Created tag definition '{}' (id: {})", saved.getTagName(), saved.getId());

        return mapToDTO(saved);
    }

    /**
     * Retrieves a tag definition by its ID.
     *
     * @param id the tag definition UUID
     * @return DTO of the tag definition
     * @throws TagDefinitionNotFoundException if no definition exists with that ID
     */
    public TagDefinitionDTO getTagDefinition(UUID id) {
        log.debug("Retrieving tag definition with id: {}", id);

        TagDefinition entity = tagDefinitionRepository.findById(id)
                .orElseThrow(() -> new TagDefinitionNotFoundException(id));

        return mapToDTO(entity);
    }

    /**
     * Lists all tag definitions ordered by tag_name, with optional entity type filter.
     *
     * @param entityType optional filter; when non-null only definitions applicable to this type are returned
     * @return list of tag definition DTOs ordered by tag_name ascending
     */
    public List<TagDefinitionDTO> listTagDefinitions(String entityType) {
        List<TagDefinition> definitions;

        if (entityType != null && !entityType.isBlank()) {
            log.debug("Listing tag definitions filtered by entityType: {}", entityType);
            definitions = tagDefinitionRepository.findByApplicableEntityType(entityType);
        } else {
            log.debug("Listing all tag definitions");
            definitions = tagDefinitionRepository.findAllByOrderByTagNameAsc();
        }

        return definitions.stream().map(this::mapToDTO).toList();
    }

    /**
     * Updates a tag definition's description, applicable entity types, and allowed values.
     * The tag_name is immutable and cannot be changed after creation.
     *
     * @param id the tag definition UUID
     * @param request the update request
     * @return DTO of the updated tag definition
     * @throws TagDefinitionNotFoundException if no definition exists with that ID
     * @throws IllegalArgumentException if any applicableEntityTypes value is not a valid EntityType
     */
    @Transactional
    public TagDefinitionDTO updateTagDefinition(UUID id, TagDefinitionUpdateRequest request) {
        log.debug("Updating tag definition with id: {}", id);

        TagDefinition entity = tagDefinitionRepository.findById(id)
                .orElseThrow(() -> new TagDefinitionNotFoundException(id));

        validateEntityTypes(request.applicableEntityTypes());

        entity.setTagDescription(request.tagDescription());
        entity.setApplicableEntityTypes(request.applicableEntityTypes());
        entity.setAllowedValues(request.allowedValues());

        TagDefinition updated = tagDefinitionRepository.save(entity);
        log.info("Updated tag definition '{}' (id: {})", updated.getTagName(), updated.getId());

        return mapToDTO(updated);
    }

    /**
     * Deletes a tag definition. Does not cascade to entity_tags.
     *
     * @param id the tag definition UUID
     * @throws TagDefinitionNotFoundException if no definition exists with that ID
     */
    @Transactional
    public void deleteTagDefinition(UUID id) {
        log.debug("Deleting tag definition with id: {}", id);

        TagDefinition entity = tagDefinitionRepository.findById(id)
                .orElseThrow(() -> new TagDefinitionNotFoundException(id));

        tagDefinitionRepository.delete(entity);
        log.info("Deleted tag definition '{}' (id: {})", entity.getTagName(), id);
    }

    /**
     * Finds a tag definition by tag name (case-insensitive).
     * Used by EntityTagService for validation during entity tag creation.
     *
     * @param tagName the tag name to look up
     * @return Optional containing the tag definition if found
     */
    public Optional<TagDefinition> findByTagName(String tagName) {
        return tagDefinitionRepository.findByTagNameIgnoreCase(tagName);
    }

    /**
     * Validates that all provided entity type strings are valid EntityType enum values.
     *
     * @param entityTypes the list of entity type strings to validate
     * @throws IllegalArgumentException if any value is not a valid EntityType
     */
    private void validateEntityTypes(List<String> entityTypes) {
        if (entityTypes == null) {
            return;
        }
        for (String type : entityTypes) {
            try {
                EntityType.valueOf(type);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        String.format("Invalid entity type: '%s'. Valid values are: %s",
                                type, List.of(EntityType.values())));
            }
        }
    }

    private TagDefinitionDTO mapToDTO(TagDefinition entity) {
        return new TagDefinitionDTO(
                entity.getId(),
                entity.getTagName(),
                entity.getTagDescription(),
                entity.getApplicableEntityTypes(),
                entity.getAllowedValues(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
