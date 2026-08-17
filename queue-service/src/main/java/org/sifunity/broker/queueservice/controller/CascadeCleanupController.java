// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sifunity.broker.queueservice.dto.CascadeCleanupRequest;
import org.sifunity.broker.queueservice.service.CascadeCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal-only REST endpoint for triggering cascade cleanup of Redis keys
 * and storage objects when a domain or solution is deleted.
 *
 * <p>Not exposed through the gateway — accessible only via internal service mesh.</p>
 */
@Tag(name = "Internal API", description = "Internal management endpoints")
@RestController
@RequestMapping("/internal/cascade-cleanup")
public class CascadeCleanupController {

    private static final Logger log = LoggerFactory.getLogger(CascadeCleanupController.class);

    private final CascadeCleanupService cascadeCleanupService;

    public CascadeCleanupController(CascadeCleanupService cascadeCleanupService) {
        this.cascadeCleanupService = cascadeCleanupService;
    }

    /**
     * Trigger cascade cleanup for a deleted domain or solution.
     *
     * <p>If {@code solutionId} is non-null, scopes cleanup to that solution.
     * Otherwise, cleans up the entire domain.</p>
     *
     * @param request the cleanup request containing domainId and optional solutionId
     * @return 202 Accepted (cleanup runs asynchronously)
     */
    @PostMapping
    public ResponseEntity<Void> triggerCleanup(@Valid @RequestBody CascadeCleanupRequest request) {
        if (request.solutionId() != null) {
            log.info("Cascade cleanup triggered: domain={}, solution={}", request.domainId(), request.solutionId());
            cascadeCleanupService.submitSolutionCleanup(request.domainId(), request.solutionId());
        } else {
            log.info("Cascade cleanup triggered: domain={}", request.domainId());
            cascadeCleanupService.submitDomainCleanup(request.domainId());
        }
        return ResponseEntity.accepted().build();
    }
}
