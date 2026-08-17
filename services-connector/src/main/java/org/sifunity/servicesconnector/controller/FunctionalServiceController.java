// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.servicesconnector.service.FunctionalServiceRoutingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for SIF 3.7 functional service operations.
 *
 * <p>Acts as a pass-through proxy: accepts raw JSON bodies, extracts routing
 * parameters from URL path variables and SIF headers, and delegates to
 * {@link FunctionalServiceRoutingService}. The broker does not interpret or
 * persist job/phase state — all state is managed by the external provider.</p>
 *
 * <p>Mirrors the {@code RequestController} pattern from requests-connector,
 * branching on the {@code requestType} header (IMMEDIATE vs DELAYED).</p>
 *
 * @see FunctionalServiceRoutingService
 */
@RestController
@RequestMapping("/{domainId}/{solutionId}/services")
@Slf4j
public class FunctionalServiceController {

    private static final String CONNECTOR_NAME = "services-connector";

    private final FunctionalServiceRoutingService routingService;
    private final HeaderSanitizer headerSanitizer;
    private final ConnectorMetricsService connectorMetricsService;

    public FunctionalServiceController(FunctionalServiceRoutingService routingService,
                                       HeaderSanitizer headerSanitizer,
                                       ConnectorMetricsService connectorMetricsService) {
        this.routingService = routingService;
        this.headerSanitizer = headerSanitizer;
        this.connectorMetricsService = connectorMetricsService;
        log.info("FunctionalServiceController initialized");
    }

    // ── Job-level operations ────────────────────────────────────────────

    /**
     * Create a new job for the given functional service.
     * POST /{domainId}/{solutionId}/services/{functionalServiceName}
     */
    @PostMapping("/{functionalServiceName}")
    public ResponseEntity<Object> createJob(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String functionalServiceName,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader("requestType") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            @RequestBody Object requestBody,
            HttpServletRequest httpServletRequest) {

        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }

        log.info("POST /services/{} - requestType={}, zoneId={}, contextId={}",
                functionalServiceName, requestType, zoneId, contextId);

        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);

        return routeRequest("POST", functionalServiceName, null, null,
                environmentId, environmentName, domainId, solutionId,
                zoneId, contextId, requestType, queueId, requestId,
                requestBody, passthroughHeaders);
    }

    /**
     * Update a functional service (service-level PUT).
     * PUT /{domainId}/{solutionId}/services/{functionalServiceName}
     */
    @PutMapping("/{functionalServiceName}")
    public ResponseEntity<Object> updateService(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String functionalServiceName,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader("requestType") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            @RequestBody Object requestBody,
            HttpServletRequest httpServletRequest) {

        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }

        log.info("PUT /services/{} - requestType={}, zoneId={}, contextId={}",
                functionalServiceName, requestType, zoneId, contextId);

        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);

        return routeRequest("PUT", functionalServiceName, null, null,
                environmentId, environmentName, domainId, solutionId,
                zoneId, contextId, requestType, queueId, requestId,
                requestBody, passthroughHeaders);
    }

    /**
     * List jobs for the given functional service.
     * GET /{domainId}/{solutionId}/services/{functionalServiceName}
     */
    @GetMapping("/{functionalServiceName}")
    public ResponseEntity<Object> listJobs(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String functionalServiceName,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader("requestType") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            HttpServletRequest httpServletRequest) {

        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }

        log.info("GET /services/{} - requestType={}, zoneId={}, contextId={}",
                functionalServiceName, requestType, zoneId, contextId);

        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);

        return routeRequest("GET", functionalServiceName, null, null,
                environmentId, environmentName, domainId, solutionId,
                zoneId, contextId, requestType, queueId, requestId,
                null, passthroughHeaders);
    }

    /**
     * Get a specific job.
     * GET /{domainId}/{solutionId}/services/{functionalServiceName}/{jobId}
     */
    @GetMapping("/{functionalServiceName}/{jobId}")
    public ResponseEntity<Object> getJob(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String functionalServiceName,
            @PathVariable String jobId,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader("requestType") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            HttpServletRequest httpServletRequest) {

        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }

        log.info("GET /services/{}/{} - requestType={}", functionalServiceName, jobId, requestType);

        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);

        return routeRequest("GET", functionalServiceName, jobId, null,
                environmentId, environmentName, domainId, solutionId,
                zoneId, contextId, requestType, queueId, requestId,
                null, passthroughHeaders);
    }

    /**
     * Delete a functional service (service-level).
     * DELETE /{domainId}/{solutionId}/services/{functionalServiceName}
     */
    @DeleteMapping("/{functionalServiceName}")
    public ResponseEntity<Object> deleteService(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String functionalServiceName,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader("requestType") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            HttpServletRequest httpServletRequest) {

        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }

        log.info("DELETE /services/{} - requestType={}, zoneId={}, contextId={}",
                functionalServiceName, requestType, zoneId, contextId);

        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);

        return routeRequest("DELETE", functionalServiceName, null, null,
                environmentId, environmentName, domainId, solutionId,
                zoneId, contextId, requestType, queueId, requestId,
                null, passthroughHeaders);
    }

    /**
     * Delete a specific job.
     * DELETE /{domainId}/{solutionId}/services/{functionalServiceName}/{jobId}
     */
    @DeleteMapping("/{functionalServiceName}/{jobId}")
    public ResponseEntity<Object> deleteJob(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String functionalServiceName,
            @PathVariable String jobId,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader("requestType") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            HttpServletRequest httpServletRequest) {

        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }

        log.info("DELETE /services/{}/{} - requestType={}", functionalServiceName, jobId, requestType);

        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);

        return routeRequest("DELETE", functionalServiceName, jobId, null,
                environmentId, environmentName, domainId, solutionId,
                zoneId, contextId, requestType, queueId, requestId,
                null, passthroughHeaders);
    }

    // ── Phase-level operations ──────────────────────────────────────────

    /**
     * Create or submit data to a phase.
     * POST /{domainId}/{solutionId}/services/{functionalServiceName}/{jobId}/{phaseName}
     */
    @PostMapping("/{functionalServiceName}/{jobId}/{phaseName}")
    public ResponseEntity<Object> createPhaseData(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String functionalServiceName,
            @PathVariable String jobId,
            @PathVariable String phaseName,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader("requestType") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            @RequestBody Object requestBody,
            HttpServletRequest httpServletRequest) {

        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }

        log.info("POST /services/{}/{}/{} - requestType={}", functionalServiceName, jobId, phaseName, requestType);

        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);

        return routeRequest("POST", functionalServiceName, jobId, phaseName,
                environmentId, environmentName, domainId, solutionId,
                zoneId, contextId, requestType, queueId, requestId,
                requestBody, passthroughHeaders);
    }

    /**
     * Update phase data.
     * PUT /{domainId}/{solutionId}/services/{functionalServiceName}/{jobId}/{phaseName}
     */
    @PutMapping("/{functionalServiceName}/{jobId}/{phaseName}")
    public ResponseEntity<Object> updatePhaseData(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String functionalServiceName,
            @PathVariable String jobId,
            @PathVariable String phaseName,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader("requestType") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            @RequestBody Object requestBody,
            HttpServletRequest httpServletRequest) {

        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }

        log.info("PUT /services/{}/{}/{} - requestType={}", functionalServiceName, jobId, phaseName, requestType);

        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);

        return routeRequest("PUT", functionalServiceName, jobId, phaseName,
                environmentId, environmentName, domainId, solutionId,
                zoneId, contextId, requestType, queueId, requestId,
                requestBody, passthroughHeaders);
    }

    /**
     * Get phase data.
     * GET /{domainId}/{solutionId}/services/{functionalServiceName}/{jobId}/{phaseName}
     */
    @GetMapping("/{functionalServiceName}/{jobId}/{phaseName}")
    public ResponseEntity<Object> getPhaseData(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String functionalServiceName,
            @PathVariable String jobId,
            @PathVariable String phaseName,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader("requestType") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            HttpServletRequest httpServletRequest) {

        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }

        log.info("GET /services/{}/{}/{} - requestType={}", functionalServiceName, jobId, phaseName, requestType);

        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);

        return routeRequest("GET", functionalServiceName, jobId, phaseName,
                environmentId, environmentName, domainId, solutionId,
                zoneId, contextId, requestType, queueId, requestId,
                null, passthroughHeaders);
    }

    // ── State operations ────────────────────────────────────────────────

    /**
     * Get phase states.
     * GET /{domainId}/{solutionId}/services/{functionalServiceName}/{jobId}/{phaseName}/states
     */
    @GetMapping("/{functionalServiceName}/{jobId}/{phaseName}/states")
    public ResponseEntity<Object> getPhaseStates(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String functionalServiceName,
            @PathVariable String jobId,
            @PathVariable String phaseName,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader("requestType") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            HttpServletRequest httpServletRequest) {

        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }

        log.info("GET /services/{}/{}/{}/states - requestType={}", functionalServiceName, jobId, phaseName, requestType);

        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);

        return routeRequest("GET", functionalServiceName, jobId, phaseName + "/states",
                environmentId, environmentName, domainId, solutionId,
                zoneId, contextId, requestType, queueId, requestId,
                null, passthroughHeaders);
    }

    /**
     * Create or update phase state.
     * POST /{domainId}/{solutionId}/services/{functionalServiceName}/{jobId}/{phaseName}/states/state
     */
    @PostMapping("/{functionalServiceName}/{jobId}/{phaseName}/states/state")
    public ResponseEntity<Object> createOrUpdatePhaseState(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable String functionalServiceName,
            @PathVariable String jobId,
            @PathVariable String phaseName,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestHeader("requestType") String requestType,
            @RequestHeader(value = "queueId", required = false) UUID queueId,
            @RequestHeader(value = "requestId", required = false) String requestId,
            @RequestBody Object requestBody,
            HttpServletRequest httpServletRequest) {

        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                return ResponseEntity.badRequest().body("zoneId is required but no default is available");
            }
        }

        log.info("POST /services/{}/{}/{}/states/state - requestType={}", functionalServiceName, jobId, phaseName, requestType);

        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);

        return routeRequest("POST", functionalServiceName, jobId, phaseName + "/states/state",
                environmentId, environmentName, domainId, solutionId,
                zoneId, contextId, requestType, queueId, requestId,
                requestBody, passthroughHeaders);
    }

    // ── Shared routing logic ────────────────────────────────────────────

    /**
     * Routes the request to the appropriate handler based on the {@code requestType} header.
     * Mirrors the IMMEDIATE/DELAYED branching in {@code RequestController}.
     */
    private ResponseEntity<Object> routeRequest(
            String httpMethod,
            String functionalServiceName,
            String jobId,
            String pathSuffix,
            UUID environmentId,
            String environmentName,
            String domainId,
            String solutionId,
            String zoneId,
            String contextId,
            String requestType,
            UUID queueId,
            String requestId,
            Object requestBody,
            Map<String, String> passthroughHeaders) {

        long startTime = System.currentTimeMillis();

        // Record SIF-level connector request metric on receipt
        connectorMetricsService.recordConnectorRequest(
            CONNECTOR_NAME, functionalServiceName, zoneId,
            httpMethod, 0);

        try {
            ResponseEntity<Object> result;

            if ("IMMEDIATE".equalsIgnoreCase(requestType)) {
                Object response = routingService.routeImmediateRequest(
                        httpMethod, functionalServiceName, jobId, pathSuffix,
                        environmentId, environmentName, domainId, solutionId, zoneId, contextId,
                        requestBody, passthroughHeaders);

                int status = HttpStatus.OK.value();
                long durationMs = System.currentTimeMillis() - startTime;
                connectorMetricsService.recordConnectorRequestDuration(
                    CONNECTOR_NAME, functionalServiceName, status, durationMs);

                result = ResponseEntity.ok(response);

            } else if ("DELAYED".equalsIgnoreCase(requestType)) {
                if (queueId == null) {
                    throw new IllegalArgumentException("queueId header is required for DELAYED requests");
                }

                Object response = routingService.routeDelayedRequest(
                        httpMethod, functionalServiceName, jobId, pathSuffix,
                        environmentId, environmentName, domainId, solutionId,
                        zoneId, contextId, queueId, requestId,
                        requestBody, passthroughHeaders);

                int status = HttpStatus.ACCEPTED.value();
                long durationMs = System.currentTimeMillis() - startTime;
                connectorMetricsService.recordConnectorRequestDuration(
                    CONNECTOR_NAME, functionalServiceName, status, durationMs);

                result = ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

            } else {
                throw new IllegalArgumentException(
                        "Invalid requestType: " + requestType + ". Must be IMMEDIATE or DELAYED");
            }

            return result;
        } catch (Exception ex) {
            // Record SIF-level error metric
            String errorType = ex.getClass().getSimpleName();
            connectorMetricsService.recordConnectorError(
                CONNECTOR_NAME, functionalServiceName, zoneId, errorType);

            // Record duration even on error
            long durationMs = System.currentTimeMillis() - startTime;
            connectorMetricsService.recordConnectorRequestDuration(
                CONNECTOR_NAME, functionalServiceName, 500, durationMs);

            throw ex;
        }
    }
}
