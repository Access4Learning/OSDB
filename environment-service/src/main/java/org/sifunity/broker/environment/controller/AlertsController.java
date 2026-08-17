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
import org.sifunity.broker.environment.service.AlertsService;
import org.sifunity.broker.environment.service.AuthorizationService;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for SIF 3.7 Alerts Utility Service.
 *
 * <p>Handles alert creation, listing, and retrieval. Update and delete
 * operations are rejected with 405 per the SIF 3.7 specification
 * (alerts are immutable).</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST {@code /alerts/alert} — create alert (201)</li>
 *   <li>GET {@code /alerts} — list alerts (200)</li>
 *   <li>GET {@code /alerts/{id}} — get alert by ID (200)</li>
 *   <li>PUT/DELETE {@code /alerts}, {@code /alerts/{id}} — rejected (405)</li>
 * </ul>
 *
 * <p><strong>Requirements:</strong> 1.1, 3.1, 3.4, 4.1, 4.2, 4.3, 4.4, 7.6, 7.7, 8.1</p>
 *
 * @see AlertsService
 * @see AuthorizationService
 */
@Tag(name = "Internal API", description = "Internal management endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}/alerts")
public class AlertsController {

    private static final Logger log = LoggerFactory.getLogger(AlertsController.class);

    private static final String METHOD_NOT_ALLOWED_MESSAGE =
            "Method not allowed. Alerts are immutable per SIF 3.7 specification.";

    private final AlertsService alertsService;
    private final AuthorizationService authorizationService;

    public AlertsController(AlertsService alertsService, AuthorizationService authorizationService) {
        this.alertsService = alertsService;
        this.authorizationService = authorizationService;
    }

    /**
     * Creates a new alert.
     *
     * <p>Validates the request, delegates to {@link AlertsService#createAlert},
     * and wraps the result in an {@link AlertResponse} envelope.</p>
     *
     * @param domainId   domain ID from path
     * @param solutionId solution ID from path
     * @param request    alert creation request body wrapped under {@code alert} key
     * @param httpRequest servlet request (carries requester attribute)
     * @return 201 Created with {@link AlertResponse}
     *
     * <p><strong>Requirements:</strong> 1.1, 7.6, 8.1, 8.4</p>
     */
    @PostMapping("/alert")
    public ResponseEntity<AlertResponse> createAlert(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @Valid @RequestBody AlertCreateRequest request,
            HttpServletRequest httpRequest) {

        validateServiceTypeHeader(httpRequest);

        if (request.alert() == null) {
            throw new SifBrokerException(
                    "ALERT_001", HttpStatus.BAD_REQUEST,
                    "Request body must contain 'alert' object wrapper per SIF 3.7 specification");
        }

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        boolean isAdmin = authorizationService.isAdmin(requester);

        log.info("Creating alert: domainId={}, solutionId={}, reporter={}, isAdmin={}",
                domainId, solutionId, request.alert().reporter(), isAdmin);

        AlertDTO alertDTO = alertsService.createAlert(
                domainId, solutionId, request.alert(), requester, isAdmin);

        log.info("Alert created: id={}", alertDTO.id());

        return ResponseEntity.status(HttpStatus.CREATED).body(new AlertResponse(alertDTO));
    }

    /**
     * Lists alerts with ownership filtering.
     *
     * <p>Admins see all alerts. Non-admins see only their own.</p>
     *
     * @param domainId   domain ID from path
     * @param solutionId solution ID from path
     * @param httpRequest servlet request (carries requester attribute)
     * @return 200 OK with {@link AlertCollectionResponse}
     *
     * <p><strong>Requirements:</strong> 3.1, 7.7, 8.1</p>
     */
    @GetMapping
    public ResponseEntity<AlertCollectionResponse> listAlerts(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            HttpServletRequest httpRequest) {

        validateServiceTypeHeader(httpRequest);

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        boolean isAdmin = authorizationService.isAdmin(requester);

        log.debug("Listing alerts: domainId={}, solutionId={}, environmentId={}, isAdmin={}",
                domainId, solutionId, requester.getId(), isAdmin);

        List<AlertDTO> alerts = alertsService.listAlerts(requester, isAdmin);

        log.debug("Returning {} alerts", alerts.size());

        return ResponseEntity.ok(new AlertCollectionResponse(new AlertCollection(alerts)));
    }

    /**
     * Retrieves a single alert by ID.
     *
     * @param domainId   domain ID from path
     * @param solutionId solution ID from path
     * @param id         alert UUID
     * @param httpRequest servlet request (carries requester attribute)
     * @return 200 OK with {@link AlertResponse}
     *
     * <p><strong>Requirements:</strong> 3.4, 7.6, 8.1</p>
     */
    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> getAlert(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        validateServiceTypeHeader(httpRequest);

        Environment requester = (Environment) httpRequest.getAttribute("requester");
        boolean isAdmin = authorizationService.isAdmin(requester);

        log.debug("Getting alert: id={}, environmentId={}, isAdmin={}",
                id, requester.getId(), isAdmin);

        AlertDTO alertDTO = alertsService.getAlertById(id, requester, isAdmin);

        return ResponseEntity.ok(new AlertResponse(alertDTO));
    }

    // ---- 405 Method Not Allowed endpoints (alerts are immutable) ----

    /**
     * Rejects PUT on single alert.
     *
     * <p><strong>Requirements:</strong> 4.1</p>
     */
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateAlert(@PathVariable String domainId,
                                            @PathVariable String solutionId,
                                            @PathVariable UUID id) {
        throw new SifBrokerException("ALERT_006", HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED_MESSAGE);
    }

    /**
     * Rejects DELETE on single alert.
     *
     * <p><strong>Requirements:</strong> 4.2</p>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable String domainId,
                                            @PathVariable String solutionId,
                                            @PathVariable UUID id) {
        throw new SifBrokerException("ALERT_006", HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED_MESSAGE);
    }

    /**
     * Rejects PUT on alerts collection.
     *
     * <p><strong>Requirements:</strong> 4.3</p>
     */
    @PutMapping
    public ResponseEntity<Void> updateAlerts(@PathVariable String domainId,
                                             @PathVariable String solutionId) {
        throw new SifBrokerException("ALERT_006", HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED_MESSAGE);
    }

    /**
     * Rejects DELETE on alerts collection.
     *
     * <p><strong>Requirements:</strong> 4.4</p>
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteAlerts(@PathVariable String domainId,
                                             @PathVariable String solutionId) {
        throw new SifBrokerException("ALERT_006", HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED_MESSAGE);
    }

    /**
     * Rejects POST on alerts collection.
     * SIF 3.7 does not define bulk create on the collection URL.
     * Create individual alerts via POST /alerts/alert.
     *
     * <p><strong>Requirements:</strong> 2.2</p>
     */
    @PostMapping
    public ResponseEntity<Void> createAlerts(@PathVariable String domainId,
                                             @PathVariable String solutionId) {
        throw new SifBrokerException("ALERT_006", HttpStatus.METHOD_NOT_ALLOWED,
                "POST to alerts collection is not supported. Use POST /alerts/alert to create individual alerts.");
    }

    /**
     * Validates the {@code serviceType} header if present.
     * Alerts is a UTILITY service — if the header is provided with any other value, reject with 400.
     * If the header is absent, we accept the request (lenient — the spec says it defaults to OBJECT,
     * but since the URL path already identifies this as the alerts service, we don't require it).
     */
    private void validateServiceTypeHeader(HttpServletRequest httpRequest) {
        String serviceType = httpRequest.getHeader("serviceType");
        if (serviceType != null && !serviceType.isBlank() && !"UTILITY".equalsIgnoreCase(serviceType)) {
            throw new SifBrokerException(
                    "ALERT_001", HttpStatus.BAD_REQUEST,
                    "Invalid serviceType header: '" + serviceType
                            + "'. The alerts service is a UTILITY service. Set serviceType to UTILITY or omit the header.");
        }
    }
}
