// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.Alert;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.AlertRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing SIF 3.7 alerts.
 *
 * <p>Handles alert creation with validation, message context correlation,
 * extended element building, persistence, and ownership-filtered queries.</p>
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Validate required fields (reporter, exchange, level)</li>
 *   <li>Check alerts provider registered in environment-global zone</li>
 *   <li>Look up message context in Redis when messageID present</li>
 *   <li>Default to environment-global zone + DEFAULT context when messageID absent</li>
 *   <li>Reject non-admin reserved extended element names</li>
 *   <li>Build extended elements (server-populated + caller-provided, server wins)</li>
 *   <li>Persist alert entity</li>
 *   <li>Publish event (best-effort, async)</li>
 *   <li>Query alerts with ownership filtering</li>
 * </ul>
 *
 * @see Alert
 * @see AlertRepository
 * @see MessageContextCacheService
 */
@Service
@Transactional
public class AlertsService {

    private static final Logger log = LoggerFactory.getLogger(AlertsService.class);

    private static final String ENVIRONMENT_GLOBAL_ZONE_ID = "environment-global";
    private static final String DEFAULT_CONTEXT_ID = "DEFAULT";
    private static final String ALERTS_SERVICE_NAME = "alerts";

    /** Reserved extended element names that are server-populated. */
    private static final Set<String> RESERVED_EXTENDED_ELEMENT_NAMES = Set.of(
            "reportingEnvironmentFingerprint",
            "sourceEnvironmentId",
            "createdAt"
    );

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final AlertRepository alertRepository;
    private final MessageContextCacheService messageContextCacheService;
    private final ProviderRegistrationRepository providerRegistrationRepository;
    private final ContextRepository contextRepository;
    private final ContextServiceRepository contextServiceRepository;
    private final ZoneRepository zoneRepository;
    private final EnvironmentRepository environmentRepository;
    private final DomainRepository domainRepository;
    private final SolutionRepository solutionRepository;
    private final AlertEventPublisher alertEventPublisher;

    public AlertsService(AlertRepository alertRepository,
                         MessageContextCacheService messageContextCacheService,
                         ProviderRegistrationRepository providerRegistrationRepository,
                         ContextRepository contextRepository,
                         ContextServiceRepository contextServiceRepository,
                         ZoneRepository zoneRepository,
                         EnvironmentRepository environmentRepository,
                         DomainRepository domainRepository,
                         SolutionRepository solutionRepository,
                         AlertEventPublisher alertEventPublisher) {
        this.alertRepository = alertRepository;
        this.messageContextCacheService = messageContextCacheService;
        this.providerRegistrationRepository = providerRegistrationRepository;
        this.contextRepository = contextRepository;
        this.contextServiceRepository = contextServiceRepository;
        this.zoneRepository = zoneRepository;
        this.environmentRepository = environmentRepository;
        this.domainRepository = domainRepository;
        this.solutionRepository = solutionRepository;
        this.alertEventPublisher = alertEventPublisher;
    }

    /**
     * Creates a new alert.
     *
     * <p>Validates required fields, checks provider registration, resolves message
     * context, builds extended elements, persists the alert, and triggers event
     * publication (best-effort).</p>
     *
     * @param domainId   the domain ID from the request path
     * @param solutionId the solution ID from the request path
     * @param request    the alert creation request body
     * @param requester  the authenticated environment making the request
     * @param isAdmin    whether the requester is an admin environment
     * @return the created alert as a DTO
     *
     * <p><strong>Requirements:</strong> 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8,
     * 1.9, 1.10, 2.1, 2.2, 2.3, 2.4, 7.2, 7.3, 7.4, 7.5, 7.8, 8.4, 10.1, 10.2</p>
     */
    public AlertDTO createAlert(String domainId, String solutionId,
                                AlertCreateBody request,
                                Environment requester, boolean isAdmin) {

        log.debug("Creating alert: reporter={}, exchange={}, level={}, messageID={}, isAdmin={}",
                request.reporter(), request.exchange(), request.level(),
                request.messageID(), isAdmin);

        // 1. Validate required fields (Req 1.2, 1.3, 1.4, 1.5)
        validateRequiredFields(request);

        // 2. Resolve solution UUID from domainId/solutionId
        Solution solution = resolveSolution(domainId, solutionId);

        // 3. Find environment-global zone for this solution
        Zone envGlobalZone = zoneRepository
                .findBySolutionUuidAndZoneId(solution.getId(), ENVIRONMENT_GLOBAL_ZONE_ID)
                .orElseThrow(() -> new SifBrokerException(
                        "ALERT_004", HttpStatus.NOT_FOUND,
                        "No provider registered for the alerts service in zone "
                                + ENVIRONMENT_GLOBAL_ZONE_ID
                                + ". Register an alerts provider before creating alerts."));

        // 4. Check alerts provider registered in environment-global zone (Req 10.1, 10.2)
        var defaultContext = contextRepository.findByZoneUuidAndContextId(
                envGlobalZone.getId(), DEFAULT_CONTEXT_ID);
        boolean providerExists = defaultContext
                .flatMap(ctx -> contextServiceRepository.findByContextUuidAndServiceName(
                        ctx.getId(), ALERTS_SERVICE_NAME))
                .flatMap(cs -> providerRegistrationRepository.findByContextServiceUuid(cs.getId()))
                .isPresent();
        if (!providerExists) {
            throw new SifBrokerException(
                    "ALERT_004", HttpStatus.NOT_FOUND,
                    "No provider registered for the alerts service in zone "
                            + ENVIRONMENT_GLOBAL_ZONE_ID
                            + ". Register an alerts provider before creating alerts.");
        }

        // 5. Reject non-admin reserved extended elements (Req 7.3)
        if (!isAdmin) {
            rejectReservedExtendedElements(request.SIF_ExtendedElements());
        }

        // 6. Resolve message context (Req 2.1, 2.2, 2.3, 2.4)
        String sourceEnvironmentId = null;
        String originatingZoneId;
        String originatingContextId;

        if (request.messageID() != null && !request.messageID().isBlank()) {
            // messageID present — look up in Redis
            Optional<MessageContext> messageContext;
            try {
                messageContext = messageContextCacheService.getMessageContext(
                        domainId, solutionId, request.messageID());
            } catch (Exception e) {
                log.error("Failed to read message context cache for messageID: {}",
                        request.messageID(), e);
                throw new SifBrokerException(
                        "ALERT_007", HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to read message context cache");
            }

            if (messageContext.isEmpty()) {
                throw new SifBrokerException(
                        "ALERT_003", HttpStatus.NOT_FOUND,
                        "Message ID '" + request.messageID()
                                + "' not found or expired in message context cache");
            }

            MessageContext ctx = messageContext.get();
            sourceEnvironmentId = ctx.sourceEnvironmentId();
            originatingZoneId = ctx.zoneId();
            originatingContextId = ctx.contextId();
        } else {
            // No messageID — default to environment-global zone + DEFAULT context
            originatingZoneId = ENVIRONMENT_GLOBAL_ZONE_ID;
            originatingContextId = DEFAULT_CONTEXT_ID;
        }

        // 7. Build extended elements (Req 7.2, 7.4, 7.5)
        Instant createdAt = Instant.now();
        List<ExtendedElement> extendedElements = buildExtendedElements(
                requester, sourceEnvironmentId, originatingZoneId,
                originatingContextId, createdAt, request.SIF_ExtendedElements(),
                isAdmin);

        // 8. Build and persist alert entity (Req 1.1, 1.8, 1.9, 1.10)
        Alert alert = new Alert();
        alert.setEnvironment(requester);
        alert.setReporter(request.reporter());
        alert.setCause(request.cause());
        alert.setExchange(request.exchange());
        alert.setLevel(request.level());
        alert.setDescription(request.description());
        alert.setMessageId(request.messageID());
        alert.setBody(request.body());
        alert.setError(request.error());
        alert.setXpath(request.xpath());
        alert.setCategory(request.category());
        alert.setCode(request.code());
        alert.setInternal(request.internal());
        alert.setExtendedElements(extendedElements);
        alert.setCreatedAt(createdAt);

        // Set target environment from resolved sourceEnvironmentId (Req 1.1, 1.2)
        if (sourceEnvironmentId != null) {
            alert.setTargetEnvironmentId(UUID.fromString(sourceEnvironmentId));
        }

        Alert savedAlert = alertRepository.save(alert);

        log.info("Alert created: id={}, reporter={}, exchange={}, level={}, environmentId={}",
                savedAlert.getId(), savedAlert.getReporter(), savedAlert.getExchange(),
                savedAlert.getLevel(), requester.getId());

        // 9. Publish event (best-effort) — Req 5.1, 5.6
        AlertDTO result = mapToAlertDTO(savedAlert);
        try {
            alertEventPublisher.publishAlertCreatedEvent(savedAlert, result, domainId, solutionId);
        } catch (Exception e) {
            log.warn("Alert event publication failed (best-effort): alertId={}, error={}",
                    savedAlert.getId(), e.getMessage());
        }

        return result;
    }

    /**
     * Lists alerts with ownership filtering.
     *
     * <p>Admins see all alerts. Non-admins see only alerts where
     * {@code environment_id} matches the requester.</p>
     *
     * @param requester the authenticated environment
     * @param isAdmin   whether the requester is an admin
     * @return list of alert DTOs
     *
     * <p><strong>Requirements:</strong> 3.1, 3.2, 3.3, 8.2, 8.3</p>
     */
    @Transactional(readOnly = true)
    public List<AlertDTO> listAlerts(Environment requester, boolean isAdmin) {
        log.debug("Listing alerts: environmentId={}, isAdmin={}", requester.getId(), isAdmin);

        List<Alert> alerts;
        if (isAdmin) {
            alerts = alertRepository.findAll();
        } else {
            alerts = alertRepository.findVisibleAlerts(requester.getId());
        }

        log.debug("Found {} alerts for environmentId={}", alerts.size(), requester.getId());
        return alerts.stream().map(this::mapToAlertDTO).toList();
    }

    /**
     * Gets a single alert by ID with visibility check.
     *
     * <p>Admins can retrieve any alert. Non-admins can retrieve alerts they
     * created, alerts directed at them, or broadcast alerts (target is NULL).
     * Returns 404 for non-existent or non-visible alerts (to avoid
     * leaking existence).</p>
     *
     * @param alertId   the alert UUID
     * @param requester the authenticated environment
     * @param isAdmin   whether the requester is an admin
     * @return the alert DTO
     * @throws SifBrokerException with ALERT_005 if not found or not visible
     *
     * <p><strong>Requirements:</strong> 3.1, 3.2, 3.3, 3.4, 3.5</p>
     */
    @Transactional(readOnly = true)
    public AlertDTO getAlertById(UUID alertId, Environment requester, boolean isAdmin) {
        log.debug("Getting alert: id={}, environmentId={}, isAdmin={}",
                alertId, requester.getId(), isAdmin);

        Optional<Alert> alertOpt;
        if (isAdmin) {
            alertOpt = alertRepository.findById(alertId);
        } else {
            alertOpt = alertRepository.findVisibleAlertById(alertId, requester.getId());
        }

        Alert alert = alertOpt.orElseThrow(() -> new SifBrokerException(
                "ALERT_005", HttpStatus.NOT_FOUND,
                "Alert with ID '" + alertId + "' not found"));

        return mapToAlertDTO(alert);
    }

    // ---- Private helpers ----

    /**
     * Validates required fields: reporter (non-blank), exchange (non-null), level (non-null).
     */
    private void validateRequiredFields(AlertCreateBody request) {
        if (request.reporter() == null || request.reporter().isBlank()) {
            throw new SifBrokerException(
                    "ALERT_001", HttpStatus.BAD_REQUEST,
                    "Missing or invalid required field: reporter must be a non-blank string");
        }
        if (request.exchange() == null) {
            throw new SifBrokerException(
                    "ALERT_001", HttpStatus.BAD_REQUEST,
                    "Missing or invalid required field: exchange is required");
        }
        if (request.level() == null) {
            throw new SifBrokerException(
                    "ALERT_001", HttpStatus.BAD_REQUEST,
                    "Missing or invalid required field: level is required");
        }
    }

    /**
     * Rejects non-admin requests that include reserved extended element names.
     */
    private void rejectReservedExtendedElements(SifExtendedElements extendedElements) {
        if (extendedElements == null || extendedElements.SIF_ExtendedElement() == null) {
            return;
        }

        List<String> reservedFound = extendedElements.SIF_ExtendedElement().stream()
                .map(SifExtendedElement::Name)
                .filter(RESERVED_EXTENDED_ELEMENT_NAMES::contains)
                .toList();

        if (!reservedFound.isEmpty()) {
            throw new SifBrokerException(
                    "ALERT_002", HttpStatus.BAD_REQUEST,
                    "Non-admin environments cannot set server-populated extended elements: "
                            + reservedFound);
        }
    }

    /**
     * Resolves the Solution entity from domainId and solutionId path variables.
     */
    private Solution resolveSolution(String domainId, String solutionId) {
        Domain domain = domainRepository.findByDomainId(domainId)
                .orElseThrow(() -> new SifBrokerException(
                        "ALERT_001", HttpStatus.BAD_REQUEST,
                        "Domain '" + domainId + "' not found"));

        return solutionRepository.findByDomainUuidAndSolutionId(domain.getId(), solutionId)
                .orElseThrow(() -> new SifBrokerException(
                        "ALERT_001", HttpStatus.BAD_REQUEST,
                        "Solution '" + solutionId + "' not found in domain '" + domainId + "'"));
    }

    /**
     * Builds the merged extended elements list.
     *
     * <p>Server-populated values:</p>
     * <ul>
     *   <li>{@code reportingEnvironmentFingerprint} — requester's fingerprint</li>
     *   <li>{@code sourceEnvironmentId} — from message context (only when messageID present)</li>
     *   <li>{@code originatingZoneId} — from message context or environment-global default</li>
     *   <li>{@code originatingContextId} — from message context or DEFAULT</li>
     *   <li>{@code createdAt} — ISO 8601 UTC timestamp</li>
     * </ul>
     *
     * <p>Caller-provided custom elements are appended. For reserved names,
     * server values take precedence unless the caller is admin (admin overrides
     * are allowed per Req 7.4, but server still wins per Req 7.5).</p>
     */
    private List<ExtendedElement> buildExtendedElements(
            Environment requester,
            String sourceEnvironmentId,
            String originatingZoneId,
            String originatingContextId,
            Instant createdAt,
            SifExtendedElements callerExtendedElements,
            boolean isAdmin) {

        List<ExtendedElement> elements = new ArrayList<>();

        // Start with caller-provided elements (excluding reserved names — server wins)
        if (callerExtendedElements != null && callerExtendedElements.SIF_ExtendedElement() != null) {
            for (SifExtendedElement callerElem : callerExtendedElements.SIF_ExtendedElement()) {
                if (callerElem.Name() != null
                        && !RESERVED_EXTENDED_ELEMENT_NAMES.contains(callerElem.Name())
                        && !"originatingZoneId".equals(callerElem.Name())
                        && !"originatingContextId".equals(callerElem.Name())) {
                    elements.add(new ExtendedElement(callerElem.Name(), callerElem.value()));
                }
            }
        }

        // Server-populated values (always added, overriding any caller values for reserved names)
        String fingerprint = requester.getFingerprint();
        if (fingerprint != null) {
            elements.add(new ExtendedElement("reportingEnvironmentFingerprint", fingerprint));
        }

        if (sourceEnvironmentId != null) {
            elements.add(new ExtendedElement("sourceEnvironmentId", sourceEnvironmentId));
        }

        elements.add(new ExtendedElement("originatingZoneId", originatingZoneId));
        elements.add(new ExtendedElement("originatingContextId", originatingContextId));
        elements.add(new ExtendedElement("createdAt", ISO_FORMATTER.format(createdAt)));

        return elements;
    }

    /**
     * Maps an Alert entity to an AlertDTO for API responses.
     */
    private AlertDTO mapToAlertDTO(Alert alert) {
        SifExtendedElements sifExtendedElements = null;
        if (alert.getExtendedElements() != null && !alert.getExtendedElements().isEmpty()) {
            List<SifExtendedElement> sifElements = alert.getExtendedElements().stream()
                    .map(e -> new SifExtendedElement(e.name(), e.value()))
                    .toList();
            sifExtendedElements = new SifExtendedElements(sifElements);
        }

        return new AlertDTO(
                alert.getId(),
                alert.getReporter(),
                alert.getCause(),
                alert.getExchange(),
                alert.getLevel(),
                alert.getDescription(),
                alert.getMessageId(),
                alert.getBody(),
                alert.getError(),
                alert.getXpath(),
                alert.getCategory(),
                alert.getCode(),
                alert.getInternal(),
                sifExtendedElements
        );
    }
}
