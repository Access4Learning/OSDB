// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.dto.SifCollection;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.environment.dto.CredentialRotationRequest;
import org.sifunity.broker.environment.dto.CredentialRotationResponse;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.EnvironmentResponse;
import org.sifunity.broker.environment.dto.EnvironmentUpdateRequest;
import org.sifunity.broker.environment.dto.EnvironmentUserDTO;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.UserEnvironmentAccessService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for environment registration and management operations.
 *
 * <p>This controller handles HTTP requests for environment lifecycle operations:
 * <ul>
 *   <li>Environment registration (POST /environments) - no authentication required</li>
 *   <li>Environment retrieval by ID (GET /environments/{id})</li>
 *   <li>Environment listing with pagination and filtering (GET /environments)</li>
 *   <li>Environment deletion (DELETE /environments/{id})</li>
 * </ul>
 *
 * <p>All endpoints except registration require authentication through the API Gateway.
 * The registration endpoint is publicly accessible to allow new applications to
 * obtain credentials.</p>
 *
 * @see EnvironmentService
 * @see EnvironmentRegistrationRequest
 * @see SifEnvironmentRegistrationResponse
 * @see SifEnvironmentResponse
 */
@Tag(name = "SIF API", description = "SIF 3.7 specification endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/environments")
public class EnvironmentController {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentController.class);

    private final EnvironmentService environmentService;
    private final UserEnvironmentAccessService userEnvironmentAccessService;

    public EnvironmentController(EnvironmentService environmentService,
                                 UserEnvironmentAccessService userEnvironmentAccessService) {
        this.environmentService = environmentService;
        this.userEnvironmentAccessService = userEnvironmentAccessService;
    }

    /**
     * Registers a new environment with the broker.
     *
     * <p>This endpoint requires admin authentication. The gateway exempts this path
     * from its own authentication filter, but the environment service's AuthorizationFilter
     * enforces that only admin environments can create new environments.</p>
     *
     * <p>Unauthenticated self-registration into the global admin domain/solution is blocked.
     * An authenticated admin can create additional admin environments.</p>
     *
     * @param request the environment registration request containing application details
     * @return ResponseEntity containing the SIF 3.7 registration response with credentials
     */
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Environment registered; the response carries the session token and shared secret once",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifEnvironmentRegistrationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failure, or a malformed request body",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "The referenced solution does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PostMapping("/environment")
    public ResponseEntity<SifEnvironmentRegistrationResponse> registerEnvironment(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @Valid @RequestBody EnvironmentRegistrationRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        // D2 (2.7, 2.8): the body arrives wrapped under "environment", with consumerName and a
        // defaultZone object rather than applicationName and a flat defaultZoneId.
        EnvironmentRegistrationBody body = request.environment();

        log.info("Registering new environment: consumerName={}, type={}, solutionId={}, defaultZoneId={}",
                body.consumerName(), body.type(), solutionId, body.defaultZoneId());

        // Block unauthenticated self-registration into the global admin domain/solution.
        // An existing admin (set by AuthorizationFilter) may create additional admin environments.
        if (GlobalAdminConstants.isGlobalAdmin(domainId, solutionId)) {
            Object requester = httpRequest.getAttribute("requester");
            if (requester == null) {
                throw new IllegalArgumentException(
                        "Self-registration into the global admin domain/solution is not permitted");
            }
            // requester is present — AuthorizationFilter already verified they are admin
            log.info("Admin-initiated environment creation in global admin domain/solution by: {}",
                    ((org.sifunity.broker.environment.domain.Environment) requester).getId());
        }

        // Use solutionId from URL path, overriding any value in the request body.
        // Requirement 3.5 (preservation): the path value MUST win. A body-supplied solutionId would
        // otherwise let a caller register into a solution its URL did not name, so this rebuild is
        // load-bearing and must not be "simplified" into passing `request` straight through.
        EnvironmentRegistrationRequest effectiveRequest = new EnvironmentRegistrationRequest(
                new EnvironmentRegistrationBody(
                        body.consumerName(), body.type(), body.defaultZone(),
                        solutionId, body.applicationInfo()));

        SifEnvironmentRegistrationResponse response = environmentService.registerEnvironment(domainId, effectiveRequest);

        log.info("Environment registered successfully: id={}", response.environment().id());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Rejects POST to the environments collection URL with 405 Method Not Allowed.
     *
     * <p>SIF 3.7 requires POST to the singular resource URL (/environments/environment).
     * POST to the collection URL is not supported.</p>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "405", description = "POST to the collection URL is not supported; use POST /environments/environment",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PostMapping
    public ResponseEntity<?> collectionPostNotAllowed(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestBody(required = false) String body) {
        log.warn("Rejected POST to collection URL /{}/{}/environments", domainId, solutionId);
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(), HttpStatus.METHOD_NOT_ALLOWED.value(),
                "ENVIRONMENT_405", "Method not allowed",
                "POST to environments collection is not supported. Use POST /environments/environment to create an environment.",
                "BROKER", Instant.now(), null, null);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(SifErrorResponseEnvelope.of(error));
    }

    /**
     * Retrieves an environment by its unique identifier.
     *
     * <p>Returns the full SIF 3.7 environment object excluding the sharedSecret.</p>
     *
     * @param id the unique identifier of the environment
     * @return ResponseEntity containing the SIF 3.7 environment response
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The environment, wrapped under the environment key",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = EnvironmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Malformed environment id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Environment does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<EnvironmentResponse> getEnvironment(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id) {

        log.debug("Retrieving environment: id={}", id);

        SifEnvironmentResponse environment = environmentService.getEnvironment(id);

        log.debug("Environment retrieved successfully: id={}", id);

        return ResponseEntity.ok(new EnvironmentResponse(environment));
    }

    /**
     * Lists environments with optional type filtering and pagination.
     *
     * <p>Returns full SIF 3.7 environment objects excluding the sharedSecret.
     * Supports filtering by environment type, solution ID, and pagination.</p>
     *
     * @param type optional environment type filter (DIRECT, BROKERED, ADMIN)
     * @param solutionId optional solution ID filter
     * @param page page number (0-based, default: 0)
     * @param size page size (default: 20, max: 100)
     * @return ResponseEntity containing SIF 3.7 environment responses
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Environments as a SIF collection"),
        @ApiResponse(responseCode = "400", description = "Malformed type filter or paging parameters",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping
    public ResponseEntity<?> listEnvironments(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestParam(required = false) EnvironmentType type,
            @RequestParam(required = false) String solutionIdParam,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // NOTE: Only admin environments can reach this endpoint.
        // The environment-service AuthorizationFilter blocks non-admin callers
        // from the list endpoint (GET /environments without an ID).

        // If solutionId filter is provided via query param, use it; otherwise use path variable
        String effectiveSolutionId = solutionIdParam != null ? solutionIdParam : solutionId;
        if (effectiveSolutionId != null) {
            log.debug("Listing environments by solution: solutionId={}", effectiveSolutionId);
            return ResponseEntity.ok(SifCollection.of("environment",
                    environmentService.listEnvironmentsBySolution(domainId, effectiveSolutionId)));
        }

        // Validate pagination parameters
        if (page < 0) {
            throw new IllegalArgumentException("Page number must be non-negative");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }

        log.debug("Listing environments: type={}, page={}, size={}", type, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<SifEnvironmentResponse> environments = environmentService.listEnvironments(type, pageable);

        log.debug("Environments retrieved: totalElements={}, totalPages={}, currentPage={}",
                environments.getTotalElements(), environments.getTotalPages(), environments.getNumber());

        return ResponseEntity.ok(SifCollection.of("environment", environments.getContent()));
    }

    /**
     * Deletes an environment and all associated data.
     *
     * @param id the unique identifier of the environment to delete
     * @return ResponseEntity with no content
     */
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Environment deleted"),
        @ApiResponse(responseCode = "400", description = "Malformed environment id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Environment does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEnvironment(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id) {

        log.info("Deleting environment: id={}", id);

        environmentService.deleteEnvironment(id);

        log.info("Environment deleted successfully: id={}", id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Updates mutable fields on an existing environment.
     *
     * <p>Only non-null fields in the request body are applied. Immutable fields
     * (id, type, sessionToken, solutionId) cannot be changed.</p>
     *
     * @param id      the unique identifier of the environment to update
     * @param request the update request with fields to change
     * @return ResponseEntity containing the updated SIF 3.7 environment response
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The updated environment",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifEnvironmentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failure, or an invalid default zone",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Environment does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<SifEnvironmentResponse> updateEnvironment(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id,
            @Valid @RequestBody EnvironmentUpdateRequest request) {

        log.info("Updating environment: id={}", id);

        SifEnvironmentResponse updated = environmentService.updateEnvironment(id, request);

        log.info("Environment updated successfully: id={}", id);

        return ResponseEntity.ok(updated);
    }

    /**
     * Rotates credentials (session token and shared secret) for an environment.
     *
     * <p>Generates new session token and shared secret. The old credentials are
     * immediately invalidated. The new shared secret is returned ONLY in this
     * response — it cannot be retrieved again.</p>
     *
     * <p>Requires authentication with the environment's CURRENT credentials
     * (proving ownership before rotation).</p>
     *
     * @param domainId   the domain ID
     * @param solutionId the solution ID
     * @param id         the environment ID to rotate credentials for
     * @return 200 OK with new session token and shared secret
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "New credentials; returned once and not retrievable afterwards",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CredentialRotationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Malformed environment id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Environment does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PostMapping("/{id}/rotate-credentials")
    public ResponseEntity<CredentialRotationResponse> rotateCredentials(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id,
            @RequestBody(required = false) CredentialRotationRequest request) {

        log.info("Rotating credentials for environment: id={}", id);

        CredentialRotationResponse response = environmentService.rotateCredentials(id, request);

        log.info("Credentials rotated successfully for environment: id={}", id);

        return ResponseEntity.ok(response);
    }

    /**
     * Lists admin users with access to a specific environment.
     *
     * <p>Validates that the environment exists and belongs to the domain+solution
     * specified in the URL path. Returns 404 on mismatch to avoid leaking
     * information about environments in other scopes.</p>
     *
     * @param domainId   the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @param envId      the environment UUID
     * @return 200 OK with list of admin users who have access to this environment
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Admin users with access to the environment"),
        @ApiResponse(responseCode = "400", description = "Malformed environment id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Environment does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping("/{envId}/admin-users")
    public ResponseEntity<List<EnvironmentUserDTO>> listEnvironmentUsers(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID envId) {

        log.debug("Listing admin users for environment: envId={}, domainId={}, solutionId={}",
                envId, domainId, solutionId);

        environmentService.validateEnvironmentBelongsToDomainSolution(envId, domainId, solutionId);

        List<EnvironmentUserDTO> users = userEnvironmentAccessService.listUsersForEnvironment(envId);

        log.debug("Admin users listed for environment: envId={}, count={}", envId, users.size());

        return ResponseEntity.ok(users);
    }
}
