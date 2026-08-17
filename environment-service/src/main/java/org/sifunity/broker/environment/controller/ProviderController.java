// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.environment.dto.AvailableProviderOptionDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.dto.ProviderResponse;
import org.sifunity.broker.environment.dto.ProviderUpdateBody;
import org.sifunity.broker.environment.dto.ProviderUpdateRequest;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.ProviderService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SifCollection;

/**
 * REST controller for provider service registration management operations.
 * 
 * <p>This controller handles HTTP requests for provider registry lifecycle operations:
 * <ul>
 *   <li>Provider service registration (POST /providers/provider)</li>
 *   <li>Provider service lookup by service/zone/context (GET /providers/lookup)</li>
 *   <li>Provider registration listing by environment (GET /providers?environmentId=...)</li>
 *   <li>Provider registration deletion (DELETE /providers/{id})</li>
 * </ul>
 * 
 * <p>All endpoints require authentication through the API Gateway.
 * Provider registrations enable service discovery and routing by recording
 * which environments provide which services in specific zones and contexts.</p>
 * 
 * <p>Request validation is performed using Bean Validation annotations on DTOs.
 * Business logic validation and error handling is delegated to the service layer.</p>
 * 
 * @see ProviderService
 * @see ProviderRegistrationRequest
 * @see ProviderRegistrationDTO
 */
@Tag(name = "SIF API", description = "SIF 3.7 specification endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/providers")
public class ProviderController {
    
    private static final Logger log = LoggerFactory.getLogger(ProviderController.class);
    
    private final ProviderService providerService;
    private final SolutionRepository solutionRepository;
    private final DomainRepository domainRepository;

    public ProviderController(ProviderService providerService, SolutionRepository solutionRepository,
                             DomainRepository domainRepository) {
        this.providerService = providerService;
        this.solutionRepository = solutionRepository;
        this.domainRepository = domainRepository;
    }
    
    /**
     * Registers a new provider service.
     * 
     * <p>Creates a provider registration that records that an environment is providing
     * a specific service identified by its context_service UUID. The combination of
     * environment and context_service must be unique — only one provider registration
     * per environment+context_service pair.</p>
     * 
     * <p>The environment must exist and be of type BROKERED before registering services.
     * The endpoint must be a valid URL and at least one supported request type is required.</p>
     * 
     * <p>The request accepts a {@code contextServiceUuid} that identifies the zone/context/service
     * combination, replacing the previous serviceName/zoneId/contextId string fields.
     * The response includes {@code consumerName} from the environment, replacing the
     * former {@code providerName} field.</p>
     * 
     * @param domainId the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @param request the provider registration request containing environmentId, contextServiceUuid,
     *                endpoint, serviceType, and supportedRequestTypes
     * @return ResponseEntity containing the created provider registration details with consumerName
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>201 Created - Provider registration successfully created</li>
     *   <li>400 Bad Request - Invalid request data (validation errors, non-BROKERED environment)</li>
     *   <li>404 Not Found - Environment or context_service does not exist</li>
     *   <li>409 Conflict - Provider already registered for this environment+context_service</li>
     *   <li>500 Internal Server Error - Unexpected server error</li>
     * </ul>
     * 
     * <p><strong>Requirements:</strong> 2.3, 2.4</p>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Provider registration created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProviderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failure, or the environment is not BROKERED",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Environment or context service does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "409", description = "A provider is already registered for this environment and service",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PostMapping("/provider")
    public ResponseEntity<ProviderResponse> registerProvider(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @Valid @RequestBody ProviderRegistrationRequest request) {
        
        log.info("Registering provider: environmentId={}, contextServiceUuid={}, serviceName={}, zoneId={}, contextId={}, endpoint={}, serviceType={}, supportedRequestTypes={}",
                request.provider().environmentId(), request.provider().contextServiceUuid(),
                request.provider().serviceName(), request.provider().zoneId(), request.provider().contextId(),
                ProtocolDTO.locationOf(request.provider().endPoint()), request.provider().serviceType(),
                request.provider().supportedRequestTypes());
        
        ProviderRegistrationDTO providerRegistration = providerService.registerProvider(domainId, solutionId, request);
        
        log.info("Provider registration created successfully: id={}, environmentId={}, serviceName={}, endpoint={}, consumerName={}", 
                providerRegistration.id(), providerRegistration.environmentId(), 
                providerRegistration.serviceName(), ProtocolDTO.locationOf(providerRegistration.endPoint()),
                providerRegistration.providerName());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(new ProviderResponse(providerRegistration));
    }

    /**
     * Rejects POST to the providers collection URL with 405 Method Not Allowed.
     *
     * <p>SIF 3.7 requires POST to the singular resource URL (/providers/provider).
     * POST to the collection URL is not supported.</p>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "405", description = "POST to the collection URL is not supported; use POST /providers/provider",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PostMapping
    public ResponseEntity<?> collectionPostNotAllowed(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestBody(required = false) String body) {
        log.warn("Rejected POST to collection URL /{}/{}/providers", domainId, solutionId);
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(), HttpStatus.METHOD_NOT_ALLOWED.value(),
                "PROVIDER_405", "Method not allowed",
                "POST to providers collection is not supported. Use POST /providers/provider to register a provider.",
                "BROKER", Instant.now(), null, null);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(SifErrorResponseEnvelope.of(error));
    }
    
    /**
     * Returns available provider registration options for an environment.
     *
     * <p>Computes the set difference between PROVIDE/APPROVED ACL entries and existing
     * provider registrations for the environment. Returns the available context_services
     * with their zone/context/service/serviceType for cascading dropdown population.</p>
     *
     * @param environmentId the environment ID to get available options for
     * @return ResponseEntity containing the list of available provider options (may be empty)
     *
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>200 OK - Available options returned (may be empty list)</li>
     *   <li>400 Bad Request - Invalid environment ID format</li>
     *   <li>500 Internal Server Error - Unexpected server error</li>
     * </ul>
     *
     * <p><strong>Requirements:</strong> 4.1, 4.2, 4.3</p>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Available registration options for the environment",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AvailableProviderOptionDTO.class))),
        @ApiResponse(responseCode = "400", description = "Missing or malformed environmentId",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping("/available-options")
    public ResponseEntity<List<AvailableProviderOptionDTO>> getAvailableOptions(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestParam UUID environmentId) {
        
        log.debug("Getting available provider options for environment: {}", environmentId);
        
        List<AvailableProviderOptionDTO> options = providerService.getAvailableOptions(environmentId);
        
        log.debug("Found {} available provider options for environment: {}", options.size(), environmentId);
        
        return ResponseEntity.ok(options);
    }
    
    /**
     * Looks up a provider registration by service name, zone, and context.
     * 
     * <p>This endpoint is the primary mechanism for service discovery. It locates
     * the provider that can handle requests for a specific service within a given
     * zone and context combination.</p>
     * 
     * <p>All parameters are required except contextId, which can be omitted if the
     * lookup is for the entire zone without a specific context.</p>
     * 
     * @param serviceName the service name to search for (required)
     * @param zoneId the zone ID to search for (required)
     * @param contextId the context ID to search for (optional)
     * @return ResponseEntity containing the provider registration if found, 404 if not found
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>200 OK - Provider registration found and returned</li>
     *   <li>400 Bad Request - Invalid service name or zone ID format</li>
     *   <li>404 Not Found - No provider found for the specified service/zone/context</li>
     *   <li>500 Internal Server Error - Unexpected server error</li>
     * </ul>
     * 
     * <p><strong>Requirements:</strong> 5.6</p>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The matching provider registration",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProviderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Missing or malformed lookup parameters",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "No provider is registered for that service, zone and context",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping("/lookup")
    public ResponseEntity<ProviderResponse> lookupProvider(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestParam String serviceName,
            @RequestParam String zoneId,
            @RequestParam(required = false) String contextId) {
        
        log.debug("Looking up provider: serviceName={}, zoneId={}, contextId={}", 
                serviceName, zoneId, contextId);
        
        UUID domainUuid = domainRepository.findByDomainId(domainId)
                .map(d -> d.getId())
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainId));
        UUID solutionUuid = solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .map(s -> s.getId())
                .orElseThrow(() -> new IllegalArgumentException("Solution not found: " + solutionId));

        Optional<ProviderRegistrationDTO> providerRegistration = providerService.lookupProvider(
                solutionUuid, serviceName, zoneId, contextId);
        
        if (providerRegistration.isPresent()) {
            log.debug("Provider found: id={}, endpoint={}", 
                    providerRegistration.get().id(), ProtocolDTO.locationOf(providerRegistration.get().endPoint()));
            return ResponseEntity.ok(new ProviderResponse(providerRegistration.get()));
        } else {
            log.debug("No provider found for serviceName={}, zoneId={}, contextId={}", 
                    serviceName, zoneId, contextId);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Lists all provider registrations for a specific environment.
     * 
     * <p>Returns all provider registrations associated with the specified environment,
     * regardless of their service type or zone. This is used for management and
     * listing operations to see all services provided by an environment.</p>
     * 
     * <p>The environmentId parameter is required and must be a valid UUID.</p>
     * 
     * <p>D1: the collection is returned in the SIF envelope form
     * {@code {"providers": {"provider": [ ... ]}}} rather than as a bare JSON array, matching the
     * convention already used by domains, solutions, zones, environments and admin users.</p>
     *
     * @param environmentId the environment ID to retrieve provider registrations for
     * @return ResponseEntity containing the wrapped provider collection (empty collection if none found)
     *
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>200 OK - Provider registrations returned (may be empty)</li>
     *   <li>400 Bad Request - Invalid environment ID format</li>
     *   <li>500 Internal Server Error - Unexpected server error</li>
     * </ul>
     *
     * <p><strong>Requirements:</strong> 5.7, 3.1</p>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Provider registrations for the environment, as a SIF collection",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifCollection.class))),
        @ApiResponse(responseCode = "400", description = "Missing or malformed environmentId",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping
    public ResponseEntity<SifCollection> listProviderRegistrations(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestParam UUID environmentId) {

        log.debug("Listing provider registrations for environment: {}", environmentId);

        List<ProviderRegistrationDTO> providerRegistrations = providerService.listProviderRegistrations(environmentId);

        log.debug("Found {} provider registrations for environment: {}",
                providerRegistrations.size(), environmentId);

        return ResponseEntity.ok(SifCollection.of("provider", providerRegistrations));
    }
    
    /**
     * Updates a provider registration's endpoint and supported request types.
     * 
     * <p>Only the endpoint URL and supportedRequestTypes can be updated after creation.
     * The environment, contextService, and serviceType remain immutable.</p>
     * 
     * @param domainId the domain ID from the URL path
     * @param solutionId the solution ID from the URL path
     * @param id the ID of the provider registration to update
     * <p>D1: the updated registration is returned in the {@code provider} envelope, the same shape
     * {@code POST} and the single-item {@code GET} already use — previously this endpoint alone
     * returned a bare DTO.</p>
     *
     * @param request the update request containing endpoint and supportedRequestTypes
     * @return ResponseEntity containing the updated provider registration in its envelope
     *
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>200 OK - Provider registration successfully updated</li>
     *   <li>400 Bad Request - Invalid request data (validation errors)</li>
     *   <li>404 Not Found - Provider registration does not exist</li>
     *   <li>500 Internal Server Error - Unexpected server error</li>
     * </ul>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The updated provider registration",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProviderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failure, or the endpoint is not a valid http/https URL",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Provider registration does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<ProviderResponse> updateProviderRegistration(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id,
            @Valid @RequestBody ProviderUpdateRequest request) {
        
        ProviderUpdateBody body = request.provider();

        log.info("Updating provider registration: id={}, endpoint={}, supportedRequestTypes={}",
                id, ProtocolDTO.locationOf(body.endPoint()), body.supportedRequestTypes());

        // The service layer keeps its flat signature: it needs the endpoint URL and the operation list,
        // not the wire envelope. Unwrapping here keeps the envelope a transport concern.
        ProviderRegistrationDTO updated = providerService.updateProvider(
                domainId, solutionId, id,
                ProtocolDTO.locationOf(body.endPoint()),
                body.supportedRequestTypes() == null
                        ? null
                        : body.supportedRequestTypes().supportedRequestType());
        
        log.info("Provider registration updated successfully: id={}", id);

        return ResponseEntity.ok(new ProviderResponse(updated));
    }
    
    /**
     * Deletes a provider registration.
     * 
     * <p>Removes the specified provider registration from the system. This operation
     * cannot be undone and will make the service unavailable for routing. The service
     * will no longer be discoverable through the lookup endpoint.</p>
     * 
     * <p>The provider registration ID must be a valid UUID that exists in the system.</p>
     * 
     * @param id the ID of the provider registration to delete
     * @return ResponseEntity with no content on successful deletion
     * 
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>204 No Content - Provider registration successfully deleted</li>
     *   <li>400 Bad Request - Invalid provider registration ID format</li>
     *   <li>404 Not Found - Provider registration does not exist</li>
     *   <li>500 Internal Server Error - Unexpected server error</li>
     * </ul>
     * 
     * <p><strong>Requirements:</strong> 5.8</p>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Provider registration deleted"),
        @ApiResponse(responseCode = "400", description = "Malformed id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Provider registration does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProviderRegistration(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id) {
        
        log.info("Deleting provider registration: {}", id);
        
        providerService.deleteProviderRegistration(domainId, solutionId, id);
        
        log.info("Provider registration deleted successfully: {}", id);
        
        return ResponseEntity.noContent().build();
    }
}