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
import org.sifunity.broker.environment.service.CodeSetsService;

import java.util.List;

/**
 * REST controller for SIF 3.7 Code Sets Registry Utility Service.
 *
 * <p>Handles code set CRUD operations with admin-only enforcement for
 * create, update, and delete. Any authenticated environment can query.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST {@code /codeSets/codeSet} — create code set (admin only, 201)</li>
 *   <li>GET {@code /codeSets} — list code sets with zone-scoping (200 or 204)</li>
 *   <li>GET {@code /codeSets/{id}} — get code set by ID with zone-scoping (200)</li>
 *   <li>PUT {@code /codeSets/{id}} — update code set (admin only, 200)</li>
 *   <li>DELETE {@code /codeSets/{id}} — delete code set (admin only, 204)</li>
 * </ul>
 *
 * <p><strong>Requirements:</strong> 1.1, 1.5, 2.1, 2.2, 3.1, 3.9, 3.10, 4.1, 4.2,
 * 4.4, 4.5, 5.1, 5.2, 5.3, 5.4, 8.9, 8.10, 10.1, 10.2, 10.3, 10.4, 11.6,
 * 12.1, 12.2, 12.3</p>
 *
 * @see CodeSetsService
 * @see AuthorizationService
 */
@Tag(name = "Internal API", description = "Internal management endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/codeSets")
public class CodeSetsController {

    private static final Logger log = LoggerFactory.getLogger(CodeSetsController.class);

    private static final String ENVIRONMENT_GLOBAL_ZONE_ID = "environment-global";

    private final CodeSetsService codeSetsService;
    private final AuthorizationService authorizationService;

    public CodeSetsController(CodeSetsService codeSetsService, AuthorizationService authorizationService) {
        this.codeSetsService = codeSetsService;
        this.authorizationService = authorizationService;
    }

    /**
     * Creates a new code set (admin only).
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param request     code set creation request body wrapped under {@code codeSet} key
     * @param httpRequest servlet request (carries requester attribute)
     * @return 201 Created with {@link CodeSetResponse}
     *
     * <p><strong>Requirements:</strong> 3.1, 3.9, 3.10, 8.9, 11.6, 12.1, 12.2, 12.3</p>
     */
    @PostMapping("/codeSet")
    public ResponseEntity<CodeSetResponse> createCodeSet(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @Valid @RequestBody CodeSetCreateRequest request,
            HttpServletRequest httpRequest) {

        validateServiceTypeHeader(httpRequest);

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        boolean isAdmin = authorizationService.isAdmin(requester);

        if (!isAdmin) {
            throw new SifBrokerException(
                    "CODESET_005", HttpStatus.FORBIDDEN,
                    "Only admin environments can create/update/delete code sets.");
        }

        log.info("Creating code set: domainId={}, solutionId={}, id={}, isAdmin={}",
                domainId, solutionId, request.codeSet().id(), isAdmin);

        CodeSetDTO dto = codeSetsService.createCodeSet(domainId, solutionId, request.codeSet());

        log.info("Code set created: id={}", dto.id());

        return ResponseEntity.status(HttpStatus.CREATED).body(new CodeSetResponse(dto));
    }

    /**
     * Lists code sets with zone-scoping.
     *
     * <p>Returns 200 with collection, or 204 if no code sets match.</p>
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param httpRequest servlet request (carries requester attribute)
     * @return 200 OK with {@link CodeSetCollectionResponse} or 204 No Content
     *
     * <p><strong>Requirements:</strong> 1.1, 1.5, 8.10, 10.2</p>
     */
    @GetMapping
    public ResponseEntity<CodeSetCollectionResponse> listCodeSets(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            HttpServletRequest httpRequest) {

        validateServiceTypeHeader(httpRequest);

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        String effectiveZoneId = resolveEffectiveZoneId(httpRequest, requester);

        log.debug("Listing code sets: domainId={}, solutionId={}, zoneId={}, environmentId={}",
                domainId, solutionId, effectiveZoneId, requester.getId());

        List<CodeSetDTO> codeSets = codeSetsService.listCodeSets(domainId, solutionId, effectiveZoneId);

        if (codeSets.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        log.debug("Returning {} code sets", codeSets.size());

        return ResponseEntity.ok(new CodeSetCollectionResponse(new CodeSetCollection(codeSets)));
    }

    /**
     * Retrieves a single code set by ID with zone-scoping.
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param id          code set identifier
     * @param httpRequest servlet request (carries requester attribute)
     * @return 200 OK with {@link CodeSetResponse}
     *
     * <p><strong>Requirements:</strong> 2.1, 2.2, 8.9, 10.2</p>
     */
    @GetMapping("/{id}")
    public ResponseEntity<CodeSetResponse> getCodeSet(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        validateServiceTypeHeader(httpRequest);

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        String effectiveZoneId = resolveEffectiveZoneId(httpRequest, requester);

        log.debug("Getting code set: id={}, zoneId={}, environmentId={}",
                id, effectiveZoneId, requester.getId());

        CodeSetDTO dto = codeSetsService.getCodeSetById(domainId, solutionId, id, effectiveZoneId);

        return ResponseEntity.ok(new CodeSetResponse(dto));
    }

    /**
     * Updates an existing code set (admin only, full replacement).
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param id          code set identifier
     * @param request     code set update request body wrapped under {@code codeSet} key
     * @param httpRequest servlet request (carries requester attribute)
     * @return 200 OK with {@link CodeSetResponse}
     *
     * <p><strong>Requirements:</strong> 4.1, 4.2, 4.4, 4.5, 8.9, 11.6, 12.1, 12.2, 12.3</p>
     */
    @PutMapping("/{id}")
    public ResponseEntity<CodeSetResponse> updateCodeSet(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String id,
            @Valid @RequestBody CodeSetCreateRequest request,
            HttpServletRequest httpRequest) {

        validateServiceTypeHeader(httpRequest);

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        boolean isAdmin = authorizationService.isAdmin(requester);

        if (!isAdmin) {
            throw new SifBrokerException(
                    "CODESET_005", HttpStatus.FORBIDDEN,
                    "Only admin environments can create/update/delete code sets.");
        }

        log.info("Updating code set: id={}, domainId={}, solutionId={}, isAdmin={}",
                id, domainId, solutionId, isAdmin);

        CodeSetDTO dto = codeSetsService.updateCodeSet(domainId, solutionId, id, request.codeSet());

        log.info("Code set updated: id={}", dto.id());

        return ResponseEntity.ok(new CodeSetResponse(dto));
    }

    /**
     * Deletes a code set (admin only).
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param id          code set identifier
     * @param httpRequest servlet request (carries requester attribute)
     * @return 204 No Content
     *
     * <p><strong>Requirements:</strong> 5.1, 5.2, 5.3, 5.4, 11.6, 12.1, 12.2, 12.3</p>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCodeSet(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        validateServiceTypeHeader(httpRequest);

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        boolean isAdmin = authorizationService.isAdmin(requester);

        if (!isAdmin) {
            throw new SifBrokerException(
                    "CODESET_005", HttpStatus.FORBIDDEN,
                    "Only admin environments can create/update/delete code sets.");
        }

        log.info("Deleting code set: id={}, domainId={}, solutionId={}, isAdmin={}",
                id, domainId, solutionId, isAdmin);

        codeSetsService.deleteCodeSet(domainId, solutionId, id);

        log.info("Code set deleted: id={}", id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Validates the {@code serviceType} header if present.
     *
     * <p>Code Sets is a UTILITY service — if the header is provided with any
     * other value, reject with 400. If absent, accept the request (lenient).</p>
     *
     * <p><strong>Requirements:</strong> 11.6, 12.1, 12.2, 12.3</p>
     */
    private void validateServiceTypeHeader(HttpServletRequest httpRequest) {
        String serviceType = httpRequest.getHeader("serviceType");
        if (serviceType != null && !serviceType.isBlank() && !"UTILITY".equalsIgnoreCase(serviceType)) {
            throw new SifBrokerException(
                    "CODESET_004", HttpStatus.BAD_REQUEST,
                    "Invalid serviceType header: '" + serviceType
                            + "'. The codeSets service is a UTILITY service. Set serviceType to UTILITY or omit the header.");
        }
    }

    /**
     * Resolves the effective zone ID for zone-scoped queries.
     *
     * <p>Priority: {@code zoneId} header → environment's default zone → {@code environment-global}.</p>
     *
     * <p><strong>Requirements:</strong> 1.4</p>
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
