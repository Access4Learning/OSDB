// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.dto.EntityTagDTO;
import org.sifunity.broker.environment.exception.EntityTagNotFoundException;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.service.EntityTagService;

import java.util.UUID;

/**
 * Internal REST controller for entity tag lookups.
 *
 * <p>This controller provides an internal API used by connector services
 * (requests-connector, responses-connector) to query entity tags for
 * feature toggles such as SIF_LEGACY_MATRIX_PARAMS.</p>
 *
 * <p><strong>Security:</strong> This endpoint is under the /internal/ prefix
 * and is blocked from external access by the gateway's InternalApiBlockingFilter.
 * No additional authentication is required for internal service-to-service calls.</p>
 */
@Tag(name = "Internal API", description = "Internal management endpoints")
@RestController
@RequestMapping("/internal/{domainId}/{solutionId}/entity-tags")
public class InternalEntityTagController {

    private static final Logger log = LoggerFactory.getLogger(InternalEntityTagController.class);

    private final EntityTagService entityTagService;

    public InternalEntityTagController(EntityTagService entityTagService) {
        this.entityTagService = entityTagService;
    }

    /**
     * Looks up an entity tag value by entity type, entity ID, and tag key.
     *
     * <p>Used by connectors to check per-environment feature flags (e.g.,
     * SIF_LEGACY_MATRIX_PARAMS) for outbound message formatting decisions.</p>
     *
     * @param domainId the domain ID (path context, not used for lookup)
     * @param solutionId the human-readable solution ID
     * @param entityType the entity type (e.g., ENVIRONMENT)
     * @param entityId the entity UUID
     * @param tagKey the tag key to look up
     * @return 200 with tag value as plain string, or 404 if not found
     */
    @GetMapping
    public ResponseEntity<String> getEntityTagValue(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestParam EntityType entityType,
            @RequestParam UUID entityId,
            @RequestParam String tagKey) {

        log.debug("Internal entity tag lookup: solutionId={}, entityType={}, entityId={}, tagKey={}",
                solutionId, entityType, entityId, tagKey);

        try {
            EntityTagDTO tag = entityTagService.getTag(domainId, solutionId, entityType, entityId, tagKey);
            log.debug("Entity tag found: value={}", tag.tagValue());
            return ResponseEntity.ok(tag.tagValue());
        } catch (EntityTagNotFoundException | SolutionNotFoundException ex) {
            log.debug("Entity tag not found: solutionId={}, entityType={}, entityId={}, tagKey={}",
                    solutionId, entityType, entityId, tagKey);
            return ResponseEntity.notFound().build();
        }
    }
}
