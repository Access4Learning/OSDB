// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.environment.dto.TagDefinitionCreateRequest;
import org.sifunity.broker.environment.dto.TagDefinitionDTO;
import org.sifunity.broker.environment.dto.TagDefinitionUpdateRequest;
import org.sifunity.broker.environment.service.TagDefinitionService;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for tag definition CRUD operations.
 *
 * <p>Authorization is enforced by the {@code AuthorizationFilter} which checks
 * that the requester is a Global Domain Admin (same pattern as /domains endpoints).</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST {@code /admin/tag-definitions} — create tag definition (201)</li>
 *   <li>GET {@code /admin/tag-definitions} — list tag definitions with optional entityType filter (200)</li>
 *   <li>GET {@code /admin/tag-definitions/{id}} — get tag definition by ID (200)</li>
 *   <li>PUT {@code /admin/tag-definitions/{id}} — update tag definition (200)</li>
 *   <li>DELETE {@code /admin/tag-definitions/{id}} — delete tag definition (204)</li>
 * </ul>
 *
 * <p><strong>Requirements:</strong> 8.1, 8.2, 8.3, 8.4, 8.5, 3.3, 3.4</p>
 *
 * @see TagDefinitionService
 */
@Tag(name = "Internal API", description = "Tag definition management endpoints")
@RestController
@RequestMapping("/admin/tag-definitions")
public class TagDefinitionController {

    private static final Logger log = LoggerFactory.getLogger(TagDefinitionController.class);

    private final TagDefinitionService tagDefinitionService;

    public TagDefinitionController(TagDefinitionService tagDefinitionService) {
        this.tagDefinitionService = tagDefinitionService;
    }

    /**
     * Creates a new tag definition.
     *
     * @param request the tag definition creation request
     * @return 201 Created with the created TagDefinitionDTO
     */
    @PostMapping
    public ResponseEntity<TagDefinitionDTO> createTagDefinition(
            @Valid @RequestBody TagDefinitionCreateRequest request) {
        log.info("Creating tag definition: tagName={}", request.tagName());

        TagDefinitionDTO created = tagDefinitionService.createTagDefinition(request);

        log.info("Tag definition created: id={}, tagName={}", created.id(), created.tagName());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists all tag definitions, optionally filtered by entity type.
     *
     * @param entityType optional filter to return only definitions applicable to this entity type
     * @return 200 OK with list of TagDefinitionDTOs
     */
    @GetMapping
    public ResponseEntity<List<TagDefinitionDTO>> listTagDefinitions(
            @RequestParam(required = false) String entityType) {
        log.debug("Listing tag definitions (entityType={})", entityType);

        List<TagDefinitionDTO> definitions = tagDefinitionService.listTagDefinitions(entityType);

        log.debug("Returning {} tag definitions", definitions.size());

        return ResponseEntity.ok(definitions);
    }

    /**
     * Retrieves a specific tag definition by ID.
     *
     * @param id the tag definition UUID
     * @return 200 OK with the TagDefinitionDTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<TagDefinitionDTO> getTagDefinition(@PathVariable UUID id) {
        log.debug("Getting tag definition: id={}", id);

        TagDefinitionDTO definition = tagDefinitionService.getTagDefinition(id);

        return ResponseEntity.ok(definition);
    }

    /**
     * Updates an existing tag definition.
     * Tag name is immutable and cannot be changed.
     *
     * @param id the tag definition UUID
     * @param request the update request
     * @return 200 OK with the updated TagDefinitionDTO
     */
    @PutMapping("/{id}")
    public ResponseEntity<TagDefinitionDTO> updateTagDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody TagDefinitionUpdateRequest request) {
        log.info("Updating tag definition: id={}", id);

        TagDefinitionDTO updated = tagDefinitionService.updateTagDefinition(id, request);

        log.info("Tag definition updated: id={}, tagName={}", updated.id(), updated.tagName());

        return ResponseEntity.ok(updated);
    }

    /**
     * Deletes a tag definition. Does not cascade to entity_tags.
     *
     * @param id the tag definition UUID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTagDefinition(@PathVariable UUID id) {
        log.info("Deleting tag definition: id={}", id);

        tagDefinitionService.deleteTagDefinition(id);

        log.info("Tag definition deleted: id={}", id);

        return ResponseEntity.noContent().build();
    }
}
