// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.dto.EntityTagCreateRequest;
import org.sifunity.broker.environment.dto.EntityTagDTO;
import org.sifunity.broker.environment.service.EntityTagService;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for entity tag management operations.
 * All endpoints are scoped under a solution.
 */
@Tag(name = "Internal API", description = "Internal management endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/tags")
public class EntityTagController {

    private static final Logger log = LoggerFactory.getLogger(EntityTagController.class);

    private final EntityTagService entityTagService;

    public EntityTagController(EntityTagService entityTagService) {
        this.entityTagService = entityTagService;
    }

    @PostMapping
    public ResponseEntity<EntityTagDTO> createTag(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @Valid @RequestBody EntityTagCreateRequest request) {
        log.info("Creating tag '{}' on {}/{} in solution {}",
                request.tagKey(), request.entityType(), request.entityId(), solutionId);
        EntityTagDTO created = entityTagService.createTag(domainId, solutionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<EntityTagDTO>> listTags(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestParam(required = false) EntityType entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String tagKey) {
        log.debug("Listing tags in solution {} (entityType={}, entityId={}, tagKey={})",
                solutionId, entityType, entityId, tagKey);
        List<EntityTagDTO> tags = entityTagService.listTags(domainId, solutionId, entityType, entityId, tagKey);
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/{entityType}/{entityId}/{tagKey}")
    public ResponseEntity<EntityTagDTO> getTag(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable EntityType entityType,
            @PathVariable UUID entityId,
            @PathVariable String tagKey) {
        log.debug("Getting tag '{}' on {}/{} in solution {}", tagKey, entityType, entityId, solutionId);
        EntityTagDTO tag = entityTagService.getTag(domainId, solutionId, entityType, entityId, tagKey);
        return ResponseEntity.ok(tag);
    }

    @DeleteMapping("/{entityType}/{entityId}/{tagKey}")
    public ResponseEntity<Void> deleteTag(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable EntityType entityType,
            @PathVariable UUID entityId,
            @PathVariable String tagKey) {
        log.info("Deleting tag '{}' on {}/{} in solution {}", tagKey, entityType, entityId, solutionId);
        entityTagService.deleteTag(domainId, solutionId, entityType, entityId, tagKey);
        return ResponseEntity.noContent().build();
    }
}
