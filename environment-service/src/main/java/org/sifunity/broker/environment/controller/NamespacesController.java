// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.NamespacesService;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for SIF 3.7 Namespaces Registry Utility Service.
 *
 * <p>Handles namespace CRUD operations with admin-only enforcement for
 * create, update, and delete. Any authenticated environment can query.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST {@code /namespaces/namespace} — create namespace (admin only, 201)</li>
 *   <li>GET {@code /namespaces} — list namespaces with zone-scoping (200 or 204)</li>
 *   <li>GET {@code /namespaces/{id}} — get namespace by ID (200)</li>
 *   <li>PUT {@code /namespaces/{id}} — update namespace (admin only, 200)</li>
 *   <li>DELETE {@code /namespaces/{id}} — delete namespace (admin only, 204)</li>
 * </ul>
 *
 * <p><strong>Requirements:</strong> 1.1, 1.5, 2.1, 2.2, 3.1, 3.9, 4.1, 4.2,
 * 4.5, 5.1, 5.2, 5.3, 8.4, 8.5, 10.1, 10.2, 10.3, 10.4, 11.6,
 * 12.1, 12.2, 12.3</p>
 *
 * @see NamespacesService
 * @see AuthorizationService
 */
@Tag(name = "Internal API", description = "Internal management endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/namespaces")
public class NamespacesController {

    private static final Logger log = LoggerFactory.getLogger(NamespacesController.class);

    private static final String ENVIRONMENT_GLOBAL_ZONE_ID = "environment-global";

    private final NamespacesService namespacesService;
    private final AuthorizationService authorizationService;

    public NamespacesController(NamespacesService namespacesService, AuthorizationService authorizationService) {
        this.namespacesService = namespacesService;
        this.authorizationService = authorizationService;
    }

    /**
     * Creates a new namespace (admin only).
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param request     namespace creation request body wrapped under {@code namespace} key
     * @param httpRequest servlet request (carries requester attribute)
     * @return 201 Created with {@link NamespaceResponse}
     */
    @PostMapping("/namespace")
    public ResponseEntity<NamespaceResponse> createNamespace(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @Valid @RequestBody NamespaceCreateRequest request,
            HttpServletRequest httpRequest) {

        validateServiceTypeHeader(httpRequest);

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        boolean isAdmin = authorizationService.isAdmin(requester);

        if (!isAdmin) {
            throw new SifBrokerException(
                    "NAMESPACE_004", HttpStatus.FORBIDDEN,
                    "Only admin environments can create/update/delete namespaces.");
        }

        log.info("Creating namespace: domainId={}, solutionId={}, uri={}, isAdmin={}",
                domainId, solutionId, request.namespace().uri(), isAdmin);

        NamespaceDTO dto = namespacesService.createNamespace(domainId, solutionId, request.namespace());

        log.info("Namespace created: id={}", dto.id());

        return ResponseEntity.status(HttpStatus.CREATED).body(new NamespaceResponse(dto));
    }

    /**
     * Lists namespaces with zone-scoping.
     *
     * <p>Returns 200 with collection, or 204 if no namespaces match.</p>
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param httpRequest servlet request (carries requester attribute)
     * @return 200 OK with {@link NamespaceCollectionResponse} or 204 No Content
     */
    @GetMapping
    public ResponseEntity<NamespaceCollectionResponse> listNamespaces(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            HttpServletRequest httpRequest) {

        validateServiceTypeHeader(httpRequest);

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        String effectiveZoneId = resolveEffectiveZoneId(httpRequest, requester);

        log.debug("Listing namespaces: domainId={}, solutionId={}, zoneId={}, environmentId={}",
                domainId, solutionId, effectiveZoneId, requester.getId());

        List<NamespaceDTO> namespaces = namespacesService.listNamespaces(domainId, solutionId, effectiveZoneId);

        if (namespaces.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        log.debug("Returning {} namespaces", namespaces.size());

        return ResponseEntity.ok(new NamespaceCollectionResponse(new NamespaceCollection(namespaces)));
    }

    /**
     * Retrieves a single namespace by ID.
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param id          namespace UUID identifier
     * @param httpRequest servlet request (carries requester attribute)
     * @return 200 OK with {@link NamespaceResponse}
     */
    @GetMapping("/{id}")
    public ResponseEntity<NamespaceResponse> getNamespace(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        validateServiceTypeHeader(httpRequest);

        log.debug("Getting namespace: id={}", id);

        NamespaceDTO dto = namespacesService.getNamespaceById(domainId, solutionId, id);

        return ResponseEntity.ok(new NamespaceResponse(dto));
    }

    /**
     * Updates an existing namespace (admin only, full replacement).
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param id          namespace UUID identifier
     * @param request     namespace update request body wrapped under {@code namespace} key
     * @param httpRequest servlet request (carries requester attribute)
     * @return 200 OK with {@link NamespaceResponse}
     */
    @PutMapping("/{id}")
    public ResponseEntity<NamespaceResponse> updateNamespace(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id,
            @Valid @RequestBody NamespaceCreateRequest request,
            HttpServletRequest httpRequest) {

        validateServiceTypeHeader(httpRequest);

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        boolean isAdmin = authorizationService.isAdmin(requester);

        if (!isAdmin) {
            throw new SifBrokerException(
                    "NAMESPACE_004", HttpStatus.FORBIDDEN,
                    "Only admin environments can create/update/delete namespaces.");
        }

        log.info("Updating namespace: id={}, domainId={}, solutionId={}, isAdmin={}",
                id, domainId, solutionId, isAdmin);

        NamespaceDTO dto = namespacesService.updateNamespace(domainId, solutionId, id, request.namespace());

        log.info("Namespace updated: id={}", dto.id());

        return ResponseEntity.ok(new NamespaceResponse(dto));
    }

    /**
     * Deletes a namespace (admin only).
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param id          namespace UUID identifier
     * @param httpRequest servlet request (carries requester attribute)
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNamespace(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        validateServiceTypeHeader(httpRequest);

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        boolean isAdmin = authorizationService.isAdmin(requester);

        if (!isAdmin) {
            throw new SifBrokerException(
                    "NAMESPACE_004", HttpStatus.FORBIDDEN,
                    "Only admin environments can create/update/delete namespaces.");
        }

        log.info("Deleting namespace: id={}, domainId={}, solutionId={}, isAdmin={}",
                id, domainId, solutionId, isAdmin);

        namespacesService.deleteNamespace(domainId, solutionId, id);

        log.info("Namespace deleted: id={}", id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Validates the {@code serviceType} header if present.
     *
     * <p>Namespaces is a UTILITY service — if the header is provided with any
     * other value, reject with 400. If absent, accept the request (lenient).</p>
     */
    private void validateServiceTypeHeader(HttpServletRequest httpRequest) {
        String serviceType = httpRequest.getHeader("serviceType");
        if (serviceType != null && !serviceType.isBlank() && !"UTILITY".equalsIgnoreCase(serviceType)) {
            throw new SifBrokerException(
                    "NAMESPACE_003", HttpStatus.BAD_REQUEST,
                    "Invalid serviceType header: '" + serviceType
                            + "'. The namespaces service is a UTILITY service. Set serviceType to UTILITY or omit the header.");
        }
    }

    /**
     * Resolves the effective zone ID for zone-scoped queries.
     *
     * <p>Priority: {@code zoneId} header → environment's default zone → {@code environment-global}.</p>
     */
    private String resolveEffectiveZoneId(HttpServletRequest httpRequest, Environment requester) {
        String zoneIdHeader = httpRequest.getHeader("zoneId");
        if (zoneIdHeader != null && !zoneIdHeader.isBlank()) {
            return zoneIdHeader;
        }

        String defaultZone = requester.getDefaultZoneId();
        if (defaultZone != null && !defaultZone.isBlank()) {
            return defaultZone;
        }

        return ENVIRONMENT_GLOBAL_ZONE_ID;
    }
}
