// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.controller;

import jakarta.validation.Valid;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.common.dto.SifCollection;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.queueservice.dto.PurgeAcceptedResponse;
import org.sifunity.broker.queueservice.dto.QueueCreateRequest;
import org.sifunity.broker.queueservice.dto.QueueResponse;
import org.sifunity.broker.queueservice.dto.QueueStatisticsResponse;
import org.sifunity.broker.queueservice.service.QueueCleanupService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

/**
 * REST controller for queue management operations.
 * 
 * <p>Provides endpoints for creating, retrieving, deleting queues,
 * and querying queue statistics.</p>
 * 
 * <p>Requirements: 1.1, 1.3, 1.4, 1.5, 12.1, 12.2, 12.3, 12.4, 14.1, 14.2, 14.3, 14.4</p>
 */
@Tag(name = "SIF API", description = "SIF 3.7 specification endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/queues")
public class QueueController {
    
    private static final Logger log = LoggerFactory.getLogger(QueueController.class);
    
    private final QueueService queueService;
    private final QueueCleanupService queueCleanupService;
    private final String internalServiceToken;
    
    public QueueController(QueueService queueService,
                           QueueCleanupService queueCleanupService,
                           @Value("${broker.internal-service-token:}") String internalServiceToken) {
        this.queueService = queueService;
        this.queueCleanupService = queueCleanupService;
        this.internalServiceToken = internalServiceToken;
    }
    
    /**
     * Create a new queue.
     * 
     * <p>Enforces that the ownerId in the request matches the authenticated
     * environment's ID. Environments can only create queues for themselves.</p>
     * 
     * @param request the queue creation request
     * @param environmentId the authenticated environment ID from gateway header
     * @return the created queue DTO
     * 
     * <p>Requirements: 1.1, 14.1, 14.2, 14.3, 14.4</p>
     */
    @PostMapping("/queue")
    public ResponseEntity<?> createQueue(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @Valid @RequestBody QueueCreateRequest request,
            @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint,
            @RequestHeader(value = SifHeaders.X_SOLUTION_UUID, required = false) String solutionUuidHeader,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken) {
        
        // Validate wrapper key presence per SIF 3.7 envelope convention
        if (request.queue() == null) {
            SifErrorResponse error = new SifErrorResponse(
                    UUID.randomUUID().toString(), HttpStatus.BAD_REQUEST.value(),
                    "QUEUE_001", "Invalid request body",
                    "Request body must contain \"queue\" wrapper key per SIF 3.7 envelope convention.",
                    "BROKER", Instant.now(), null, null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
        }
        
        log.info("Creating queue: name={}, ownerId={}, authenticatedEnv={}, fingerprint={}, solutionUuid={}", 
            request.queue().name(), request.queue().ownerId(), environmentId, fingerprint, solutionUuidHeader);
        
        // Enforce ownership — fail-closed: deny if environmentId is absent or mismatched.
        // broker-services (fingerprint) bypasses this check only with a valid Internal_Service_Token.
        boolean isBrokerServices = "broker-services".equals(fingerprint) && isValidInternalToken(serviceToken);
        if (!isBrokerServices
                && (environmentId == null || !request.queue().ownerId().toString().equals(environmentId))) {
            log.warn("Ownership violation: environment {} attempted to create queue for {}", 
                environmentId, request.queue().ownerId());
            return forbiddenResponse(environmentId);
        }
        
        UUID solutionUuid = solutionUuidHeader != null ? UUID.fromString(solutionUuidHeader) : null;
        
        QueueDTO queue = queueService.createQueue(
            request.queue().name(),
            request.queue().ownerId(),
            request.queue().maxRetries(),
            request.queue().retryDelayMs(),
            request.queue().maxConcurrentConnections(),
            domainId,
            solutionId,
            solutionUuid
        );
        
        log.info("Queue created: id={}, name={}", queue.id(), queue.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(new QueueResponse(queue));
    }
    
    /**
     * Reject POST to the collection URL.
     * 
     * <p>SIF 3.7 requires POST to the singular resource URL (/queues/queue).
     * POST to the collection URL is not supported.</p>
     * 
     * <p>Requirements: 2.4</p>
     */
    @PostMapping
    public ResponseEntity<?> collectionPostNotAllowed(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestBody(required = false) String body) {
        log.warn("Rejected POST to collection URL /{}/{}/queues", domainId, solutionId);
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(), HttpStatus.METHOD_NOT_ALLOWED.value(),
                "QUEUE_005", "Method not allowed",
                "POST to queues collection is not supported. Use POST /queues/queue to create a queue.",
                "BROKER", Instant.now(), null, null);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Get a queue by ID.
     * 
     * @param queueId the queue ID
     * @return the queue DTO
     * 
     * <p>Requirements: 1.3, 14.1, 14.2, 14.3, 14.4</p>
     */
    @GetMapping("/{queueId}")
    public ResponseEntity<?> getQueue(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID queueId,
            @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken) {
        log.debug("Getting queue: queueId={}", queueId);
        
        QueueDTO queue = queueService.getQueue(queueId, domainId, solutionId);
        
        // Fail-closed: deny if environmentId is absent or mismatched
        boolean isBrokerServices = "broker-services".equals(fingerprint) && isValidInternalToken(serviceToken);
        if (!isBrokerServices
                && (environmentId == null || !queue.ownerId().toString().equals(environmentId))) {
            log.warn("Ownership violation: environment {} attempted to access queue {}", environmentId, queueId);
            return forbiddenResponse(environmentId);
        }
        
        return ResponseEntity.ok(new QueueResponse(queue));
    }
    
    /**
     * Get queues for the authenticated environment.
     * 
     * <p>Returns only queues owned by the calling environment. If the caller
     * has fingerprint "broker-services" (solution-wide visibility), returns all
     * queues in the solution. If no X-Environment-Id header, returns all queues.</p>
     * 
     * @param environmentId the authenticated environment ID from gateway header
     * @param fingerprint the environment fingerprint from gateway header (optional)
     * @return list of queue DTOs scoped to the caller
     * 
     * <p>Requirements: 1.4, 14.1, 14.2, 14.3, 14.4</p>
     */
    @GetMapping
    public ResponseEntity<?> getQueues(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint,
            @RequestHeader(value = SifHeaders.X_SOLUTION_UUID, required = false) String solutionUuidHeader,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken) {
        List<QueueDTO> queues;
        UUID solutionUuid = solutionUuidHeader != null ? UUID.fromString(solutionUuidHeader) : null;
        
        boolean isAdmin = GlobalAdminConstants.isGlobalAdmin(domainId, solutionId);
        boolean isBrokerServices = "broker-services".equals(fingerprint) && isValidInternalToken(serviceToken);
        
        if (isAdmin || isBrokerServices) {
            // Solution-wide visibility for admin or broker-services only
            log.debug("Getting all queues for solution: fingerprint={}, solutionUuid={}",
                    fingerprint, solutionUuid);
            queues = queueService.getAllQueues(domainId, solutionId, solutionUuid);
        } else if (environmentId != null) {
            // Non-admin caller: always scope to their own authenticated environment ID
            UUID ownerId = UUID.fromString(environmentId);
            log.debug("Getting queues for authenticated environment: {}", ownerId);
            queues = queueService.getQueuesByOwner(ownerId, domainId, solutionId);
        } else {
            // Fail-closed: deny if environmentId is absent
            log.warn("Ownership violation: missing X-Environment-Id for queue listing");
            return forbiddenResponse(null);
        }
        log.debug("Found {} queues", queues.size());
        return ResponseEntity.ok(SifCollection.of("queue", queues));
    }
    
    /**
     * Delete a queue (async).
     * Returns 202 Accepted immediately; cleanup runs in background.
     * 
     * @param queueId the queue ID
     * @return 202 Accepted with async cleanup status
     * 
     * <p>Requirements: 1.5, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 7.4, 14.1, 14.2, 14.3, 14.4</p>
     */
    @DeleteMapping("/{queueId}")
    public ResponseEntity<?> deleteQueue(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID queueId,
            @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken) {
        log.info("Deleting queue: queueId={}", queueId);
        
        QueueDTO queue = queueService.getQueue(queueId);
        // Fail-closed: deny if environmentId is absent or mismatched
        boolean isBrokerServices = "broker-services".equals(fingerprint) && isValidInternalToken(serviceToken);
        if (!isBrokerServices
                && (environmentId == null || !queue.ownerId().toString().equals(environmentId))) {
            log.warn("Ownership violation: environment {} attempted to delete queue {}", environmentId, queueId);
            return forbiddenResponse(environmentId);
        }
        
        // Always use the queue owner's environment ID for stream key construction.
        // The stream key includes the owner's environment ID, not the caller's.
        UUID ownerEnvId = queue.ownerId();
        queueCleanupService.submitDelete(queueId, domainId, solutionId, ownerEnvId);
        
        log.info("Queue delete initiated (async): queueId={}", queueId);
        return ResponseEntity.accepted().body(Map.of(
                "queueId", queueId,
                "status", "ACCEPTED",
                "message", "Queue delete initiated"
        ));
    }
    
    /**
     * Purge all messages from a queue (async).
     * Returns 202 Accepted immediately; cleanup runs in background.
     * 
     * @param queueId the queue ID
     * @return 202 Accepted with purge status
     * 
     * <p>Requirements: 8.1, 8.2, 8.3, 8.4, 9.1, 9.2, 9.3</p>
     */
    @DeleteMapping("/{queueId}/messages")
    public ResponseEntity<?> purgeQueue(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID queueId,
            @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken) {
        log.info("Purging queue: queueId={}", queueId);
        
        QueueDTO queue = queueService.getQueue(queueId);
        // Fail-closed: deny if environmentId is absent or mismatched
        boolean isBrokerServices = "broker-services".equals(fingerprint) && isValidInternalToken(serviceToken);
        if (!isBrokerServices
                && (environmentId == null || !queue.ownerId().toString().equals(environmentId))) {
            log.warn("Ownership violation: environment {} attempted to purge queue {}", environmentId, queueId);
            return forbiddenResponse(environmentId);
        }
        
        // Always use the queue owner's environment ID for stream key construction.
        // The stream key includes the owner's environment ID, not the caller's.
        UUID ownerEnvId = queue.ownerId();
        queueCleanupService.submitPurge(queueId, domainId, solutionId, ownerEnvId);
        
        log.info("Queue purge initiated (async): queueId={}", queueId);
        return ResponseEntity.accepted().body(new PurgeAcceptedResponse(
                queueId, "ACCEPTED", "Queue purge initiated"
        ));
    }
    
    /**
     * Get queue statistics.
     * 
     * @param queueId the queue ID
     * @return queue statistics
     * 
     * <p>Requirements: 12.1, 12.2, 12.3, 12.4, 14.1, 14.2, 14.3, 14.4</p>
     */
    @GetMapping("/{queueId}/stats")
    public ResponseEntity<?> getQueueStatistics(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID queueId,
            @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken) {
        log.debug("Getting queue statistics: queueId={}", queueId);
        
        QueueDTO queue = queueService.getQueue(queueId);
        // Fail-closed: deny if environmentId is absent or mismatched
        boolean isBrokerServices = "broker-services".equals(fingerprint) && isValidInternalToken(serviceToken);
        if (!isBrokerServices
                && (environmentId == null || !queue.ownerId().toString().equals(environmentId))) {
            log.warn("Ownership violation: environment {} attempted to access stats for queue {}", environmentId, queueId);
            return forbiddenResponse(environmentId);
        }
        
        QueueService.QueueStatistics stats = queueService.getQueueStatistics(queueId,
                domainId, solutionId,
                environmentId != null ? UUID.fromString(environmentId) : queue.ownerId());
        
        QueueStatisticsResponse response = new QueueStatisticsResponse(
            stats.queueId(),
            stats.pendingCount(),
            stats.deliveredCount(),
            stats.deadLetterCount(),
            stats.totalProcessed()
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Validates the presented internal service token using constant-time comparison.
     *
     * @param presentedToken the token from the X-Internal-Service-Token header
     * @return true if the token is valid, false otherwise
     */
    private boolean isValidInternalToken(String presentedToken) {
        if (internalServiceToken == null || internalServiceToken.isEmpty()
                || presentedToken == null || presentedToken.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                internalServiceToken.getBytes(StandardCharsets.UTF_8),
                presentedToken.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<?> forbiddenResponse(String environmentId) {
        String detail = environmentId == null
                ? "Missing X-Environment-Id header. Caller identity is required."
                : "Environment " + environmentId + " is not authorized to access this resource.";
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(), HttpStatus.FORBIDDEN.value(),
                "AUTHZ_002", "Ownership violation",
                detail,
                "BROKER", Instant.now(), null, null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(SifErrorResponseEnvelope.of(error));
    }
}
