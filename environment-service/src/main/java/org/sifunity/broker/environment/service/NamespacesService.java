// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Namespace;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.NamespaceRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.util.*;

/**
 * Service for managing SIF 3.7 namespaces.
 *
 * <p>Handles namespace CRUD operations with validation, zone-scoping logic,
 * provider registration checks, duplicate detection, and DTO conversion.</p>
 *
 * <p>Key differences from {@link CodeSetsService}:</p>
 * <ul>
 *   <li>No codeItems validation — namespaces are flat objects</li>
 *   <li>No JSONB conversion — all fields are simple VARCHAR</li>
 *   <li>{@code id} is broker-assigned UUID (any provided id on create is ignored)</li>
 *   <li>Duplicate detection by {@code (uri, zone, solutionUuid)} not {@code (codeSetId, zone, solutionUuid)}</li>
 *   <li>Zone-scoping override key is {@code uri} (not {@code codeSetId})</li>
 *   <li>Field length validation: uri max 2048, url max 2048</li>
 * </ul>
 *
 * @see Namespace
 * @see NamespaceRepository
 */
@Service
@Transactional
public class NamespacesService {

    private static final Logger log = LoggerFactory.getLogger(NamespacesService.class);

    private static final String ENVIRONMENT_GLOBAL_ZONE_ID = "environment-global";
    private static final String NAMESPACES_SERVICE_NAME = "namespaces";

    private static final int MAX_URI_LENGTH = 2048;
    private static final int MAX_URL_LENGTH = 2048;

    private final NamespaceRepository namespaceRepository;
    private final ProviderRegistrationRepository providerRegistrationRepository;
    private final ContextRepository contextRepository;
    private final ContextServiceRepository contextServiceRepository;
    private final ZoneRepository zoneRepository;
    private final DomainRepository domainRepository;
    private final SolutionRepository solutionRepository;
    private final NamespaceEventPublisher namespaceEventPublisher;
    private final MeterRegistry meterRegistry;

    public NamespacesService(NamespaceRepository namespaceRepository,
                             ProviderRegistrationRepository providerRegistrationRepository,
                             ContextRepository contextRepository,
                             ContextServiceRepository contextServiceRepository,
                             ZoneRepository zoneRepository,
                             DomainRepository domainRepository,
                             SolutionRepository solutionRepository,
                             NamespaceEventPublisher namespaceEventPublisher,
                             MeterRegistry meterRegistry) {
        this.namespaceRepository = namespaceRepository;
        this.providerRegistrationRepository = providerRegistrationRepository;
        this.contextRepository = contextRepository;
        this.contextServiceRepository = contextServiceRepository;
        this.zoneRepository = zoneRepository;
        this.domainRepository = domainRepository;
        this.solutionRepository = solutionRepository;
        this.namespaceEventPublisher = namespaceEventPublisher;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Creates a new namespace.
     *
     * <p>Validates required fields, checks provider registration, detects duplicates,
     * assigns broker-generated UUID id (ignoring any provided id), persists, and returns DTO.</p>
     *
     * @param domainId   domain ID from request path
     * @param solutionId solution ID from request path
     * @param body       namespace creation body
     * @return created namespace as DTO
     */
    public NamespaceDTO createNamespace(String domainId, String solutionId, NamespaceCreateBody body) {
        log.debug("Creating namespace: zone={}, uri={}", body.zone(), body.uri());

        // 1. Validate required fields
        validateRequiredFields(body.zone(), body.uri(), body.url());

        // 2. Validate field lengths
        validateFieldLengths(body.uri(), body.url());

        // 3. Resolve solution UUID
        Solution solution = resolveSolution(domainId, solutionId);

        // 4. Check namespaces provider registered in environment-global zone
        checkProviderRegistered(solution.getId());

        // 5. Check duplicate (uri + zone + solutionUuid) → 409
        Optional<Namespace> existing = namespaceRepository.findByUriAndZoneAndSolutionUuid(
                body.uri(), body.zone(), solution.getId());
        if (existing.isPresent()) {
            throw new SifBrokerException(
                    "NAMESPACE_007", HttpStatus.CONFLICT,
                    "A namespace with URI '" + body.uri() + "' already exists in zone '" + body.zone() + "'.");
        }

        // 6. Build and persist entity — id is broker-assigned UUID (ignore any provided id)
        Namespace namespace = new Namespace();
        namespace.setZone(body.zone());
        namespace.setUri(body.uri());
        namespace.setUrl(body.url());
        namespace.setSolutionUuid(solution.getId());

        Namespace saved = namespaceRepository.save(namespace);

        log.info("Namespace created: id={}, zone={}, uri={}, solutionUuid={}",
                saved.getId(), saved.getZone(), saved.getUri(), saved.getSolutionUuid());

        // 7. Publish event (best-effort)
        NamespaceDTO dto = mapToDTO(saved);
        try {
            namespaceEventPublisher.publishNamespaceCreatedEvent(dto, domainId, solutionId);
        } catch (Exception e) {
            log.warn("Namespace CREATE event publication failed (best-effort): id={}, error={}",
                    saved.getId(), e.getMessage());
        }

        return dto;
    }

    /**
     * Lists namespaces for a solution with zone-scoping logic.
     *
     * @param domainId   domain ID from request path
     * @param solutionId solution ID from request path
     * @param zoneId     effective zone ID for scoping
     * @return list of zone-scoped namespace DTOs
     */
    @Transactional(readOnly = true)
    public List<NamespaceDTO> listNamespaces(String domainId, String solutionId, String zoneId) {
        log.debug("Listing namespaces: domainId={}, solutionId={}, zoneId={}", domainId, solutionId, zoneId);

        Solution solution = resolveSolution(domainId, solutionId);
        List<Namespace> allNamespaces = namespaceRepository.findBySolutionUuid(solution.getId());

        List<Namespace> scoped = applyZoneScoping(allNamespaces, zoneId);

        log.debug("Found {} namespaces after zone-scoping (zoneId={})", scoped.size(), zoneId);
        return scoped.stream().map(this::mapToDTO).toList();
    }

    /**
     * Gets a single namespace by UUID id.
     *
     * @param domainId   domain ID from request path
     * @param solutionId solution ID from request path
     * @param id         namespace UUID identifier
     * @return namespace DTO
     * @throws SifBrokerException with NAMESPACE_005 if not found
     */
    @Transactional(readOnly = true)
    public NamespaceDTO getNamespaceById(String domainId, String solutionId, UUID id) {
        log.debug("Getting namespace: id={}", id);

        Solution solution = resolveSolution(domainId, solutionId);

        Namespace namespace = namespaceRepository.findById(id)
                .filter(ns -> ns.getSolutionUuid().equals(solution.getId()))
                .orElseThrow(() -> new SifBrokerException(
                        "NAMESPACE_005", HttpStatus.NOT_FOUND,
                        "Namespace with ID '" + id + "' not found"));

        return mapToDTO(namespace);
    }

    /**
     * Updates an existing namespace (full replacement).
     *
     * @param domainId   domain ID from request path
     * @param solutionId solution ID from request path
     * @param id         namespace UUID identifier
     * @param body       update request body
     * @return updated namespace as DTO
     * @throws SifBrokerException with NAMESPACE_005 if not found
     */
    public NamespaceDTO updateNamespace(String domainId, String solutionId, UUID id, NamespaceCreateBody body) {
        log.debug("Updating namespace: id={}, zone={}, uri={}", id, body.zone(), body.uri());

        // 1. Validate required fields
        validateRequiredFields(body.zone(), body.uri(), body.url());

        // 2. Validate field lengths
        validateFieldLengths(body.uri(), body.url());

        // 3. Resolve solution and find existing namespace
        Solution solution = resolveSolution(domainId, solutionId);

        Namespace existing = namespaceRepository.findById(id)
                .filter(ns -> ns.getSolutionUuid().equals(solution.getId()))
                .orElseThrow(() -> new SifBrokerException(
                        "NAMESPACE_005", HttpStatus.NOT_FOUND,
                        "Namespace with ID '" + id + "' not found"));

        // 4. Check duplicate (uri + zone + solutionUuid excluding self) → 409
        Optional<Namespace> duplicate = namespaceRepository.findByUriAndZoneAndSolutionUuid(
                body.uri(), body.zone(), solution.getId());
        if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
            throw new SifBrokerException(
                    "NAMESPACE_007", HttpStatus.CONFLICT,
                    "A namespace with URI '" + body.uri() + "' already exists in zone '" + body.zone() + "'.");
        }

        // 5. Full replacement of all fields
        existing.setZone(body.zone());
        existing.setUri(body.uri());
        existing.setUrl(body.url());

        Namespace saved = namespaceRepository.save(existing);

        log.info("Namespace updated: id={}, zone={}, uri={}", saved.getId(), saved.getZone(), saved.getUri());

        // 6. Publish event (best-effort)
        NamespaceDTO dto = mapToDTO(saved);
        try {
            namespaceEventPublisher.publishNamespaceUpdatedEvent(dto, domainId, solutionId);
        } catch (Exception e) {
            log.warn("Namespace UPDATE event publication failed (best-effort): id={}, error={}",
                    saved.getId(), e.getMessage());
        }

        return dto;
    }

    /**
     * Deletes a namespace by UUID id.
     *
     * @param domainId   domain ID from request path
     * @param solutionId solution ID from request path
     * @param id         namespace UUID identifier
     * @throws SifBrokerException with NAMESPACE_005 if not found
     */
    public void deleteNamespace(String domainId, String solutionId, UUID id) {
        log.debug("Deleting namespace: id={}", id);

        Solution solution = resolveSolution(domainId, solutionId);

        Namespace existing = namespaceRepository.findById(id)
                .filter(ns -> ns.getSolutionUuid().equals(solution.getId()))
                .orElseThrow(() -> new SifBrokerException(
                        "NAMESPACE_005", HttpStatus.NOT_FOUND,
                        "Namespace with ID '" + id + "' not found"));

        // Build DTO before delete for event publication
        NamespaceDTO dto = mapToDTO(existing);

        namespaceRepository.delete(existing);

        log.info("Namespace deleted: id={}, zone={}, uri={}", existing.getId(), existing.getZone(), existing.getUri());

        // Publish event (best-effort)
        try {
            namespaceEventPublisher.publishNamespaceDeletedEvent(dto, domainId, solutionId);
        } catch (Exception e) {
            log.warn("Namespace DELETE event publication failed (best-effort): id={}, error={}",
                    existing.getId(), e.getMessage());
        }
    }

    // ---- Validation helpers ----

    /**
     * Validates required fields: zone (non-blank), uri (non-blank), url (non-blank).
     */
    private void validateRequiredFields(String zone, String uri, String url) {
        if (zone == null || zone.isBlank()) {
            throw new SifBrokerException(
                    "NAMESPACE_001", HttpStatus.BAD_REQUEST,
                    "Missing or invalid required field: zone must be a non-blank string");
        }
        if (uri == null || uri.isBlank()) {
            throw new SifBrokerException(
                    "NAMESPACE_001", HttpStatus.BAD_REQUEST,
                    "Missing or invalid required field: uri must be a non-blank string");
        }
        if (url == null || url.isBlank()) {
            throw new SifBrokerException(
                    "NAMESPACE_001", HttpStatus.BAD_REQUEST,
                    "Missing or invalid required field: url must be a non-blank string");
        }
    }

    /**
     * Validates field lengths: uri max 2048, url max 2048.
     */
    private void validateFieldLengths(String uri, String url) {
        if (uri != null && uri.length() > MAX_URI_LENGTH) {
            throw new SifBrokerException(
                    "NAMESPACE_002", HttpStatus.BAD_REQUEST,
                    "Field 'uri' exceeds maximum length of " + MAX_URI_LENGTH + " characters");
        }
        if (url != null && url.length() > MAX_URL_LENGTH) {
            throw new SifBrokerException(
                    "NAMESPACE_002", HttpStatus.BAD_REQUEST,
                    "Field 'url' exceeds maximum length of " + MAX_URL_LENGTH + " characters");
        }
    }

    /**
     * Checks that a namespaces provider is registered in the environment-global zone.
     */
    private void checkProviderRegistered(java.util.UUID solutionUuid) {
        boolean providerExists = zoneRepository.findBySolutionUuidAndZoneId(solutionUuid, ENVIRONMENT_GLOBAL_ZONE_ID)
                .flatMap(zone -> contextRepository.findByZoneUuidAndContextId(zone.getId(), "DEFAULT"))
                .flatMap(ctx -> contextServiceRepository.findByContextUuidAndServiceName(
                        ctx.getId(), NAMESPACES_SERVICE_NAME))
                .flatMap(cs -> providerRegistrationRepository.findByContextServiceUuid(cs.getId()))
                .isPresent();
        if (!providerExists) {
            throw new SifBrokerException(
                    "NAMESPACE_006", HttpStatus.NOT_FOUND,
                    "No provider registered for the namespaces service in zone "
                            + ENVIRONMENT_GLOBAL_ZONE_ID + ".");
        }
    }

    /**
     * Resolves the Solution entity from domainId and solutionId path variables.
     */
    private Solution resolveSolution(String domainId, String solutionId) {
        Domain domain = domainRepository.findByDomainId(domainId)
                .orElseThrow(() -> new SifBrokerException(
                        "NAMESPACE_001", HttpStatus.BAD_REQUEST,
                        "Domain '" + domainId + "' not found"));

        return solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), solutionId)
                .orElseThrow(() -> new SifBrokerException(
                        "NAMESPACE_001", HttpStatus.BAD_REQUEST,
                        "Solution '" + solutionId + "' not found in domain '" + domainId + "'"));
    }

    // ---- Zone-scoping logic ----

    /**
     * Applies zone-scoping logic to a list of namespaces.
     *
     * <p>Override key is {@code uri} (not {@code id} like code sets).</p>
     *
     * <ul>
     *   <li>If zoneId = "environment-global" → return all (no override logic)</li>
     *   <li>Otherwise: start with globals, override with zone-specific entries sharing same uri,
     *       include zone-only entries</li>
     * </ul>
     */
    private List<Namespace> applyZoneScoping(List<Namespace> allNamespaces, String zoneId) {
        return Timer.builder("sif.zone.scoping.duration")
                .description("Zone-scoping filter latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
                .record(() -> {
                    // environment-global returns everything
                    if (ENVIRONMENT_GLOBAL_ZONE_ID.equals(zoneId)) {
                        return allNamespaces;
                    }

                    // Start with globals, override with zone-specific
                    Map<String, Namespace> result = new LinkedHashMap<>();

                    // Add all global namespaces first
                    for (Namespace ns : allNamespaces) {
                        if (ENVIRONMENT_GLOBAL_ZONE_ID.equals(ns.getZone())) {
                            result.put(ns.getUri(), ns);
                        }
                    }

                    // Override with zone-specific namespaces (same uri replaces global) + add zone-only entries
                    for (Namespace ns : allNamespaces) {
                        if (zoneId.equals(ns.getZone())) {
                            result.put(ns.getUri(), ns);
                        }
                    }

                    return new ArrayList<>(result.values());
                });
    }

    // ---- DTO conversion ----

    /**
     * Maps a Namespace entity to a NamespaceDTO for API responses.
     */
    NamespaceDTO mapToDTO(Namespace entity) {
        return new NamespaceDTO(
                entity.getId().toString(),
                entity.getZone(),
                entity.getUri(),
                entity.getUrl()
        );
    }
}
