// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.sifunity.responsesconnector.dto.ResponseResultDTO;
import org.sifunity.responsesconnector.service.ResponseDestinationValidator;
import org.sifunity.responsesconnector.service.ResponseRoutingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for handling provider responses per the SIF 3.7 specification.
 * <p>
 * Providers POST raw SIF payloads to {@code POST /{domainId}/{solutionId}/responses}
 * with service metadata in HTTP headers. The controller builds the internal
 * {@link ResponseDTO} from headers and the raw payload.
 * </p>
 *
 * <p><b>Endpoints:</b></p>
 * <ul>
 *   <li>POST /{domainId}/{solutionId}/responses — raw payload, service info in headers</li>
 * </ul>
 *
 * <p><b>Removed:</b> {@code POST /{domainId}/{solutionId}/responses/error}. It corresponded to no
 * SIF concept — the specification conveys errors as an Error Object carried in the <em>response
 * payload</em>, with the code in the HTTP status field, and defines no separate error endpoint —
 * and it had no callers anywhere in the repository. Submit errors to {@code /responses} with a SIF
 * Error Object payload instead. The internal equivalent
 * ({@code POST /internal/responses/error}, {@link InternalResponseController}) is retained: the
 * requests-connector uses it to report proxy and paging failures to the consumer.</p>
 *
 * <p><b>Required Headers:</b></p>
 * <ul>
 *   <li>X-Environment-Id: UUID of the environment (injected by Gateway)</li>
 *   <li>X-Environment-Name: Name of the environment (injected by Gateway)</li>
 *   <li>queueId: UUID of the consumer's queue for receiving the response</li>
 *   <li>relativeServicePath: the SIF relative service path (e.g. "StudentPersonals" or
 *       "StudentPersonals;contextId=DEFAULT;zoneId=test-zone"). The service name is the
 *       first segment before any semicolons. Falls back to {@code serviceName} header
 *       for backward compatibility.</li>
 *   <li>serviceType: OBJECT, UTILITY, FUNCTIONAL, etc. (defaults to OBJECT)</li>
 *   <li>requestId: original request ID for correlation (optional)</li>
 * </ul>
 *
 * @see ResponseRoutingService
 * @see ResponseDTO
 * @see ResponseResultDTO
 */
@RestController
@RequestMapping("/{domainId}/{solutionId}/responses")
@Slf4j
public class ResponseController {

    private static final String CONNECTOR_NAME = "responses-connector";

    private final ResponseRoutingService routingService;
    private final HeaderSanitizer headerSanitizer;
    private final ConnectorMetricsService connectorMetricsService;
    private final ResponseDestinationValidator destinationValidator;

    public ResponseController(ResponseRoutingService routingService,
                              HeaderSanitizer headerSanitizer,
                              ConnectorMetricsService connectorMetricsService,
                              ResponseDestinationValidator destinationValidator) {
        this.routingService = routingService;
        this.headerSanitizer = headerSanitizer;
        this.connectorMetricsService = connectorMetricsService;
        this.destinationValidator = destinationValidator;
        log.info("ResponseController initialized");
    }

    /**
     * Primary response endpoint per SIF 3.7 spec.
     * <p>
     * Providers POST raw SIF payloads to {@code POST /{domainId}/{solutionId}/responses}
     * with service metadata in HTTP headers. The body is the raw SIF payload (may be null
     * for empty query results).
     * </p>
     * <p>
     * The service name is derived from the {@code relativeServicePath} header — the first
     * segment before any semicolons (matrix params). Falls back to {@code serviceName}
     * header for backward compatibility.
     * </p>
     */
    @PostMapping
    public ResponseEntity<ResponseResultDTO> handleResponse(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @RequestHeader("X-Environment-Id") UUID environmentId,
            @RequestHeader("X-Environment-Name") String environmentName,
            @RequestHeader("queueId") UUID queueId,
            @RequestHeader("requestId") String requestId,
            @RequestHeader(value = "relativeServicePath") String relativeServicePath,
            @RequestHeader(value = "serviceType", required = false, defaultValue = "OBJECT") String serviceType,
            @RequestHeader(value = "zoneId", required = false) String zoneId,
            @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
            @RequestBody(required = false) Object rawPayload,
            HttpServletRequest httpServletRequest) {

        String resolvedServiceName = extractServiceName(relativeServicePath);

        return doHandleResponse(domainId, solutionId, resolvedServiceName, environmentId,
                environmentName, queueId, requestId, serviceType, zoneId, contextId,
                relativeServicePath, rawPayload, httpServletRequest);
    }


    // ========================================================================
    // Shared implementation
    // ========================================================================

    /**
     * Extracts the service name from a {@code relativeServicePath} header per SIF 3.7.
     *
     * <p>The relativeServicePath can take several forms:</p>
     * <ul>
     *   <li>{@code StudentPersonals} — plain name</li>
     *   <li>{@code /StudentPersonals} — leading slash</li>
     *   <li>{@code StudentPersonals;contextId=DEFAULT;zoneId=zone1} — matrix params</li>
     *   <li>{@code /StudentPersonals;contextId=DEFAULT} — leading slash + matrix params</li>
     *   <li>{@code StudentPersonals?param=value} — query params</li>
     *   <li>{@code /StudentPersonals?param=value&other=x} — leading slash + query params</li>
     *   <li>{@code StudentPersonals/uuid123} — with object ID segment</li>
     * </ul>
     *
     * <p>The service name is always the first path segment, stripped of any leading slash,
     * and truncated before any {@code ;} (matrix params), {@code ?} (query params),
     * or {@code /} (sub-path segments).</p>
     *
     * @param relativeServicePath the SIF relative service path header
     * @return the extracted service name
     */
    public static String extractServiceName(String relativeServicePath) {
        if (relativeServicePath == null || relativeServicePath.isBlank()) {
            return null;
        }

        String path = relativeServicePath.trim();

        // Strip leading slash
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Truncate at first ';' (matrix params), '?' (query params), or '/' (sub-path)
        int end = path.length();
        int semicolon = path.indexOf(';');
        int question = path.indexOf('?');
        int slash = path.indexOf('/');

        if (semicolon >= 0 && semicolon < end) end = semicolon;
        if (question >= 0 && question < end) end = question;
        if (slash >= 0 && slash < end) end = slash;

        String serviceName = path.substring(0, end);
        return serviceName.isBlank() ? null : serviceName;
    }

    private ResponseEntity<ResponseResultDTO> doHandleResponse(
            String domainId, String solutionId, String serviceName,
            UUID environmentId, String environmentName,
            UUID queueId, String requestId,
            String serviceType, String zoneId, String contextId,
            String relativeServicePath, Object rawPayload,
            HttpServletRequest httpServletRequest) {

        long startTime = System.currentTimeMillis();

        // Sanitize incoming HTTP headers (strip blacklisted and structured SIF headers)
        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);

        // Build ResponseDTO from headers and raw payload
        ResponseDTO response = ResponseDTO.builder()
                .serviceName(serviceName)
                .serviceType(serviceType)
                .payload(rawPayload)
                .headers(passthroughHeaders)
                .build();

        // Record SIF-level connector request metric on receipt
        connectorMetricsService.recordConnectorRequest(
                CONNECTOR_NAME, serviceName, null,
                httpServletRequest.getMethod(), 0);

        try {
            log.info("Received response: serviceName={}, queueId={}, requestId={}, environmentId={}, passthroughHeaderCount={}",
                    serviceName, queueId, requestId, environmentId, passthroughHeaders.size());

            ResponseResultDTO result = routingService.routeResponse(
                    domainId, solutionId,
                    environmentId, environmentName, queueId, requestId, response,
                    passthroughHeaders, zoneId, contextId, relativeServicePath);

            long durationMs = System.currentTimeMillis() - startTime;
            connectorMetricsService.recordConnectorRequestDuration(
                    CONNECTOR_NAME, serviceName, 202, durationMs);

            log.info("Response handled successfully: messageId={}, queueId={}, status={}",
                    result.getMessageId(), result.getQueueId(), result.getStatus());

            return ResponseEntity.accepted().body(result);
        } catch (Exception ex) {
            String errorType = ex.getClass().getSimpleName();
            connectorMetricsService.recordConnectorError(
                    CONNECTOR_NAME, serviceName, null, errorType);

            long durationMs = System.currentTimeMillis() - startTime;
            connectorMetricsService.recordConnectorRequestDuration(
                    CONNECTOR_NAME, serviceName, 500, durationMs);

            throw ex;
        }
    }

}
