// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.environment.config.ProvisionRequestConfig;
import org.sifunity.broker.environment.domain.*;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.exception.AdminRequiredException;
import org.sifunity.broker.environment.exception.ProvisionRequestAccessDeniedException;
import org.sifunity.broker.environment.exception.ProvisionRequestNotFoundException;
import org.sifunity.broker.environment.exception.ProvisionRequestValidationException;
import org.sifunity.broker.environment.repository.*;

import java.time.Instant;
import java.util.*;

/**
 * Service handling provision request business logic.
 *
 * <p>Implements SIF 3.7 Infrastructure Services section 6 for consumer
 * self-provisioning via provisionRequest resources.</p>
 */
@Service
@Transactional
public class ProvisionRequestService {

    private static final Logger log = LoggerFactory.getLogger(ProvisionRequestService.class);

    private static final Set<String> VALID_SERVICE_TYPES = Set.of(
            "UTILITY", "OBJECT", "FUNCTIONAL", "SERVICEPATH", "XQUERYTEMPLATE", "SERVICE");

    private static final Set<String> VALID_RIGHT_TYPES = Set.of(
            "QUERY", "CREATE", "UPDATE", "DELETE", "SUBSCRIBE", "PROVIDE");

    private final ProvisionRequestRepository provisionRequestRepository;
    private final AclEntryRepository aclEntryRepository;
    private final EnvironmentRepository environmentRepository;
    private final ZoneRepository zoneRepository;
    private final ContextRepository contextRepository;
    private final ContextServiceRepository contextServiceRepository;
    private final ProvisionRequestConfig config;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    public ProvisionRequestService(ProvisionRequestRepository provisionRequestRepository,
                                   AclEntryRepository aclEntryRepository,
                                   EnvironmentRepository environmentRepository,
                                   ZoneRepository zoneRepository,
                                   ContextRepository contextRepository,
                                   ContextServiceRepository contextServiceRepository,
                                   ProvisionRequestConfig config,
                                   AuthorizationService authorizationService,
                                   ObjectMapper objectMapper) {
        this.provisionRequestRepository = provisionRequestRepository;
        this.aclEntryRepository = aclEntryRepository;
        this.environmentRepository = environmentRepository;
        this.zoneRepository = zoneRepository;
        this.contextRepository = contextRepository;
        this.contextServiceRepository = contextServiceRepository;
        this.config = config;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new provision request for the given environment.
     *
     * <p>Validates the requester owns the environment (self-provisioning only),
     * validates the payload structure, persists the request, and optionally
     * auto-approves all assertions based on configuration.</p>
     *
     * @param environmentId the environment ID from the URL path
     * @param payload       the provision request payload with rights assertions
     * @param requester     the authenticated environment making the request
     * @return the created provision request response DTO
     *
     * <p><strong>Requirements:</strong> 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 4.1, 4.2, 4.3</p>
     */
    @CacheEvict(value = {"acl-permissions", "acl-any-grant"}, allEntries = true)
    public ProvisionRequestResponseDTO createProvisionRequest(UUID environmentId,
                                                               ProvisionRequestCreateDTO payload,
                                                               Environment requester) {
        log.debug("Creating provision request for environment: {}", environmentId);

        // 1. Validate self-provisioning: requester must own the environment
        if (!requester.getId().equals(environmentId)) {
            throw new ProvisionRequestAccessDeniedException(requester.getId(), environmentId);
        }

        // 2. Validate payload
        validateCreatePayload(payload);

        // 3. Serialize provisionedZones to JSON
        String payloadJson = serializeProvisionedZones(payload.provisionedZones());

        // 4. Persist ProvisionRequest with completionStatus=null
        ProvisionRequest provisionRequest = new ProvisionRequest();
        provisionRequest.setEnvironmentId(environmentId);
        provisionRequest.setCompletionStatus(null);
        provisionRequest.setProvisionedZonesPayload(payloadJson);
        provisionRequest.setCreatedAt(Instant.now());

        provisionRequest = provisionRequestRepository.save(provisionRequest);
        log.info("Created provision request {} for environment {}", provisionRequest.getId(), environmentId);

        // 5. Auto-approve if enabled
        ProvisionedZonesDTO responseZones = payload.provisionedZones();
        if (config.autoApprove()) {
            log.debug("Auto-approve enabled, resolving all assertions to APPROVED");
            responseZones = autoApproveAssertions(payload.provisionedZones(), environmentId);

            provisionRequest.setCompletionStatus(CompletionStatus.ACCEPTED);
            provisionRequest.setResolvedAt(Instant.now());
            provisionRequest.setProvisionedZonesPayload(serializeProvisionedZones(responseZones));
            provisionRequest = provisionRequestRepository.save(provisionRequest);
            log.info("Auto-approved provision request {}", provisionRequest.getId());
        }

        // 6. Build response — omit completionStatus if null (handled by @JsonInclude)
        String completionStatusStr = provisionRequest.getCompletionStatus() != null
                ? provisionRequest.getCompletionStatus().name()
                : null;

        return new ProvisionRequestResponseDTO(
                provisionRequest.getId(),
                completionStatusStr,
                responseZones
        );
    }

    /**
     * Retrieves a provision request by ID with ownership/admin validation.
     *
     * <p>The controller uses the returned completionStatus to decide HTTP status:
     * 200 if resolved (completionStatus non-null), 202 if still pending (null).</p>
     *
     * @param id        the provision request ID
     * @param requester the authenticated environment making the request
     * @return the provision request response DTO
     * @throws ProvisionRequestNotFoundException    if no provision request exists with the given ID
     * @throws ProvisionRequestAccessDeniedException if requester is not the owner and not admin
     *
     * <p><strong>Requirements:</strong> 2.1, 2.2, 2.3, 2.4, 2.5, 2.6</p>
     */
    @Transactional(readOnly = true)
    public ProvisionRequestResponseDTO getProvisionRequest(UUID id, Environment requester) {
        log.debug("Querying provision request: {}", id);

        ProvisionRequest provisionRequest = provisionRequestRepository.findById(id)
                .orElseThrow(() -> new ProvisionRequestNotFoundException(id));

        validateOwnershipOrAdmin(provisionRequest, requester);

        ProvisionedZonesDTO zones = deserializeProvisionedZones(provisionRequest.getProvisionedZonesPayload());

        String completionStatusStr = provisionRequest.getCompletionStatus() != null
                ? provisionRequest.getCompletionStatus().name()
                : null;

        return new ProvisionRequestResponseDTO(
                provisionRequest.getId(),
                completionStatusStr,
                zones
        );
    }

    /**
     * Deletes a provision request by ID with ownership/admin validation.
     *
     * @param id        the provision request ID
     * @param requester the authenticated environment making the request
     * @throws ProvisionRequestNotFoundException    if no provision request exists with the given ID
     * @throws ProvisionRequestAccessDeniedException if requester is not the owner and not admin
     *
     * <p><strong>Requirements:</strong> 3.1, 3.2, 3.3, 3.4</p>
     */
    public void deleteProvisionRequest(UUID id, Environment requester) {
        log.debug("Deleting provision request: {}", id);

        ProvisionRequest provisionRequest = provisionRequestRepository.findById(id)
                .orElseThrow(() -> new ProvisionRequestNotFoundException(id));

        validateOwnershipOrAdmin(provisionRequest, requester);

        provisionRequestRepository.delete(provisionRequest);
        log.info("Deleted provision request {}", id);
    }

    /**
     * Resolves a provision request by an admin, setting each right assertion
     * to APPROVED or REJECTED and creating/updating corresponding ACL entries.
     *
     * @param id              the provision request ID
     * @param resolvedPayload the resolved payload with APPROVED/REJECTED right values
     * @param requester       the authenticated environment (must be admin)
     * @return the updated provision request response DTO
     *
     * <p><strong>Requirements:</strong> 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 6.1, 6.2, 6.3, 6.4</p>
     */
    @CacheEvict(value = {"acl-permissions", "acl-any-grant"}, allEntries = true)
    public ProvisionRequestResponseDTO resolveProvisionRequest(UUID id,
                                                                ProvisionRequestResolveDTO resolvedPayload,
                                                                Environment requester) {
        log.debug("Resolving provision request: {}", id);

        // 1. Validate requester is admin
        if (!authorizationService.isAdmin(requester)) {
            throw new AdminRequiredException(requester.getId());
        }

        // 2. Load provision request
        ProvisionRequest provisionRequest = provisionRequestRepository.findById(id)
                .orElseThrow(() -> new ProvisionRequestNotFoundException(id));

        // 3. Validate resolved payload structure
        validateResolvePayload(resolvedPayload);

        // 4. Get environment entity for ACL creation
        UUID environmentId = provisionRequest.getEnvironmentId();
        Environment environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new ProvisionRequestValidationException("PROV_001",
                        "Environment not found: " + environmentId));

        // 5. Collect all right values for completionStatus computation
        List<String> allRightValues = new ArrayList<>();

        // 6. Process each zone/service/right assertion
        for (ProvisionedZoneDTO zone : resolvedPayload.provisionedZones().provisionedZone()) {
            Zone zoneEntity = zoneRepository.findBySolutionUuidAndZoneId(environment.getSolutionUuid(), zone.id())
                    .orElseThrow(() -> new ProvisionRequestValidationException("PROV_006",
                            "Zone not found: " + zone.id()));

            for (ProvisionedServiceDTO svc : zone.services().service()) {
                final UUID contextUuid;
                if (svc.contextId() != null) {
                    Context ctx = contextRepository.findByZoneUuidAndContextId(
                                    zoneEntity.getId(), svc.contextId())
                            .orElseThrow(() -> new ProvisionRequestValidationException("PROV_007",
                                    "Context not found: " + svc.contextId()
                                            + " in zone: " + zone.id()));
                    contextUuid = ctx.getId();
                } else {
                    contextUuid = null;
                }

                for (RightDTO right : svc.rights().right()) {
                    AccessType accessType = AccessType.valueOf(right.type());
                    AccessLevel accessLevel = AccessLevel.valueOf(right.value());

                    createOrUpdateAclEntry(environment, svc.name(), contextUuid,
                            accessType, accessLevel);

                    allRightValues.add(right.value());
                }
            }
        }

        // 7. Compute completionStatus
        CompletionStatus completionStatus = computeCompletionStatus(allRightValues);

        // 8. Update provision request
        provisionRequest.setProvisionedZonesPayload(
                serializeProvisionedZones(resolvedPayload.provisionedZones()));
        provisionRequest.setCompletionStatus(completionStatus);
        provisionRequest.setResolvedAt(Instant.now());
        provisionRequest = provisionRequestRepository.save(provisionRequest);

        log.info("Resolved provision request {} with status {}", id, completionStatus);

        return new ProvisionRequestResponseDTO(
                provisionRequest.getId(),
                completionStatus.name(),
                resolvedPayload.provisionedZones()
        );
    }

    /**
     * Computes the completion status from a list of right values.
     *
     * @param rightValues list of right value strings (APPROVED or REJECTED)
     * @return ACCEPTED if all APPROVED, REJECTED if all REJECTED, MIXED otherwise
     */
    public static CompletionStatus computeCompletionStatus(List<String> rightValues) {
        boolean hasApproved = false;
        boolean hasRejected = false;

        for (String value : rightValues) {
            if ("APPROVED".equals(value)) {
                hasApproved = true;
            } else if ("REJECTED".equals(value)) {
                hasRejected = true;
            }
            if (hasApproved && hasRejected) {
                return CompletionStatus.MIXED;
            }
        }

        if (hasApproved) {
            return CompletionStatus.ACCEPTED;
        }
        return CompletionStatus.REJECTED;
    }

    // ---- Ownership Validation ----

    private void validateOwnershipOrAdmin(ProvisionRequest provisionRequest, Environment requester) {
        boolean isOwner = requester.getId().equals(provisionRequest.getEnvironmentId());
        boolean isAdmin = authorizationService.isAdmin(requester);

        if (!isOwner && !isAdmin) {
            throw new ProvisionRequestAccessDeniedException(requester.getId(), provisionRequest.getId());
        }
    }

    // ---- Validation ----

    private void validateResolvePayload(ProvisionRequestResolveDTO payload) {
        if (payload.provisionedZones() == null || payload.provisionedZones().provisionedZone() == null
                || payload.provisionedZones().provisionedZone().isEmpty()) {
            throw new ProvisionRequestValidationException("PROV_005",
                    "provisionedZones must contain at least one provisionedZone");
        }

        for (ProvisionedZoneDTO zone : payload.provisionedZones().provisionedZone()) {
            validateResolveZone(zone);
        }
    }

    private void validateResolveZone(ProvisionedZoneDTO zone) {
        if (zone.id() == null || zone.id().isBlank()) {
            throw new ProvisionRequestValidationException("PROV_005",
                    "Each provisionedZone must have a non-empty id");
        }
        if (zone.services() == null || zone.services().service() == null
                || zone.services().service().isEmpty()) {
            throw new ProvisionRequestValidationException("PROV_005",
                    "Each provisionedZone must contain at least one service");
        }
        for (ProvisionedServiceDTO svc : zone.services().service()) {
            validateResolveService(svc);
        }
    }

    private void validateResolveService(ProvisionedServiceDTO svc) {
        if (svc.name() == null || svc.name().isBlank()) {
            throw new ProvisionRequestValidationException("PROV_005",
                    "Each service must have a non-empty name");
        }
        if (svc.type() == null || svc.type().isBlank()) {
            throw new ProvisionRequestValidationException("PROV_005",
                    "Each service must have a non-empty type");
        }
        if (svc.rights() == null || svc.rights().right() == null
                || svc.rights().right().isEmpty()) {
            throw new ProvisionRequestValidationException("PROV_005",
                    "Each service must have at least one right assertion");
        }
        for (RightDTO right : svc.rights().right()) {
            validateResolveRight(right);
        }
    }

    private void validateResolveRight(RightDTO right) {
        if (right.type() == null || right.type().isBlank()) {
            throw new ProvisionRequestValidationException("PROV_005",
                    "Each right must have a non-null type");
        }
        if (!VALID_RIGHT_TYPES.contains(right.type())) {
            throw new ProvisionRequestValidationException("PROV_005",
                    "Invalid right type: " + right.type()
                            + ". Must be one of: " + VALID_RIGHT_TYPES);
        }
        if (!"APPROVED".equals(right.value()) && !"REJECTED".equals(right.value())) {
            throw new ProvisionRequestValidationException("PROV_005",
                    "Each resolved right value must be APPROVED or REJECTED, got: " + right.value());
        }
    }

    private void validateCreatePayload(ProvisionRequestCreateDTO payload) {
        if (payload.provisionedZones() == null || payload.provisionedZones().provisionedZone() == null
                || payload.provisionedZones().provisionedZone().isEmpty()) {
            throw new ProvisionRequestValidationException("PROV_001",
                    "provisionedZones must contain at least one provisionedZone");
        }

        for (ProvisionedZoneDTO zone : payload.provisionedZones().provisionedZone()) {
            validateZone(zone);
        }
    }

    private void validateZone(ProvisionedZoneDTO zone) {
        if (zone.id() == null || zone.id().isBlank()) {
            throw new ProvisionRequestValidationException("PROV_001",
                    "Each provisionedZone must have a non-empty id");
        }
        if (zone.services() == null || zone.services().service() == null
                || zone.services().service().isEmpty()) {
            throw new ProvisionRequestValidationException("PROV_001",
                    "Each provisionedZone must contain at least one service");
        }
        for (ProvisionedServiceDTO svc : zone.services().service()) {
            validateService(svc);
        }
    }

    private void validateService(ProvisionedServiceDTO svc) {
        if (svc.name() == null || svc.name().isBlank()) {
            throw new ProvisionRequestValidationException("PROV_001",
                    "Each service must have a non-empty name");
        }
        if (svc.type() == null || svc.type().isBlank()) {
            throw new ProvisionRequestValidationException("PROV_001",
                    "Each service must have a non-empty type");
        }
        if (!VALID_SERVICE_TYPES.contains(svc.type())) {
            throw new ProvisionRequestValidationException("PROV_001",
                    "Invalid service type: " + svc.type()
                            + ". Must be one of: " + VALID_SERVICE_TYPES);
        }
        if (svc.rights() == null || svc.rights().right() == null
                || svc.rights().right().isEmpty()) {
            throw new ProvisionRequestValidationException("PROV_001",
                    "Each service must have at least one right assertion");
        }
        for (RightDTO right : svc.rights().right()) {
            validateRight(right);
        }
    }

    private void validateRight(RightDTO right) {
        if (right.type() == null || right.type().isBlank()) {
            throw new ProvisionRequestValidationException("PROV_001",
                    "Each right must have a non-null type");
        }
        if (!VALID_RIGHT_TYPES.contains(right.type())) {
            throw new ProvisionRequestValidationException("PROV_001",
                    "Invalid right type: " + right.type()
                            + ". Must be one of: " + VALID_RIGHT_TYPES);
        }
        if (!"REQUESTED".equals(right.value())) {
            throw new ProvisionRequestValidationException("PROV_001",
                    "Each right value must be REQUESTED on creation, got: " + right.value());
        }
    }

    // ---- Serialization ----

    private String serializeProvisionedZones(ProvisionedZonesDTO zones) {
        try {
            return objectMapper.writeValueAsString(zones);
        } catch (JsonProcessingException e) {
            throw new ProvisionRequestValidationException("PROV_001",
                    "Failed to serialize provisionedZones: " + e.getMessage());
        }
    }

    private ProvisionedZonesDTO deserializeProvisionedZones(String json) {
        try {
            return objectMapper.readValue(json, ProvisionedZonesDTO.class);
        } catch (JsonProcessingException e) {
            throw new ProvisionRequestValidationException("PROV_001",
                    "Failed to deserialize provisionedZones: " + e.getMessage());
        }
    }

    // ---- Auto-Approval ----

    private ProvisionedZonesDTO autoApproveAssertions(ProvisionedZonesDTO zones, UUID environmentId) {
        Environment environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new ProvisionRequestValidationException("PROV_001",
                        "Environment not found: " + environmentId));

        List<ProvisionedZoneDTO> approvedZones = new ArrayList<>();

        for (ProvisionedZoneDTO zone : zones.provisionedZone()) {
            Zone zoneEntity = zoneRepository.findBySolutionUuidAndZoneId(environment.getSolutionUuid(), zone.id())
                    .orElseThrow(() -> new ProvisionRequestValidationException("PROV_006",
                            "Zone not found: " + zone.id()));

            List<ProvisionedServiceDTO> approvedServices = new ArrayList<>();

            for (ProvisionedServiceDTO svc : zone.services().service()) {
                UUID contextUuid = null;
                if (svc.contextId() != null) {
                    Context ctx = contextRepository.findByZoneUuidAndContextId(
                                    zoneEntity.getId(), svc.contextId())
                            .orElseThrow(() -> new ProvisionRequestValidationException("PROV_007",
                                    "Context not found: " + svc.contextId()
                                            + " in zone: " + zone.id()));
                    contextUuid = ctx.getId();
                }

                List<RightDTO> approvedRights = new ArrayList<>();
                for (RightDTO right : svc.rights().right()) {
                    AccessType accessType = AccessType.valueOf(right.type());

                    // Create or update ACL entry
                    createOrUpdateAclEntry(environment, svc.name(), contextUuid,
                            accessType, AccessLevel.APPROVED);

                    approvedRights.add(new RightDTO(right.type(), "APPROVED"));
                }

                approvedServices.add(new ProvisionedServiceDTO(
                        svc.type(), svc.name(), svc.contextId(),
                        new ProvisionedServiceRightsDTO(approvedRights)));
            }

            approvedZones.add(new ProvisionedZoneDTO(zone.id(),
                    new ProvisionedZoneServicesDTO(approvedServices)));
        }

        return new ProvisionedZonesDTO(approvedZones);
    }

    private void createOrUpdateAclEntry(Environment environment, String serviceName,
                                         UUID contextUuid, AccessType accessType,
                                         AccessLevel accessLevel) {
        // Resolve (serviceName, contextUuid) → contextServiceUuid
        var contextServiceOpt = contextServiceRepository
                .findByContextUuidAndServiceName(contextUuid, serviceName);
        
        if (contextServiceOpt.isPresent()) {
            UUID contextServiceUuid = contextServiceOpt.get().getId();
            Optional<AclEntry> existing = aclEntryRepository
                    .findByEnvironmentIdAndContextServiceUuidAndAccessType(
                            environment.getId(), contextServiceUuid, accessType);

            if (existing.isPresent()) {
                AclEntry entry = existing.get();
                entry.setAccessLevel(accessLevel);
                aclEntryRepository.save(entry);
                log.debug("Updated ACL entry {} to {} for env={}, service={}, context={}, type={}",
                        entry.getId(), accessLevel, environment.getId(), serviceName, contextUuid, accessType);
            } else {
                AclEntry entry = new AclEntry();
                entry.setEnvironment(environment);
                entry.setContextServiceUuid(contextServiceUuid);
                entry.setAccessType(accessType);
                entry.setAccessLevel(accessLevel);
                aclEntryRepository.save(entry);
                log.debug("Created ACL entry for env={}, service={}, context={}, type={}, level={}",
                        environment.getId(), serviceName, contextUuid, accessType, accessLevel);
            }
        } else {
            // Auto-create context_service, then create ACL entry
            org.sifunity.broker.environment.domain.ContextService cs = new org.sifunity.broker.environment.domain.ContextService();
            cs.setContextUuid(contextUuid);
            cs.setServiceName(serviceName);
            cs.setServiceType("OBJECT");
            org.sifunity.broker.environment.domain.ContextService saved = contextServiceRepository.save(cs);
            
            AclEntry entry = new AclEntry();
            entry.setEnvironment(environment);
            entry.setContextServiceUuid(saved.getId());
            entry.setAccessType(accessType);
            entry.setAccessLevel(accessLevel);
            aclEntryRepository.save(entry);
            log.debug("Created context_service and ACL entry for env={}, service={}, context={}, type={}, level={}",
                    environment.getId(), serviceName, contextUuid, accessType, accessLevel);
        }
    }
}
