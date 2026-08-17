// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.CodeItemAction;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.CodeSet;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.CodeSetRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing SIF 3.7 code sets.
 *
 * <p>Handles code set CRUD operations with validation, zone-scoping logic,
 * provider registration checks, duplicate detection, and DTO conversion.</p>
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Validate required fields (zone, version, timestamp) on create/update</li>
 *   <li>Validate codeItems if present (code, value, action, timestamp required per item; field length constraints)</li>
 *   <li>Check codeSets provider registered in environment-global zone</li>
 *   <li>Enforce duplicate detection (codeSetId + zone + solutionUuid) → 409</li>
 *   <li>Assign UUID id if not provided on create</li>
 *   <li>Apply zone-scoping logic on queries</li>
 *   <li>Full replacement on update (PUT semantics)</li>
 * </ul>
 *
 * @see CodeSet
 * @see CodeSetRepository
 */
@Service
@Transactional
public class CodeSetsService {

    private static final Logger log = LoggerFactory.getLogger(CodeSetsService.class);

    private static final String ENVIRONMENT_GLOBAL_ZONE_ID = "environment-global";
    private static final String CODE_SETS_SERVICE_NAME = "codeSets";

    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_VALUE_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 1024;
    private static final int MAX_DEFINITION_LENGTH = 4096;

    private final CodeSetRepository codeSetRepository;
    private final ProviderRegistrationRepository providerRegistrationRepository;
    private final ContextRepository contextRepository;
    private final ContextServiceRepository contextServiceRepository;
    private final ZoneRepository zoneRepository;
    private final DomainRepository domainRepository;
    private final SolutionRepository solutionRepository;
    private final CodeSetEventPublisher codeSetEventPublisher;
    private final MeterRegistry meterRegistry;

    public CodeSetsService(CodeSetRepository codeSetRepository,
                           ProviderRegistrationRepository providerRegistrationRepository,
                           ContextRepository contextRepository,
                           ContextServiceRepository contextServiceRepository,
                           ZoneRepository zoneRepository,
                           DomainRepository domainRepository,
                           SolutionRepository solutionRepository,
                           CodeSetEventPublisher codeSetEventPublisher,
                           MeterRegistry meterRegistry) {
        this.codeSetRepository = codeSetRepository;
        this.providerRegistrationRepository = providerRegistrationRepository;
        this.contextRepository = contextRepository;
        this.contextServiceRepository = contextServiceRepository;
        this.zoneRepository = zoneRepository;
        this.domainRepository = domainRepository;
        this.solutionRepository = solutionRepository;
        this.codeSetEventPublisher = codeSetEventPublisher;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Creates a new code set.
     *
     * <p>Validates required fields, checks provider registration, detects duplicates,
     * assigns UUID id if not provided, persists the code set, and returns the DTO.</p>
     *
     * @param domainId   the domain ID from the request path
     * @param solutionId the solution ID from the request path
     * @param body       the code set creation body
     * @return the created code set as a DTO
     *
     * <p><strong>Requirements:</strong> 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8,
     * 3.11, 8.4, 8.5, 8.6, 8.7, 8.8, 13.1, 13.2, 13.3, 13.4, 13.5</p>
     */
    public CodeSetDTO createCodeSet(String domainId, String solutionId, CodeSetCreateBody body) {
        log.debug("Creating code set: id={}, zone={}, version={}", body.id(), body.zone(), body.version());

        // 1. Validate required fields (Req 3.2, 3.3, 3.4, 3.5)
        validateRequiredFields(body.zone(), body.version(), body.timestamp());

        // 2. Validate id length if provided (Req 8.8)
        if (body.id() != null && body.id().length() > MAX_ID_LENGTH) {
            throw new SifBrokerException(
                    "CODESET_003", HttpStatus.BAD_REQUEST,
                    "Field 'id' exceeds maximum length of " + MAX_ID_LENGTH + " characters");
        }

        // 3. Validate codeItems if present (Req 13.1, 13.2, 13.3, 13.4, 13.5, 8.4, 8.5, 8.6, 8.7)
        validateCodeItems(body.codeItems());

        // 4. Resolve solution UUID from domainId/solutionId
        Solution solution = resolveSolution(domainId, solutionId);

        // 5. Check codeSets provider registered in environment-global zone (Req 11.1)
        checkProviderRegistered(solution.getId());

        // 6. Determine code set id — use provided or generate UUID (Req 3.6)
        String codeSetId = (body.id() != null && !body.id().isBlank()) ? body.id() : UUID.randomUUID().toString();

        // 7. Check duplicate (codeSetId + zone + solutionUuid) → 409 (Req 3.11)
        Optional<CodeSet> existing = codeSetRepository.findByCodeSetIdAndZoneAndSolutionUuid(
                codeSetId, body.zone(), solution.getId());
        if (existing.isPresent()) {
            throw new SifBrokerException(
                    "CODESET_008", HttpStatus.CONFLICT,
                    "A code set with ID '" + codeSetId + "' already exists in zone '" + body.zone() + "'.");
        }

        // 8. Build and persist entity
        CodeSet codeSet = new CodeSet();
        codeSet.setCodeSetId(codeSetId);
        codeSet.setZone(body.zone());
        codeSet.setVersion(body.version());
        codeSet.setTimestamp(body.timestamp());
        codeSet.setSource(body.source());
        codeSet.setCodeItems(toCodeItemsWrapper(body.codeItems()));
        codeSet.setSolutionUuid(solution.getId());

        CodeSet saved = codeSetRepository.save(codeSet);

        log.info("Code set created: id={}, zone={}, solutionUuid={}",
                saved.getCodeSetId(), saved.getZone(), saved.getSolutionUuid());

        // 9. Publish event (best-effort) — Req 6.1, 6.6
        CodeSetDTO dto = mapToCodeSetDTO(saved);
        try {
            codeSetEventPublisher.publishCodeSetCreatedEvent(dto, domainId, solutionId);
        } catch (Exception e) {
            log.warn("Code set CREATE event publication failed (best-effort): id={}, error={}",
                    saved.getCodeSetId(), e.getMessage());
        }

        return dto;
    }

    /**
     * Lists code sets for a solution with zone-scoping logic.
     *
     * <p>Fetches all code sets for the solution, then applies zone-scoping:
     * if zoneId is "environment-global", returns all; otherwise overrides
     * globals with zone-specific entries sharing the same codeSetId.</p>
     *
     * @param domainId  the domain ID from the request path
     * @param solutionId the solution ID from the request path
     * @param zoneId    the effective zone ID for scoping
     * @return list of zone-scoped code set DTOs
     *
     * <p><strong>Requirements:</strong> 1.1, 1.2, 1.3, 1.6, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6</p>
     */
    @Transactional(readOnly = true)
    public List<CodeSetDTO> listCodeSets(String domainId, String solutionId, String zoneId) {
        log.debug("Listing code sets: domainId={}, solutionId={}, zoneId={}", domainId, solutionId, zoneId);

        Solution solution = resolveSolution(domainId, solutionId);
        List<CodeSet> allCodeSets = codeSetRepository.findBySolutionUuid(solution.getId());

        List<CodeSet> scoped = applyZoneScoping(allCodeSets, zoneId);

        log.debug("Found {} code sets after zone-scoping (zoneId={})", scoped.size(), zoneId);
        return scoped.stream().map(this::mapToCodeSetDTO).toList();
    }

    /**
     * Gets a single code set by ID with zone-scoping logic.
     *
     * <p>If a zone-specific entry exists for the requested zone, returns it
     * instead of the global entry. Returns 404 if not found.</p>
     *
     * @param domainId   the domain ID from the request path
     * @param solutionId the solution ID from the request path
     * @param id         the code set identifier
     * @param zoneId     the effective zone ID for scoping
     * @return the code set DTO
     * @throws SifBrokerException with CODESET_006 if not found
     *
     * <p><strong>Requirements:</strong> 2.1, 2.2, 2.3, 7.1, 7.2, 7.3</p>
     */
    @Transactional(readOnly = true)
    public CodeSetDTO getCodeSetById(String domainId, String solutionId, String id, String zoneId) {
        log.debug("Getting code set: id={}, zoneId={}", id, zoneId);

        Solution solution = resolveSolution(domainId, solutionId);

        // If zone is not environment-global, try zone-specific first
        if (!ENVIRONMENT_GLOBAL_ZONE_ID.equals(zoneId)) {
            Optional<CodeSet> zoneSpecific = codeSetRepository.findByCodeSetIdAndZoneAndSolutionUuid(
                    id, zoneId, solution.getId());
            if (zoneSpecific.isPresent()) {
                return mapToCodeSetDTO(zoneSpecific.get());
            }
        }

        // Fall back to global (or direct lookup if zoneId is environment-global)
        Optional<CodeSet> global = codeSetRepository.findByCodeSetIdAndZoneAndSolutionUuid(
                id, ENVIRONMENT_GLOBAL_ZONE_ID, solution.getId());
        if (global.isPresent()) {
            return mapToCodeSetDTO(global.get());
        }

        // If zoneId is environment-global, also check if there's a zone-specific entry with this id
        // (environment-global returns all, so any zone match is valid)
        if (ENVIRONMENT_GLOBAL_ZONE_ID.equals(zoneId)) {
            List<CodeSet> allForSolution = codeSetRepository.findBySolutionUuid(solution.getId());
            Optional<CodeSet> anyMatch = allForSolution.stream()
                    .filter(cs -> cs.getCodeSetId().equals(id))
                    .findFirst();
            if (anyMatch.isPresent()) {
                return mapToCodeSetDTO(anyMatch.get());
            }
        }

        throw new SifBrokerException(
                "CODESET_006", HttpStatus.NOT_FOUND,
                "Code set with ID '" + id + "' not found");
    }

    /**
     * Updates an existing code set (full replacement).
     *
     * <p>Validates required fields and codeItems, finds the existing code set by
     * internal UUID, replaces all fields, persists, and returns the updated DTO.</p>
     *
     * @param domainId   the domain ID from the request path
     * @param solutionId the solution ID from the request path
     * @param id         the code set identifier
     * @param body       the update request body
     * @return the updated code set as a DTO
     * @throws SifBrokerException with CODESET_006 if not found
     *
     * <p><strong>Requirements:</strong> 4.1, 4.2, 4.3, 4.6, 8.4, 8.5, 8.6, 8.7, 8.8,
     * 13.1, 13.2, 13.3, 13.4, 13.5</p>
     */
    public CodeSetDTO updateCodeSet(String domainId, String solutionId, String id, CodeSetCreateBody body) {
        log.debug("Updating code set: id={}, zone={}, version={}", id, body.zone(), body.version());

        // 1. Validate required fields (Req 4.3)
        validateRequiredFields(body.zone(), body.version(), body.timestamp());

        // 2. Validate id length if provided (Req 8.8)
        if (body.id() != null && body.id().length() > MAX_ID_LENGTH) {
            throw new SifBrokerException(
                    "CODESET_003", HttpStatus.BAD_REQUEST,
                    "Field 'id' exceeds maximum length of " + MAX_ID_LENGTH + " characters");
        }

        // 3. Validate codeItems if present
        validateCodeItems(body.codeItems());

        // 4. Resolve solution and find existing code set
        Solution solution = resolveSolution(domainId, solutionId);

        CodeSet existing = codeSetRepository.findByCodeSetIdAndZoneAndSolutionUuid(
                id, body.zone(), solution.getId())
                .orElse(null);

        // If not found by id + zone, try finding by id in any zone for this solution
        if (existing == null) {
            List<CodeSet> allForSolution = codeSetRepository.findBySolutionUuid(solution.getId());
            existing = allForSolution.stream()
                    .filter(cs -> cs.getCodeSetId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new SifBrokerException(
                            "CODESET_006", HttpStatus.NOT_FOUND,
                            "Code set with ID '" + id + "' not found"));
        }

        // 5. Full replacement of all fields (Req 4.6)
        existing.setCodeSetId(body.id() != null && !body.id().isBlank() ? body.id() : existing.getCodeSetId());
        existing.setZone(body.zone());
        existing.setVersion(body.version());
        existing.setTimestamp(body.timestamp());
        existing.setSource(body.source());
        existing.setCodeItems(toCodeItemsWrapper(body.codeItems()));

        CodeSet saved = codeSetRepository.save(existing);

        log.info("Code set updated: id={}, zone={}", saved.getCodeSetId(), saved.getZone());

        // Publish event (best-effort) — Req 6.2, 6.6
        CodeSetDTO dto = mapToCodeSetDTO(saved);
        try {
            codeSetEventPublisher.publishCodeSetUpdatedEvent(dto, domainId, solutionId);
        } catch (Exception e) {
            log.warn("Code set UPDATE event publication failed (best-effort): id={}, error={}",
                    saved.getCodeSetId(), e.getMessage());
        }

        return dto;
    }

    /**
     * Deletes a code set by its identifier.
     *
     * @param domainId   the domain ID from the request path
     * @param solutionId the solution ID from the request path
     * @param id         the code set identifier
     * @throws SifBrokerException with CODESET_006 if not found
     *
     * <p><strong>Requirements:</strong> 5.1, 5.2, 5.4</p>
     */
    public void deleteCodeSet(String domainId, String solutionId, String id) {
        log.debug("Deleting code set: id={}", id);

        Solution solution = resolveSolution(domainId, solutionId);

        List<CodeSet> allForSolution = codeSetRepository.findBySolutionUuid(solution.getId());
        CodeSet existing = allForSolution.stream()
                .filter(cs -> cs.getCodeSetId().equals(id))
                .findFirst()
                .orElseThrow(() -> new SifBrokerException(
                        "CODESET_006", HttpStatus.NOT_FOUND,
                        "Code set with ID '" + id + "' not found"));

        // Build DTO before delete for event publication
        CodeSetDTO dto = mapToCodeSetDTO(existing);

        codeSetRepository.delete(existing);

        log.info("Code set deleted: id={}, zone={}", existing.getCodeSetId(), existing.getZone());

        // Publish event (best-effort) — Req 6.3, 6.6
        try {
            codeSetEventPublisher.publishCodeSetDeletedEvent(dto, domainId, solutionId);
        } catch (Exception e) {
            log.warn("Code set DELETE event publication failed (best-effort): id={}, error={}",
                    existing.getCodeSetId(), e.getMessage());
        }
    }

    // ---- Validation helpers ----

    /**
     * Validates required fields: zone (non-blank), version (non-blank), timestamp (non-blank).
     *
     * <p><strong>Requirements:</strong> 3.2, 3.3, 3.4, 3.5, 4.3</p>
     */
    private void validateRequiredFields(String zone, String version, String timestamp) {
        if (zone == null || zone.isBlank()) {
            throw new SifBrokerException(
                    "CODESET_001", HttpStatus.BAD_REQUEST,
                    "Missing or invalid required field: zone must be a non-blank string");
        }
        if (version == null || version.isBlank()) {
            throw new SifBrokerException(
                    "CODESET_001", HttpStatus.BAD_REQUEST,
                    "Missing or invalid required field: version must be a non-blank string");
        }
        if (timestamp == null || timestamp.isBlank()) {
            throw new SifBrokerException(
                    "CODESET_001", HttpStatus.BAD_REQUEST,
                    "Missing or invalid required field: timestamp must be a non-blank string");
        }
    }

    /**
     * Validates codeItems if present.
     *
     * <p>Each code item must have: non-blank code, non-blank value (max 128),
     * valid action enum, non-blank timestamp. Optional fields: description (max 1024),
     * definition (max 4096).</p>
     *
     * <p><strong>Requirements:</strong> 13.1, 13.2, 13.3, 13.4, 13.5, 8.4, 8.5, 8.6, 8.7</p>
     */
    private void validateCodeItems(CodeItemsDTO codeItems) {
        if (codeItems == null || codeItems.codeItem() == null || codeItems.codeItem().isEmpty()) {
            return;
        }

        List<CodeItemDTO> items = codeItems.codeItem();
        for (int i = 0; i < items.size(); i++) {
            CodeItemDTO item = items.get(i);

            // code required (Req 13.1)
            if (item.code() == null || item.code().isBlank()) {
                throw new SifBrokerException(
                        "CODESET_002", HttpStatus.BAD_REQUEST,
                        "Invalid code item at index " + i + ": 'code' must be a non-blank string");
            }

            // value required, max 128 (Req 13.2, 8.5)
            if (item.value() == null || item.value().isBlank()) {
                throw new SifBrokerException(
                        "CODESET_002", HttpStatus.BAD_REQUEST,
                        "Invalid code item at index " + i + ": 'value' must be a non-blank string");
            }
            if (item.value().length() > MAX_VALUE_LENGTH) {
                throw new SifBrokerException(
                        "CODESET_003", HttpStatus.BAD_REQUEST,
                        "Invalid code item at index " + i + ": 'value' exceeds maximum length of "
                                + MAX_VALUE_LENGTH + " characters");
            }

            // action required, must be valid enum (Req 13.3, 8.4)
            if (item.action() == null) {
                throw new SifBrokerException(
                        "CODESET_002", HttpStatus.BAD_REQUEST,
                        "Invalid code item at index " + i + ": 'action' is required and must be one of: "
                                + Arrays.toString(CodeItemAction.values()));
            }

            // timestamp required (Req 13.4)
            if (item.timestamp() == null || item.timestamp().isBlank()) {
                throw new SifBrokerException(
                        "CODESET_002", HttpStatus.BAD_REQUEST,
                        "Invalid code item at index " + i + ": 'timestamp' must be a non-blank string");
            }

            // description max 1024 (Req 8.6)
            if (item.description() != null && item.description().length() > MAX_DESCRIPTION_LENGTH) {
                throw new SifBrokerException(
                        "CODESET_003", HttpStatus.BAD_REQUEST,
                        "Invalid code item at index " + i + ": 'description' exceeds maximum length of "
                                + MAX_DESCRIPTION_LENGTH + " characters");
            }

            // definition max 4096 (Req 8.7)
            if (item.definition() != null && item.definition().length() > MAX_DEFINITION_LENGTH) {
                throw new SifBrokerException(
                        "CODESET_003", HttpStatus.BAD_REQUEST,
                        "Invalid code item at index " + i + ": 'definition' exceeds maximum length of "
                                + MAX_DEFINITION_LENGTH + " characters");
            }
        }
    }

    /**
     * Checks that a codeSets provider is registered in the environment-global zone.
     *
     * <p><strong>Requirements:</strong> 11.1</p>
     */
    private void checkProviderRegistered(java.util.UUID solutionUuid) {
        boolean providerExists = zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, ENVIRONMENT_GLOBAL_ZONE_ID)
                .flatMap(zone -> contextRepository.findByZoneUuidAndContextId(zone.getId(), "DEFAULT"))
                .flatMap(ctx -> contextServiceRepository.findByContextUuidAndServiceName(
                        ctx.getId(), CODE_SETS_SERVICE_NAME))
                .flatMap(cs -> providerRegistrationRepository.findByContextServiceUuid(cs.getId()))
                .isPresent();
        if (!providerExists) {
            throw new SifBrokerException(
                    "CODESET_007", HttpStatus.NOT_FOUND,
                    "No provider registered for the codeSets service in zone "
                            + ENVIRONMENT_GLOBAL_ZONE_ID + ".");
        }
    }

    /**
     * Resolves the Solution entity from domainId and solutionId path variables.
     */
    private Solution resolveSolution(String domainId, String solutionId) {
        Domain domain = domainRepository.findByDomainId(domainId)
                .orElseThrow(() -> new SifBrokerException(
                        "CODESET_001", HttpStatus.BAD_REQUEST,
                        "Domain '" + domainId + "' not found"));

        return solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), solutionId)
                .orElseThrow(() -> new SifBrokerException(
                        "CODESET_001", HttpStatus.BAD_REQUEST,
                        "Solution '" + solutionId + "' not found in domain '" + domainId + "'"));
    }

    // ---- Zone-scoping logic ----

    /**
     * Applies zone-scoping logic to a list of code sets.
     *
     * <p>Algorithm:</p>
     * <ul>
     *   <li>If zoneId = "environment-global" → return all code sets (no override logic)</li>
     *   <li>Otherwise: start with globals, override with zone-specific entries sharing
     *       the same codeSetId, include zone-only entries</li>
     * </ul>
     *
     * <p><strong>Requirements:</strong> 7.1, 7.2, 7.3, 7.4, 7.5, 7.6</p>
     */
    private List<CodeSet> applyZoneScoping(List<CodeSet> allCodeSets, String zoneId) {
        return Timer.builder("sif.zone.scoping.duration")
                .description("Zone-scoping filter latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(() -> {
                    // environment-global returns everything (Req 7.5)
                    if (ENVIRONMENT_GLOBAL_ZONE_ID.equals(zoneId)) {
                        return allCodeSets;
                    }

                    // Start with globals, override with zone-specific
                    Map<String, CodeSet> result = new LinkedHashMap<>();

                    // Add all global sets first
                    for (CodeSet cs : allCodeSets) {
                        if (ENVIRONMENT_GLOBAL_ZONE_ID.equals(cs.getZone())) {
                            result.put(cs.getCodeSetId(), cs);
                        }
                    }

                    // Override with zone-specific sets (same id replaces global) + add zone-only entries
                    for (CodeSet cs : allCodeSets) {
                        if (zoneId.equals(cs.getZone())) {
                            result.put(cs.getCodeSetId(), cs);
                        }
                    }

                    return new ArrayList<>(result.values());
                });
    }

    // ---- DTO conversion ----

    /**
     * Maps a CodeSet entity to a CodeSetDTO for API responses.
     */
    CodeSetDTO mapToCodeSetDTO(CodeSet entity) {
        return new CodeSetDTO(
                entity.getCodeSetId(),
                entity.getZone(),
                entity.getVersion(),
                entity.getTimestamp(),
                entity.getSource(),
                toCodeItemsDTO(entity.getCodeItems())
        );
    }

    /**
     * Converts a CodeItemsDTO (from request) to a CodeItemsWrapper (for JSONB persistence).
     */
    private CodeItemsWrapper toCodeItemsWrapper(CodeItemsDTO dto) {
        if (dto == null || dto.codeItem() == null) {
            return null;
        }

        List<CodeItemData> items = dto.codeItem().stream()
                .map(this::toCodeItemData)
                .toList();

        return new CodeItemsWrapper(items);
    }

    /**
     * Converts a single CodeItemDTO to CodeItemData for JSONB storage.
     */
    private CodeItemData toCodeItemData(CodeItemDTO dto) {
        List<AliasData> aliases = null;
        if (dto.aliases() != null && dto.aliases().alias() != null) {
            aliases = dto.aliases().alias().stream()
                    .map(this::toAliasData)
                    .toList();
        }

        return new CodeItemData(
                dto.code(),
                dto.source(),
                dto.namespace(),
                dto.value(),
                dto.description(),
                dto.definition(),
                aliases,
                dto.action() != null ? dto.action().name() : null,
                dto.timestamp()
        );
    }

    /**
     * Converts an AliasDTO to AliasData for JSONB storage.
     */
    private AliasData toAliasData(AliasDTO dto) {
        AliasCodeData codeData = null;
        if (dto.code() != null) {
            codeData = new AliasCodeData(dto.code().old(), dto.code().official(), dto.code().value());
        }
        return new AliasData(codeData, dto.source(), dto.namespace());
    }

    /**
     * Converts a CodeItemsWrapper (from JSONB) to a CodeItemsDTO for API responses.
     */
    private CodeItemsDTO toCodeItemsDTO(CodeItemsWrapper wrapper) {
        if (wrapper == null || wrapper.codeItem() == null) {
            return null;
        }

        List<CodeItemDTO> items = wrapper.codeItem().stream()
                .map(this::toCodeItemDTO)
                .toList();

        return new CodeItemsDTO(items);
    }

    /**
     * Converts a single CodeItemData to CodeItemDTO for API responses.
     */
    private CodeItemDTO toCodeItemDTO(CodeItemData data) {
        AliasesDTO aliasesDTO = null;
        if (data.aliases() != null) {
            List<AliasDTO> aliasDTOs = data.aliases().stream()
                    .map(this::toAliasDTO)
                    .toList();
            aliasesDTO = new AliasesDTO(aliasDTOs);
        }

        CodeItemAction action = null;
        if (data.action() != null) {
            action = CodeItemAction.valueOf(data.action());
        }

        return new CodeItemDTO(
                data.code(),
                data.source(),
                data.namespace(),
                data.value(),
                data.description(),
                data.definition(),
                aliasesDTO,
                action,
                data.timestamp()
        );
    }

    /**
     * Converts an AliasData to AliasDTO for API responses.
     */
    private AliasDTO toAliasDTO(AliasData data) {
        AliasCodeDTO codeDTO = null;
        if (data.code() != null) {
            codeDTO = new AliasCodeDTO(data.code().old(), data.code().official(), data.code().value());
        }
        return new AliasDTO(codeDTO, data.source(), data.namespace());
    }
}
