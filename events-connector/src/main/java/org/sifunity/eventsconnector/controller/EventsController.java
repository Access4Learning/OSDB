// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.eventsconnector.dto.DeliveryStatus;
import org.sifunity.eventsconnector.dto.EventDistributionResponse;
import org.sifunity.eventsconnector.dto.EventPublicationRequest;
import org.sifunity.eventsconnector.exception.EventValidationException;
import org.sifunity.eventsconnector.service.EventDistributionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for receiving event publications from providers.
 * 
 * <p>This controller provides the external API endpoint for providers to publish
 * events to the broker. It handles the POST /events endpoint, extracts environment
 * context and event headers, validates the request, and delegates to the
 * EventDistributionService for distribution to all matching subscriber queues.</p>
 * 
 * <p><strong>API Endpoint:</strong></p>
 * <ul>
 *   <li>POST /events - Publish an event for distribution to subscribers</li>
 * </ul>
 * 
 * <p><strong>Request Headers:</strong></p>
 * <ul>
 *   <li>X-Environment-Id: Environment ID (injected by Gateway) - required</li>
 *   <li>X-Environment-Name: Environment name (injected by Gateway) - required</li>
 *   <li>serviceName: Service/object type name (e.g., StudentPersonal) - required</li>
 *   <li>zoneId: Zone identifier - required</li>
 *   <li>contextId: Context identifier - optional, defaults to DEFAULT</li>
 *   <li>messageId: Unique message identifier - optional, auto-generated if not provided</li>
 *   <li>timestamp: ISO-8601 timestamp - optional, set to current time if not provided</li>
 * </ul>
 * 
 * <p><strong>Response Status Codes:</strong></p>
 * <ul>
 *   <li>200 OK: All queues succeeded OR no subscribers found</li>
 *   <li>207 Multi-Status: Some queues succeeded, some failed (partial delivery)</li>
 *   <li>400 Bad Request: Validation error (missing required headers or invalid data)</li>
 *   <li>500 Internal Server Error: All queues failed</li>
 *   <li>503 Service Unavailable: Subscription Service or Queue Service unavailable</li>
 * </ul>
 * 
 * <p><strong>Authentication and Authorization:</strong></p>
 * <p>This controller assumes all requests have been authenticated and authorized by
 * the API Gateway. No authentication or authorization checks are performed here.
 * The Gateway injects environment context headers (X-Environment-Id, X-Environment-Name)
 * which are extracted and validated by this controller.</p>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>1: Event Reception from Providers - Accept events via REST API</li>
 *   <li>6: Event Validation - Validate required headers</li>
 *   <li>7: Environment Context Handling - Extract and validate environment headers</li>
 *   <li>15: API Design - RESTful API with appropriate status codes</li>
 * </ul>
 * 
 * @see EventDistributionService
 * @see EventPublicationRequest
 * @see EventDistributionResponse
 */
@Tag(name = "SIF API")
@RestController
@RequestMapping("/{domainId}/{solutionId}/events")
@RequiredArgsConstructor
@Slf4j
public class EventsController {
    
    private static final String CONNECTOR_NAME = "events-connector";
    
    private final EventDistributionService distributionService;
    private final HeaderSanitizer headerSanitizer;
    private final ConnectorMetricsService connectorMetricsService;
    
    /**
     * Publishes an event for distribution to all matching subscriber queues.
     * 
     * <p>This endpoint receives event publications from providers and distributes
     * them to all subscriber queues that have matching subscriptions (based on
     * serviceName, zoneId, and contextId).</p>
     * 
     * <p><strong>Request Processing Flow:</strong></p>
     * <ol>
     *   <li>Extract and validate environment context headers (X-Environment-Id, X-Environment-Name)</li>
     *   <li>Extract and validate event headers (serviceName, zoneId, contextId, messageId, timestamp)</li>
     *   <li>Validate required headers (serviceName, zoneId)</li>
     *   <li>Create EventPublicationRequest with headers and payload</li>
     *   <li>Delegate to EventDistributionService for distribution</li>
     *   <li>Return appropriate HTTP status based on delivery status</li>
     * </ol>
     * 
     * <p><strong>Header Validation:</strong></p>
     * <ul>
     *   <li>X-Environment-Id: Must be present and valid UUID</li>
     *   <li>X-Environment-Name: Must be present and not blank</li>
     *   <li>serviceName: Must be present and not blank</li>
     *   <li>zoneId: Must be present and not blank</li>
     *   <li>contextId: Optional, defaults to DEFAULT if not provided</li>
     *   <li>messageId: Optional, auto-generated if not provided</li>
     *   <li>timestamp: Optional, set to current time if not provided</li>
     * </ul>
     * 
     * <p><strong>Response Status Mapping:</strong></p>
     * <ul>
     *   <li>SUCCESS: 200 OK - All queues received the event successfully</li>
     *   <li>NO_SUBSCRIBERS: 200 OK - No subscriptions found (not an error)</li>
     *   <li>PARTIAL: 207 Multi-Status - Some queues succeeded, some failed</li>
     *   <li>FAILED: 500 Internal Server Error - All queues failed</li>
     * </ul>
     * 
     * <p><strong>Error Handling:</strong></p>
     * <ul>
     *   <li>Missing required headers: 400 Bad Request via EventValidationException</li>
     *   <li>Invalid header format: 400 Bad Request via EventValidationException</li>
     *   <li>Service unavailable: 503 Service Unavailable via GlobalExceptionHandler</li>
     *   <li>Unexpected errors: 500 Internal Server Error via GlobalExceptionHandler</li>
     * </ul>
     * 
     * @param environmentId Environment ID from X-Environment-Id header (injected by Gateway)
     * @param environmentName Environment name from X-Environment-Name header (injected by Gateway)
     * @param serviceName Service/object type name from serviceName header
     * @param zoneId Zone identifier from zoneId header
     * @param contextId Context identifier from contextId header (optional, defaults to DEFAULT)
     * @param messageId Message identifier from messageId header (optional, auto-generated if not provided)
     * @param timestampStr Timestamp string from timestamp header (optional, set to current time if not provided)
     * @param payload Event payload as JSON
     * @return ResponseEntity with EventDistributionResponse and appropriate HTTP status
     * @throws EventValidationException if required headers are missing or invalid
     */
    @PostMapping
    public ResponseEntity<Void> publishEvent(
        @PathVariable String domainId,
        @PathVariable String solutionId,
        @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
        @RequestHeader(value = "X-Environment-Name", required = false) String environmentName,
        @RequestHeader(value = "serviceName", required = false) String serviceName,
        @RequestHeader(value = "zoneId", required = false) String zoneId,
        @RequestHeader(value = "X-Default-Zone-Id", required = false) String defaultZoneId,
        @RequestHeader(value = "contextId", required = false, defaultValue = "DEFAULT") String contextId,
        @RequestHeader(value = "messageId", required = false) String messageId,
        @RequestHeader(value = "timestamp", required = false) String timestampStr,
        @RequestBody JsonNode payload,
        HttpServletRequest httpServletRequest
    ) {
        long startTime = System.currentTimeMillis();
        
        // Zone ID fallback: use X-Default-Zone-Id if zoneId is absent
        if (zoneId == null || zoneId.isBlank()) {
            if (defaultZoneId != null && !defaultZoneId.isBlank()) {
                zoneId = defaultZoneId;
            } else {
                throw new EventValidationException("zoneId is required but no default is available");
            }
        }
        
        // Record SIF-level connector request metric on receipt
        connectorMetricsService.recordConnectorRequest(
            CONNECTOR_NAME, serviceName, zoneId,
            httpServletRequest.getMethod(), 0);
        
        try {
            // Validate environment context headers
            validateEnvironmentContext(environmentId, environmentName);
            
            // Validate required event headers
            validateEventHeaders(serviceName, zoneId);
            
            // Parse timestamp if provided
            Instant timestamp = parseTimestamp(timestampStr);
            
            // Sanitize incoming HTTP headers (strip blacklisted and structured SIF headers)
            Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);
            
            // Log event reception
            log.info("Received event publication: service={}, zone={}, context={}, environmentId={}, environmentName={}, passthroughHeaderCount={}",
                serviceName, zoneId, contextId, environmentId, environmentName, passthroughHeaders.size());
            
            // Create event publication request
            EventPublicationRequest request = new EventPublicationRequest(
                serviceName,
                zoneId,
                contextId,
                messageId,
                timestamp,
                payload,
                passthroughHeaders,
                UUID.fromString(environmentId)
            );
            
            // Distribute event to all matching subscriber queues
            EventDistributionResponse response = distributionService.distributeEvent(domainId, solutionId, request);
            
            // Return appropriate HTTP status based on delivery status
            HttpStatus status = determineHttpStatus(response.deliveryStatus());
            
            // Record SIF-level request duration on completion
            long durationMs = System.currentTimeMillis() - startTime;
            connectorMetricsService.recordConnectorRequestDuration(
                CONNECTOR_NAME, serviceName, status.value(), durationMs);
            
            log.info("Event publication complete: messageId={}, status={}, httpStatus={}, subscriberCount={}",
                response.messageId(), response.deliveryStatus(), status, response.subscriberCount());
            
            return ResponseEntity.status(status).build();
            
        } catch (Exception ex) {
            // Record SIF-level error metric
            String errorType = ex.getClass().getSimpleName();
            connectorMetricsService.recordConnectorError(
                CONNECTOR_NAME, serviceName, zoneId, errorType);
            
            // Record duration even on error
            long durationMs = System.currentTimeMillis() - startTime;
            connectorMetricsService.recordConnectorRequestDuration(
                CONNECTOR_NAME, serviceName, 500, durationMs);
            
            throw ex;
        }
    }
    
    /**
     * Validates environment context headers injected by the Gateway.
     * 
     * <p>The Gateway is responsible for authenticating requests and injecting
     * environment context headers. This method validates that these headers
     * are present and valid.</p>
     * 
     * <p><strong>Validation Rules:</strong></p>
     * <ul>
     *   <li>X-Environment-Id must be present and not blank</li>
     *   <li>X-Environment-Id must be a valid UUID</li>
     *   <li>X-Environment-Name must be present and not blank</li>
     * </ul>
     * 
     * @param environmentId Environment ID from X-Environment-Id header
     * @param environmentName Environment name from X-Environment-Name header
     * @throws EventValidationException if environment context is missing or invalid
     */
    private void validateEnvironmentContext(String environmentId, String environmentName) {
        if (environmentId == null || environmentId.isBlank()) {
            log.error("Missing environment context: X-Environment-Id header is required");
            throw new EventValidationException("Missing environment context: X-Environment-Id header is required");
        }
        
        if (environmentName == null || environmentName.isBlank()) {
            log.error("Missing environment context: X-Environment-Name header is required");
            throw new EventValidationException("Missing environment context: X-Environment-Name header is required");
        }
        
        // Validate that environmentId is a valid UUID
        try {
            UUID.fromString(environmentId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid environment context: X-Environment-Id must be a valid UUID, got: {}", environmentId);
            throw new EventValidationException("Invalid environment context: X-Environment-Id must be a valid UUID");
        }
    }
    
    /**
     * Validates required event headers.
     * 
     * <p><strong>Validation Rules:</strong></p>
     * <ul>
     *   <li>serviceName must be present and not blank</li>
     *   <li>zoneId must be present and not blank</li>
     * </ul>
     * 
     * <p>Note: contextId is optional and defaults to DEFAULT if not provided.</p>
     * 
     * @param serviceName Service/object type name
     * @param zoneId Zone identifier
     * @throws EventValidationException if required headers are missing
     */
    private void validateEventHeaders(String serviceName, String zoneId) {
        if (serviceName == null || serviceName.isBlank()) {
            log.error("Missing required header: serviceName is required");
            throw new EventValidationException("Missing required header: serviceName is required");
        }
        
        if (zoneId == null || zoneId.isBlank()) {
            log.error("Missing required header: zoneId is required");
            throw new EventValidationException("Missing required header: zoneId is required");
        }
    }
    
    /**
     * Parses timestamp string to Instant.
     * 
     * <p>If the timestamp string is null or blank, returns null (which will be
     * handled by EventPublicationRequest to set current time).</p>
     * 
     * <p>If the timestamp string is invalid, throws EventValidationException.</p>
     * 
     * @param timestampStr Timestamp string in ISO-8601 format
     * @return Instant parsed from string, or null if string is null/blank
     * @throws EventValidationException if timestamp format is invalid
     */
    private Instant parseTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.isBlank()) {
            return null;
        }
        
        try {
            return Instant.parse(timestampStr);
        } catch (Exception e) {
            log.error("Invalid timestamp format: {}, expected ISO-8601 format", timestampStr);
            throw new EventValidationException("Invalid timestamp format: expected ISO-8601 format (e.g., 2024-01-15T10:30:00Z)");
        }
    }
    
    /**
     * Determines the appropriate HTTP status code based on delivery status.
     * 
     * <p>Per SIF §8.2, the eventsConnector returns HTTP 202 (Accepted) to the
     * publisher. There are no further interactions with the Event Publisher
     * from this point on.</p>
     * 
     * <p><strong>Status Mapping:</strong></p>
     * <ul>
     *   <li>SUCCESS: 202 Accepted - Event accepted for distribution</li>
     *   <li>NO_SUBSCRIBERS: 202 Accepted - Accepted (no subscribers is not an error)</li>
     *   <li>PARTIAL: 202 Accepted - Accepted (partial delivery is internal concern)</li>
     *   <li>FAILED: 500 Internal Server Error - All queues failed</li>
     * </ul>
     * 
     * @param deliveryStatus Delivery status from event distribution
     * @return Appropriate HTTP status code
     */
    private HttpStatus determineHttpStatus(DeliveryStatus deliveryStatus) {
        return switch (deliveryStatus) {
            case SUCCESS, NO_SUBSCRIBERS, PARTIAL -> HttpStatus.ACCEPTED;
            case FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
