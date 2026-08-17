// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import org.sifunity.broker.environment.dto.XQueryTemplateUpdateBody;
import org.sifunity.broker.environment.dto.XQueryTemplateUpdateRequest;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.environment.dto.PermissionCheckRequest;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.XQueryTemplateCreateRequest;
import org.sifunity.broker.environment.dto.XQueryTemplateDTO;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.XQueryTemplateService;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

/**
 * REST controller for SIF 3.7 Named Query Template (xQueryTemplate) utility service.
 *
 * <p>Handles Named Query Template CRUD operations. Create and delete require
 * PROVIDE access to the "xQueryTemplates" service. Any authenticated environment
 * can list and retrieve templates.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST {@code /{domainId}/{solutionId}/xquerys} — create template (PROVIDE ACL required, 201)</li>
 *   <li>GET {@code /{domainId}/{solutionId}/xquerys} — list all templates in solution (200 or 204)</li>
 *   <li>GET {@code /{domainId}/{solutionId}/xquerys/{queryToken}} — get single template (200)</li>
 *   <li>DELETE {@code /{domainId}/{solutionId}/xquerys/{queryToken}} — delete template (PROVIDE ACL required, 204)</li>
 * </ul>
 *
 * <p><strong>Requirements:</strong> 1.1, 1.2, 1.3, 1.4, 1.5, 1.6</p>
 *
 * @see XQueryTemplateService
 * @see AuthorizationService
 */
@Tag(name = "SIF API", description = "SIF 3.7 specification endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/xquerys")
public class XQueryTemplateController {

    private static final Logger log = LoggerFactory.getLogger(XQueryTemplateController.class);

    private static final String XQUERY_TEMPLATES_SERVICE = "xQueryTemplates";

    private final XQueryTemplateService xQueryTemplateService;
    private final AuthorizationService authorizationService;

    public XQueryTemplateController(XQueryTemplateService xQueryTemplateService,
                                    AuthorizationService authorizationService) {
        this.xQueryTemplateService = xQueryTemplateService;
        this.authorizationService = authorizationService;
    }

    /**
     * Creates a new Named Query Template.
     *
     * <p>Requires PROVIDE access to the "xQueryTemplates" service. The requester's
     * environment ID is used as the owning provider of the template.</p>
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param request     template creation request body
     * @param httpRequest servlet request (carries requester attribute)
     * @return 201 Created with {@link XQueryTemplateResponse}
     *
     * <p><strong>Requirements:</strong> 1.1, 1.5, 1.6</p>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "XQuery template created"),
        @ApiResponse(responseCode = "400", description = "Validation failure, or a malformed request body",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "The referenced domain, solution or environment does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "409", description = "A template with that query token already exists",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PostMapping("/xquery")
    public ResponseEntity<XQueryTemplateResponse> createTemplate(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            // D6 / requirement 2.12: @Valid was missing entirely, so no constraint on this contract
            // was ever enforced. Without it the nested XQueryTemplateBody constraints are inert.
            @jakarta.validation.Valid @RequestBody XQueryTemplateCreateRequest request,
            HttpServletRequest httpRequest) {

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        validateProvideAccess(requester);

        log.info("Creating xQueryTemplate: domainId={}, solutionId={}, token={}, environmentId={}",
                domainId, solutionId, request.xquery().id(), requester.getId());

        XQueryTemplateDTO dto = xQueryTemplateService.create(domainId, solutionId, requester.getId(), request);

        log.info("xQueryTemplate created: token='{}'", dto.id());

        return ResponseEntity.status(HttpStatus.CREATED).body(new XQueryTemplateResponse(dto));
    }

    /**
     * Rejects POST to the xquerys collection URL with 405 Method Not Allowed.
     *
     * <p>SIF 3.7 requires POST to the singular resource URL (/xquerys/xquery).
     * POST to the collection URL is not supported.</p>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "405", description = "POST to the collection URL is not supported; use POST /xquerys/xquery",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PostMapping
    public ResponseEntity<?> collectionPostNotAllowed(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestBody(required = false) String body) {
        log.warn("Rejected POST to collection URL /{}/{}/xquerys", domainId, solutionId);
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(), HttpStatus.METHOD_NOT_ALLOWED.value(),
                "XQUERY_405", "Method not allowed",
                "POST to xquerys collection is not supported. Use POST /xquerys/xquery to create a template.",
                "BROKER", Instant.now(), null, null);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(SifErrorResponseEnvelope.of(error));
    }

    /**
     * Lists all Named Query Templates in a solution.
     *
     * <p>Returns 200 with collection, or 204 if no templates exist.</p>
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param httpRequest servlet request (carries requester attribute)
     * @return 200 OK with {@link XQueryTemplateCollectionResponse} or 204 No Content
     *
     * <p><strong>Requirements:</strong> 1.2</p>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "XQuery templates as a SIF collection"),
        @ApiResponse(responseCode = "204", description = "No templates are defined"),
        @ApiResponse(responseCode = "400", description = "Malformed path or query parameters",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping
    public ResponseEntity<XQueryTemplateCollectionResponse> listTemplates(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            HttpServletRequest httpRequest) {

        log.debug("Listing xQueryTemplates: domainId={}, solutionId={}", domainId, solutionId);

        List<XQueryTemplateDTO> templates = xQueryTemplateService.list(domainId, solutionId);

        if (templates.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        log.debug("Returning {} xQueryTemplates", templates.size());

        return ResponseEntity.ok(new XQueryTemplateCollectionResponse(
                new XQueryTemplateCollection(templates)));
    }

    /**
     * Retrieves a single Named Query Template by its query token.
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param queryToken  the query token identifier
     * @param httpRequest servlet request (carries requester attribute)
     * @return 200 OK with {@link XQueryTemplateResponse}
     *
     * <p><strong>Requirements:</strong> 1.3</p>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The XQuery template, wrapped under the xquery key"),
        @ApiResponse(responseCode = "400", description = "Malformed query token",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "No template with that query token",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping("/{queryToken}")
    public ResponseEntity<XQueryTemplateResponse> getTemplate(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String queryToken,
            HttpServletRequest httpRequest) {

        log.debug("Getting xQueryTemplate: token='{}', solutionId={}", queryToken, solutionId);

        XQueryTemplateDTO dto = xQueryTemplateService.getByToken(domainId, solutionId, queryToken);

        return ResponseEntity.ok(new XQueryTemplateResponse(dto));
    }

    /**
     * Deletes a Named Query Template.
     *
     * <p>Requires PROVIDE access to the "xQueryTemplates" service.</p>
     *
     * @param domainId    domain ID from path
     * @param solutionId  solution ID from path
     * @param queryToken  the query token identifier
     * @param httpRequest servlet request (carries requester attribute)
     * @return 204 No Content
     *
     * <p><strong>Requirements:</strong> 1.4, 1.6</p>
     */
    /**
     * Applies a partial update to a Named Query Template.
     *
     * <p>Wrapped under {@code xquery}, matching create. Every field is optional per the
     * specification's Update schema, so approving a template is this endpoint with a body carrying
     * only {@code status}:</p>
     *
     * <pre>{@code { "xquery": { "status": "APPROVED" } } }</pre>
     *
     * <p>The query token is not updatable — see {@link XQueryTemplateUpdateBody}.</p>
     *
     * @param domainId   the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @param queryToken the token identifying the template
     * @param request    the wrapped, all-optional update body
     * @return the updated template, wrapped under {@code xquery}
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The updated template",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = XQueryTemplateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Malformed body, or an invalid type or status value",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "403", description = "The caller may not modify this template",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "No template with that query token",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PutMapping("/{queryToken}")
    public ResponseEntity<XQueryTemplateResponse> updateTemplate(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String queryToken,
            @jakarta.validation.Valid @RequestBody XQueryTemplateUpdateRequest request,
            HttpServletRequest httpRequest) {

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        validateProvideAccess(requester);

        log.info("Updating xQueryTemplate: domainId={}, solutionId={}, token='{}', environmentId={}",
                domainId, solutionId, queryToken, requester.getId());

        XQueryTemplateDTO dto = xQueryTemplateService.update(domainId, solutionId, queryToken, request);

        log.info("xQueryTemplate updated: token='{}', status='{}'", dto.id(), dto.status());

        return ResponseEntity.ok(new XQueryTemplateResponse(dto));
    }

    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "XQuery template deleted"),
        @ApiResponse(responseCode = "400", description = "Malformed query token",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "403", description = "The caller may not delete this template",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "No template with that query token",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @DeleteMapping("/{queryToken}")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String queryToken,
            HttpServletRequest httpRequest) {

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        validateProvideAccess(requester);

        log.info("Deleting xQueryTemplate: token='{}', solutionId={}, environmentId={}",
                queryToken, solutionId, requester.getId());

        xQueryTemplateService.delete(domainId, solutionId, queryToken);

        log.info("xQueryTemplate deleted: token='{}'", queryToken);

        return ResponseEntity.noContent().build();
    }

    /**
     * Validates that the requester has PROVIDE access to the xQueryTemplates service.
     *
     * <p>Admin environments are always permitted. Non-admin environments must have an
     * APPROVED PROVIDE ACL entry for the "xQueryTemplates" service in their default zone.</p>
     *
     * @param requester the authenticated environment making the request
     * @throws SifBrokerException with 403 if access is denied
     *
     * <p><strong>Requirements:</strong> 1.6</p>
     */
    private void validateProvideAccess(Environment requester) {
        if (authorizationService.isAdmin(requester)) {
            return;
        }

        String zoneId = requester.getDefaultZoneId();
        if (zoneId == null || zoneId.isBlank()) {
            throw new SifBrokerException(
                    "XQUERY_003", HttpStatus.FORBIDDEN,
                    "Access denied: environment does not have PROVIDE access to xQueryTemplates service.");
        }

        PermissionCheckRequest permissionCheck = new PermissionCheckRequest(
                requester.getId(),
                XQUERY_TEMPLATES_SERVICE,
                zoneId,
                "DEFAULT",
                AccessType.PROVIDE
        );

        var response = authorizationService.checkPermission(permissionCheck);
        if (!response.granted()) {
            throw new SifBrokerException(
                    "XQUERY_003", HttpStatus.FORBIDDEN,
                    "Access denied: environment does not have PROVIDE access to xQueryTemplates service.");
        }
    }

    /**
     * SIF envelope response wrapper for a single xquery template.
     * Produces JSON: {@code {"xquery": {...}}}
     */
    public record XQueryTemplateResponse(XQueryTemplateDTO xquery) {}

    /**
     * SIF envelope response wrapper for a collection of xquery templates.
     * Produces JSON: {@code {"xquerys": {"xquery": [...]}}}
     */
    public record XQueryTemplateCollectionResponse(XQueryTemplateCollection xquerys) {}

    /**
     * Inner collection wrapper containing the list of xquery templates.
     */
    public record XQueryTemplateCollection(List<XQueryTemplateDTO> xquery) {}
}
