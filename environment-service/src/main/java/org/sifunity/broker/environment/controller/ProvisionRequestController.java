// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.ProvisionRequestCreateDTO;
import org.sifunity.broker.environment.dto.ProvisionRequestResolveDTO;
import org.sifunity.broker.environment.dto.ProvisionRequestResponseDTO;
import org.sifunity.broker.environment.service.ProvisionRequestService;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for provision request operations.
 *
 * <p>Handles SIF 3.7 provisionRequest lifecycle:
 * <ul>
 *   <li>POST / — create provision request (consumer, self only)</li>
 *   <li>GET /{id} — query provision request (owner or admin)</li>
 *   <li>DELETE /{id} — delete provision request (owner or admin)</li>
 *   <li>PUT /{id} — admin resolution of provision request</li>
 * </ul>
 *
 * @see ProvisionRequestService
 */
@Tag(name = "SIF API", description = "SIF 3.7 specification endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/environments/{environmentId}/provisionRequests")
public class ProvisionRequestController {

    private static final Logger log = LoggerFactory.getLogger(ProvisionRequestController.class);

    private final ProvisionRequestService provisionRequestService;

    public ProvisionRequestController(ProvisionRequestService provisionRequestService) {
        this.provisionRequestService = provisionRequestService;
    }

    /**
     * Creates a new provision request for the given environment.
     *
     * @param domainId      the domain ID from the URL path
     * @param solutionId    the solution ID from the URL path
     * @param environmentId the environment ID from the URL path
     * @param payload       the provision request payload with rights assertions
     * @param request       the HTTP request (contains requester attribute)
     * @return HTTP 201 with the created provision request
     */
    @PostMapping("/provisionRequest")
    public ResponseEntity<ProvisionRequestResponseDTO> createProvisionRequest(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID environmentId,
            @RequestBody ProvisionRequestCreateDTO payload,
            HttpServletRequest request) {

        Environment requester = (Environment) request.getAttribute("requester");
        log.info("Creating provision request for environment: {}", environmentId);

        ProvisionRequestResponseDTO response = provisionRequestService
                .createProvisionRequest(environmentId, payload, requester);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Rejects POST to the provisionRequests collection URL with 405 Method Not Allowed.
     *
     * <p>SIF 3.7 requires POST to the singular resource URL
     * (/provisionRequests/provisionRequest). POST to the collection URL is not supported.</p>
     */
    @PostMapping
    public ResponseEntity<?> collectionPostNotAllowed(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID environmentId,
            @RequestBody(required = false) String body) {
        log.warn("Rejected POST to collection URL /{}/{}/environments/{}/provisionRequests",
                domainId, solutionId, environmentId);
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(), HttpStatus.METHOD_NOT_ALLOWED.value(),
                "PROVISION_REQUEST_405", "Method not allowed",
                "POST to provisionRequests collection is not supported. Use POST /provisionRequests/provisionRequest to create a provision request.",
                "BROKER", Instant.now(), null, null);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(SifErrorResponseEnvelope.of(error));
    }

    /**
     * Queries a provision request by ID.
     *
     * <p>Returns HTTP 200 if resolved, HTTP 202 if still pending.</p>
     *
     * @param domainId      the domain ID from the URL path
     * @param solutionId    the solution ID from the URL path
     * @param environmentId the environment ID from the URL path
     * @param id            the provision request ID
     * @param request       the HTTP request (contains requester attribute)
     * @return HTTP 200 with resolved request, or HTTP 202 if pending
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProvisionRequestResponseDTO> getProvisionRequest(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID environmentId,
            @PathVariable UUID id,
            HttpServletRequest request) {

        Environment requester = (Environment) request.getAttribute("requester");
        log.debug("Querying provision request: {}", id);

        ProvisionRequestResponseDTO response = provisionRequestService
                .getProvisionRequest(id, requester);

        if (response.completionStatus() == null) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a provision request by ID.
     *
     * @param domainId      the domain ID from the URL path
     * @param solutionId    the solution ID from the URL path
     * @param environmentId the environment ID from the URL path
     * @param id            the provision request ID
     * @param request       the HTTP request (contains requester attribute)
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProvisionRequest(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID environmentId,
            @PathVariable UUID id,
            HttpServletRequest request) {

        Environment requester = (Environment) request.getAttribute("requester");
        log.info("Deleting provision request: {}", id);

        provisionRequestService.deleteProvisionRequest(id, requester);

        return ResponseEntity.noContent().build();
    }

    /**
     * Resolves a provision request (admin only).
     *
     * @param domainId      the domain ID from the URL path
     * @param solutionId    the solution ID from the URL path
     * @param environmentId the environment ID from the URL path
     * @param id            the provision request ID
     * @param payload       the resolved payload with APPROVED/REJECTED values
     * @param request       the HTTP request (contains requester attribute)
     * @return HTTP 200 with the resolved provision request
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProvisionRequestResponseDTO> resolveProvisionRequest(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID environmentId,
            @PathVariable UUID id,
            @RequestBody ProvisionRequestResolveDTO payload,
            HttpServletRequest request) {

        Environment requester = (Environment) request.getAttribute("requester");
        log.info("Resolving provision request: {}", id);

        ProvisionRequestResponseDTO response = provisionRequestService
                .resolveProvisionRequest(id, payload, requester);

        return ResponseEntity.ok(response);
    }
}
