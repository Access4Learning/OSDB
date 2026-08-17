// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.dto.SifCollection;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.dto.ContextCreateBody;
import org.sifunity.broker.environment.dto.ContextCreateRequest;
import org.sifunity.broker.environment.dto.ContextCreateRequestWrapper;
import org.sifunity.broker.environment.dto.ContextDTO;
import org.sifunity.broker.environment.dto.ContextResponse;
import org.sifunity.broker.environment.dto.ZoneCreateBody;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.dto.ZoneCreateRequestWrapper;
import org.sifunity.broker.environment.dto.ZoneDTO;
import org.sifunity.broker.environment.dto.ZoneResponse;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.List;

/**
 * REST controller for zone and context management operations.
 *
 * <p>This controller handles HTTP requests for zone and context lifecycle operations,
 * following the SIF 3.7 object-service pattern established by
 * {@code CodeSetsController}/{@code NamespacesController}:</p>
 * <ul>
 *   <li>Zone creation on the singular subpath (POST /zones/zone), wrapped body/response</li>
 *   <li>Zone listing (GET /zones) — SIF collection envelope</li>
 *   <li>Context creation on the singular subpath (POST /zones/{zoneId}/contexts/context)</li>
 *   <li>Context listing within a zone (GET /zones/{zoneId}/contexts) — SIF collection envelope</li>
 *   <li>Zone/context deletion (DELETE) — no request body / no Content-Type required</li>
 * </ul>
 *
 * <p>All endpoints require authentication through the API Gateway.
 * Zones and contexts provide logical organization for services and data routing.</p>
 *
 * <p>Request validation is performed using Bean Validation annotations on DTOs.
 * Admin-rejection and protected-resource errors are raised as {@link SifBrokerException}
 * so the {@link GlobalExceptionHandler} emits the broker-standard
 * {@code SifErrorResponse} ({@code { code, message, scope, timestamp }}).</p>
 *
 * <p><strong>Create endpoints.</strong> The only create endpoints are the SIF singular
 * subpaths ({@code POST /zones/zone}, {@code POST /zones/{zoneId}/contexts/context}) with the
 * request body wrapped under the object key and the identifier accepted only under {@code id},
 * matching the canonical {@code CodeSetsController} which exposes only
 * {@code POST /codeSets/codeSet}. There are no bare-collection create endpoints.</p>
 *
 * @see ZoneService
 * @see ZoneCreateRequestWrapper
 * @see ZoneResponse
 * @see ContextCreateRequestWrapper
 * @see ContextResponse
 */
@Tag(name = "SIF API", description = "SIF 3.7 specification endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/zones")
public class ZoneController {

    private static final Logger log = LoggerFactory.getLogger(ZoneController.class);

    private static final String PROTECTED_ZONE_ID = "environment-global";
    private static final String PROTECTED_CONTEXT_ID = "DEFAULT";

    /** Error code for admin-only rejection (403). */
    private static final String ADMIN_FORBIDDEN_CODE = "ZONE_004";
    /** Error code for protected-resource rejection (409). */
    private static final String PROTECTED_RESOURCE_CODE = "ZONE_005";

    private final ZoneService zoneService;

    public ZoneController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    /**
     * Creates a new zone (SIF object-service singular subpath).
     *
     * <p>Exposed on {@code POST /{domainId}/{solutionId}/zones/zone}. The request body is
     * wrapped under the {@code zone} object key ({@code { "zone": { "id": ..., "description":
     * ... } }}), and the SIF business identifier is accepted only under the canonical {@code id}
     * field. A body supplying the identifier only under the legacy {@code zoneId} alias
     * (omitting {@code id}) is rejected with {@code 400 Bad Request} by Bean Validation.</p>
     *
     * <p>Returns HTTP 201 Created with the created zone wrapped under the {@code zone} key.</p>
     * <p>Returns HTTP 403 Forbidden (SifErrorResponse) if the caller is not an admin.</p>
     * <p>Returns HTTP 409 Conflict if the zone ID already exists.</p>
     * <p>Returns HTTP 400 Bad Request if validation fails.</p>
     *
     * @param request the wrapped zone creation request ({@code { "zone": { "id", "description" } }})
     * @return ResponseEntity containing the created zone wrapped in a {@link ZoneResponse}
     */
    @PostMapping("/zone")
    public ResponseEntity<ZoneResponse> createZone(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @Valid @RequestBody ZoneCreateRequestWrapper request,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint) {
        ZoneCreateBody body = request.zone();
        log.info("Received request to create zone with ID: {} in solution: {}", body.id(), solutionId);

        requireAdmin(domainId, solutionId, fingerprint, "Only admin environments can create zones",
                "zone creation");

        // Solution identifier is populated from the URL path, not the request body.
        ZoneCreateRequest effectiveRequest = new ZoneCreateRequest(solutionId, body.id(), body.description());
        ZoneDTO createdZone = zoneService.createZone(domainId, effectiveRequest);

        log.info("Successfully created zone with ID: {} (UUID: {})",
                createdZone.zoneId(), createdZone.id());

        return ResponseEntity.status(HttpStatus.CREATED).body(new ZoneResponse(createdZone));
    }

    /**
     * Retrieves all zones in the system.
     * Optionally filters by solution ID if provided as a query parameter.
     *
     * <p>Returns a list of all zones currently registered in the system, wrapped in the SIF
     * collection envelope ({@code { "zones": { "zone": [ ... ] } }}). The list may be empty
     * if no zones have been created.</p>
     *
     * <p>Returns HTTP 200 OK with the list of zones.</p>
     *
     * @param solutionIdParam optional solution ID to filter by
     * @return ResponseEntity containing the SIF collection of zones
     * @see ZoneDTO
     */
    @GetMapping
    public ResponseEntity<SifCollection> listZones(
        @PathVariable String domainId,
        @PathVariable String solutionId,
        @RequestParam(required = false) String solutionIdParam
    ) {
        // Use solutionId from path variable by default
        String effectiveSolutionId = solutionIdParam != null ? solutionIdParam : solutionId;
        log.debug("Received request to list zones (solutionId filter: {})", effectiveSolutionId);

        List<ZoneDTO> zones = zoneService.listZonesBySolution(domainId, effectiveSolutionId);

        log.debug("Returning {} zones", zones.size());

        return ResponseEntity.ok(SifCollection.of("zone", zones));
    }

    /**
     * Creates a new context within the specified zone (SIF object-service singular subpath).
     *
     * <p>Exposed on {@code POST /{domainId}/{solutionId}/zones/{zoneId}/contexts/context}. The
     * request body is wrapped under the {@code context} object key ({@code { "context": { "id":
     * ..., "description": ... } }}), and the SIF business identifier is accepted only under the
     * canonical {@code id} field. A body supplying the identifier only under the legacy
     * {@code contextId} alias (omitting {@code id}) is rejected with {@code 400 Bad Request} by
     * Bean Validation.</p>
     *
     * <p>The parent zone must exist before creating a context within it.</p>
     *
     * <p>Returns HTTP 201 Created with the created context wrapped under the {@code context} key.</p>
     * <p>Returns HTTP 403 Forbidden (SifErrorResponse) if the caller is not an admin.</p>
     * <p>Returns HTTP 404 Not Found if the parent zone does not exist.</p>
     * <p>Returns HTTP 409 Conflict if the context ID already exists in the zone.</p>
     * <p>Returns HTTP 400 Bad Request if validation fails.</p>
     *
     * @param zoneId  the zone ID where the context should be created
     * @param request the wrapped context creation request ({@code { "context": { "id", "description" } }})
     * @return ResponseEntity containing the created context wrapped in a {@link ContextResponse}
     */
    @PostMapping("/{zoneId}/contexts/context")
    public ResponseEntity<ContextResponse> createContext(
        @PathVariable String domainId,
        @PathVariable String solutionId,
        @PathVariable String zoneId,
        @Valid @RequestBody ContextCreateRequestWrapper request,
        @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint
    ) {
        ContextCreateBody body = request.context();
        log.info("Received request to create context with ID: {} in zone: {}", body.id(), zoneId);

        requireAdmin(domainId, solutionId, fingerprint, "Only admin environments can create contexts",
                "context creation in zone " + zoneId);

        ContextCreateRequest effectiveRequest = new ContextCreateRequest(body.id(), body.description());
        ContextDTO createdContext = zoneService.createContext(domainId, solutionId, zoneId, effectiveRequest);

        log.info("Successfully created context with ID: {} in zone: {} (UUID: {})",
                createdContext.contextId(), createdContext.zoneId(), createdContext.id());

        return ResponseEntity.status(HttpStatus.CREATED).body(new ContextResponse(createdContext));
    }

    /**
     * Retrieves all contexts within the specified zone.
     *
     * <p>Returns a list of all contexts within the specified zone, wrapped in the SIF collection
     * envelope ({@code { "contexts": { "context": [ ... ] } }}). The list may be empty if no
     * contexts have been created in the zone.</p>
     *
     * <p>Note: This endpoint does not validate that the zone exists.
     * If the zone does not exist, an empty list will be returned.</p>
     *
     * <p>Returns HTTP 200 OK with the list of contexts.</p>
     *
     * @param zoneId the zone ID to retrieve contexts for
     * @return ResponseEntity containing the SIF collection of contexts
     * @see ContextDTO
     */
    @GetMapping("/{zoneId}/contexts")
    public ResponseEntity<SifCollection> listContexts(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String zoneId) {
        log.debug("Received request to list contexts for zone: {}", zoneId);

        List<ContextDTO> contexts = zoneService.listContexts(domainId, solutionId, zoneId);

        log.debug("Returning {} contexts for zone: {}", contexts.size(), zoneId);

        return ResponseEntity.ok(SifCollection.of("context", contexts));
    }

    /**
     * Deletes a zone by its zone ID.
     *
     * <p>Only admin environments (global admin or broker-services) can delete zones.
     * The system-managed zone "environment-global" cannot be deleted.</p>
     *
     * <p>Deletion cascades to all child contexts and their dependent data
     * (context_services, ACL entries, provider registrations, subscriptions).</p>
     *
     * <p>This endpoint carries no request body and requires no {@code Content-Type} header.</p>
     *
     * <p>Returns HTTP 204 No Content on successful deletion.</p>
     * <p>Returns HTTP 403 Forbidden (SifErrorResponse) if caller is not an admin.</p>
     * <p>Returns HTTP 404 Not Found if zone does not exist.</p>
     * <p>Returns HTTP 409 Conflict (SifErrorResponse) if zone is the protected "environment-global" zone.</p>
     *
     * @param domainId the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @param zoneId the zone ID to delete
     * @param fingerprint the X-Environment-Fingerprint header for admin identification
     * @return ResponseEntity with no content on success
     */
    @DeleteMapping("/{zoneId}")
    public ResponseEntity<Void> deleteZone(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String zoneId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint) {
        log.info("Received request to delete zone: {} in solution: {}", zoneId, solutionId);

        requireAdmin(domainId, solutionId, fingerprint, "Only admin environments can delete zones",
                "zone deletion");

        // Protected zone guard: environment-global cannot be deleted
        if (PROTECTED_ZONE_ID.equals(zoneId)) {
            log.warn("Attempted deletion of protected zone 'environment-global' in /{}/{}", domainId, solutionId);
            throw new SifBrokerException(PROTECTED_RESOURCE_CODE, HttpStatus.CONFLICT,
                    "The 'environment-global' zone is protected and cannot be deleted");
        }

        zoneService.deleteZone(domainId, solutionId, zoneId);

        log.info("Successfully deleted zone: {} in solution: {}", zoneId, solutionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes a context within the specified zone.
     *
     * <p>Only admin environments (global admin or broker-services) can delete contexts.
     * The "DEFAULT" context cannot be deleted.</p>
     *
     * <p>This endpoint carries no request body and requires no {@code Content-Type} header.</p>
     *
     * <p>Returns HTTP 204 No Content on successful deletion.</p>
     * <p>Returns HTTP 403 Forbidden (SifErrorResponse) if caller is not an admin.</p>
     * <p>Returns HTTP 404 Not Found if context does not exist.</p>
     * <p>Returns HTTP 409 Conflict (SifErrorResponse) if context is "DEFAULT".</p>
     */
    @DeleteMapping("/{zoneId}/contexts/{contextId}")
    public ResponseEntity<Void> deleteContext(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String zoneId,
            @PathVariable String contextId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint) {
        log.info("Received request to delete context: {} in zone: {}", contextId, zoneId);

        requireAdmin(domainId, solutionId, fingerprint, "Only admin environments can delete contexts",
                "context deletion in zone " + zoneId);

        // Protected context guard
        if (PROTECTED_CONTEXT_ID.equals(contextId)) {
            log.warn("Attempted deletion of protected context 'DEFAULT' in zone: {}", zoneId);
            throw new SifBrokerException(PROTECTED_RESOURCE_CODE, HttpStatus.CONFLICT,
                    "The 'DEFAULT' context is protected and cannot be deleted");
        }

        zoneService.deleteContext(domainId, solutionId, zoneId, contextId);

        log.info("Successfully deleted context: {} in zone: {}", contextId, zoneId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Enforces the admin-only guard shared by create and delete operations.
     *
     * <p>A caller is an admin when it targets the global-admin domain/solution
     * ({@link GlobalAdminConstants#isGlobalAdmin(String, String)}) or presents the
     * {@code broker-services} fingerprint. Non-admin callers are rejected with a
     * {@link SifBrokerException} (HTTP 403), which the {@link GlobalExceptionHandler}
     * renders as the standard {@code SifErrorResponse}. The guard decision logic is
     * identical to the prior inline checks; only the error payload shape changed.</p>
     *
     * @param domainId    domain ID from the path
     * @param solutionId  solution ID from the path
     * @param fingerprint the X-Environment-Fingerprint header (may be null)
     * @param message     the rejection message for non-admin callers
     * @param action      short description of the attempted action (for logging)
     */
    private void requireAdmin(String domainId, String solutionId, String fingerprint,
                              String message, String action) {
        if (!GlobalAdminConstants.isGlobalAdmin(domainId, solutionId)
                && !"broker-services".equals(fingerprint)) {
            log.warn("Non-admin environment attempted {} in /{}/{}", action, domainId, solutionId);
            throw new SifBrokerException(ADMIN_FORBIDDEN_CODE, HttpStatus.FORBIDDEN, message);
        }
    }
}
