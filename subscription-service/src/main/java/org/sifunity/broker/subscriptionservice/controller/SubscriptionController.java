// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.sifunity.broker.common.dto.SifCollection;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.subscriptionservice.dto.SubscriptionCreateRequest;
import org.sifunity.broker.subscriptionservice.dto.SubscriptionResponse;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for subscription management operations.
 * 
 * <p>This controller provides endpoints for:</p>
 * <ul>
 *   <li>Creating subscriptions</li>
 *   <li>Querying EVENT subscriptions with exact context matching</li>
 *   <li>Querying DELAYED_REQUEST subscriptions with exact matching</li>
 *   <li>Retrieving subscriptions by ID or queue</li>
 *   <li>Deleting subscriptions</li>
 * </ul>
 * 
 * <p>All endpoints follow RESTful conventions and return appropriate HTTP status codes.</p>
 * 
 * @see SubscriptionService
 */
@Tag(name = "SIF API", description = "SIF 3.7 specification endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/subscriptions")
public class SubscriptionController {
    
    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);
    
    private final SubscriptionService subscriptionService;
    
    /**
     * Constructs a SubscriptionController with the required service dependency.
     *
     * @param subscriptionService the subscription service
     */
    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }
    
    /**
     * Creates a new subscription.
     * 
     * <p>The request body must be wrapped in a {@code subscription} key per SIF 3.7:</p>
     * <pre>{"subscription": {"queueId": "...", "serviceName": "...", ...}}</pre>
     * 
     * @param request the subscription creation request wrapper
     * @return the created subscription with HTTP 201 Created status
     */
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Subscription created"),
        @ApiResponse(responseCode = "400", description = "Validation failure, or an aclEntryId that does not resolve (D7: not 404)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "403", description = "The caller holds no matching approved grant, or the access type does not match",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "The referenced queue does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "409", description = "An equivalent subscription already exists",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PostMapping("/subscription")
    public ResponseEntity<?> createSubscription(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @Valid @RequestBody SubscriptionCreateRequest request) {
        
        // Validate wrapper key presence per SIF 3.7 envelope convention
        if (request.subscription() == null) {
            SifErrorResponse error = new SifErrorResponse(
                    UUID.randomUUID().toString(), HttpStatus.BAD_REQUEST.value(),
                    "SUBSCRIPTION_001", "Invalid request body",
                    "Request body must contain \"subscription\" wrapper key per SIF 3.7 envelope convention.",
                    "BROKER", Instant.now(), null, null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(SifErrorResponseEnvelope.of(error));
        }
        
        SubscriptionDTO created = subscriptionService.createSubscription(domainId, solutionId, request.subscription());
        return ResponseEntity.status(HttpStatus.CREATED).body(new SubscriptionResponse(created));
    }
    
    /**
     * Rejects POST to the subscriptions collection URL with 405 Method Not Allowed.
     * 
     * <p>SIF 3.7 requires POST to the singular resource URL (/subscriptions/subscription).
     * POST to the collection URL is not supported.</p>
     * 
     * <p>Requirements: 2.9</p>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "405", description = "POST to the collection URL is not supported; use POST /subscriptions/subscription",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PostMapping
    public ResponseEntity<?> collectionPostNotAllowed(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestBody(required = false) String body) {
        log.warn("Rejected POST to collection URL /{}/{}/subscriptions", domainId, solutionId);
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(), HttpStatus.METHOD_NOT_ALLOWED.value(),
                "SUBSCRIPTION_005", "Method not allowed",
                "POST to subscriptions collection is not supported. Use POST /subscriptions/subscription to create a subscription.",
                "BROKER", Instant.now(), null, null);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Queries EVENT subscriptions matching the given service name and context.
     * 
     * <p>When contextUuid is provided, returns subscriptions with exact contextUuid match.
     * When contextUuid is omitted, returns all EVENT subscriptions for the service (used by
     * the alert publisher to find all alert subscribers regardless of context).</p>
     * 
     * @param serviceName the service name to match (required)
     * @param contextUuid the context UUID to match (optional — omit for all contexts)
     * @return list of matching EVENT subscriptions with HTTP 200 OK status
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event subscriptions for the caller"),
        @ApiResponse(responseCode = "400", description = "Malformed query parameters",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping("/events")
    public ResponseEntity<?> queryEventSubscriptions(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestParam String serviceName,
            @RequestParam(required = false) UUID contextUuid) {
        List<SubscriptionDTO> subscriptions;
        if (contextUuid != null) {
            subscriptions = subscriptionService.findEventSubscriptions(serviceName, contextUuid);
        } else {
            subscriptions = subscriptionService.findEventSubscriptionsByServiceName(serviceName);
        }
        return ResponseEntity.ok(SifCollection.of("subscription", subscriptions));
    }
    
    /**
     * Queries the DELAYED_REQUEST subscription matching the given service name and context.
     * 
     * <p>DELAYED_REQUEST subscriptions require exact matching.
     * Returns HTTP 404 Not Found if no matching subscription exists.</p>
     * 
     * @param serviceName the service name to match (required)
     * @param contextUuid the context UUID to match (required)
     * @return the matching DELAYED_REQUEST subscription with HTTP 200 OK status
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The delayed-request subscription for the caller"),
        @ApiResponse(responseCode = "400", description = "Malformed query parameters",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping("/delayed-requests")
    public ResponseEntity<SubscriptionDTO> queryDelayedRequestSubscription(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestParam String serviceName,
            @RequestParam(required = false) UUID contextUuid) {
        SubscriptionDTO subscription = subscriptionService
            .findDelayedRequestSubscription(serviceName, contextUuid);
        return ResponseEntity.ok(subscription);
    }
    
    /**
     * Retrieves a subscription by its unique identifier.
     * 
     * @param id the subscription ID
     * @return the subscription details with HTTP 200 OK status
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The subscription"),
        @ApiResponse(responseCode = "400", description = "Malformed subscription id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Subscription does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> getSubscription(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id) {
        SubscriptionDTO subscription = subscriptionService.getSubscription(id);
        return ResponseEntity.ok(new SubscriptionResponse(subscription));
    }
    
    /**
     * Lists subscriptions. When queueId is provided, filters by queue.
     * When queueId is omitted, returns all subscriptions for the requesting environment
     * (identified by the X-Environment-Id header injected by the gateway).
     * 
     * @param queueId the queue ID (optional)
     * @param environmentId the environment ID from the gateway header
     * @return list of subscriptions with HTTP 200 OK status
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Subscriptions as a SIF collection"),
        @ApiResponse(responseCode = "400", description = "Malformed query parameters",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping
    public ResponseEntity<?> listSubscriptions(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestParam(required = false) UUID queueId,
            @RequestHeader("X-Environment-Id") UUID environmentId) {
        List<SubscriptionDTO> subscriptions;
        if (queueId != null) {
            subscriptions = subscriptionService.findSubscriptionsByQueue(queueId);
        } else {
            subscriptions = subscriptionService.findSubscriptionsByEnvironment(environmentId);
        }
        return ResponseEntity.ok(SifCollection.of("subscription", subscriptions));
    }
    
    /**
     * Deletes a subscription by its unique identifier.
     * 
     * @param id the subscription ID
     * @return HTTP 204 No Content status on successful deletion
     */
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Subscription deleted"),
        @ApiResponse(responseCode = "400", description = "Malformed subscription id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Subscription does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubscription(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id) {
        subscriptionService.deleteSubscription(id);
        return ResponseEntity.noContent().build();
    }
}
