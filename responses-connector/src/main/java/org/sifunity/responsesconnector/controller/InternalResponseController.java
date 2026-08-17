// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.sifunity.responsesconnector.dto.ResponseResultDTO;
import org.sifunity.responsesconnector.service.ResponseRoutingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for handling internal responses from the Requests Connector.
 * <p>
 * This controller provides internal REST API endpoints for receiving responses from the
 * Requests Connector service (DELAYED_to_IMMEDIATE pattern). Unlike the public ResponseController,
 * this controller does NOT require environment context headers and does NOT require authentication,
 * as it is intended for internal use only within the trusted network.
 * </p>
 * 
 * <p><b>Endpoints:</b></p>
 * <ul>
 *   <li>POST /internal/responses - Accept internal responses from Requests Connector</li>
 *   <li>POST /internal/responses/error - Accept internal error responses from Requests Connector</li>
 * </ul>
 * 
 * <p><b>Required Headers:</b></p>
 * <ul>
 *   <li>queueId: UUID of the consumer's queue for receiving the response</li>
 *   <li>requestId: UUID of the original request for correlation</li>
 * </ul>
 * 
 * <p><b>Authentication:</b></p>
 * <p>
 * This controller is for INTERNAL USE ONLY by the Requests Connector. No authentication
 * is required as it should only be accessible within the trusted internal network.
 * Network policies should be configured to prevent external access to these endpoints.
 * </p>
 * 
 * <p><b>Environment Context:</b></p>
 * <p>
 * Unlike the public ResponseController, this controller does NOT require or use environment
 * context headers (X-Environment-Id, X-Environment-Name). Internal responses are processed
 * without environment context.
 * </p>
 * 
 * <p><b>Validation:</b></p>
 * <ul>
 *   <li>Required headers must be present (queueId, requestId)</li>
 *   <li>Request body must be valid JSON conforming to DTO schema</li>
 *   <li>Bean Validation annotations on DTOs enforce field-level validation</li>
 *   <li>Missing or invalid data results in HTTP 400 Bad Request</li>
 * </ul>
 * 
 * <p><b>Error Handling:</b></p>
 * <ul>
 *   <li>400 Bad Request: Missing headers or invalid request body</li>
 *   <li>404 Not Found: Queue does not exist</li>
 *   <li>503 Service Unavailable: Queue Service unavailable</li>
 *   <li>504 Gateway Timeout: Queue Service timeout</li>
 *   <li>500 Internal Server Error: Unexpected errors</li>
 * </ul>
 * 
 * <p><b>Requirements:</b></p>
 * <ul>
 *   <li>2.1: Accept response from Requests Connector via internal API endpoint</li>
 *   <li>2.2: Validate queueId is provided</li>
 *   <li>2.3: Validate requestId is provided for correlation</li>
 *   <li>2.4: Accept both success and error responses</li>
 *   <li>2.5: Process response identically to provider responses</li>
 *   <li>2.6: Internal API accessible without authentication (trusted internal network)</li>
 *   <li>14.2: Provide internal API endpoint for receiving responses from Requests Connector</li>
 * </ul>
 * 
 * @see ResponseRoutingService
 * @see ResponseDTO
 * @see ResponseResultDTO
 */
@RestController
@RequestMapping("/internal/responses")
@Slf4j
public class InternalResponseController {
    
    private final ResponseRoutingService routingService;
    private final HeaderSanitizer headerSanitizer;
    
    /**
     * Constructs an InternalResponseController with the required dependencies.
     * 
     * @param routingService Service for routing responses to consumer queues
     * @param headerSanitizer Sanitizer for extracting passthrough headers from the request
     */
    public InternalResponseController(ResponseRoutingService routingService,
                                      HeaderSanitizer headerSanitizer) {
        this.routingService = routingService;
        this.headerSanitizer = headerSanitizer;
        log.info("InternalResponseController initialized");
    }
    
    /**
     * Handles internal responses from the Requests Connector and routes them to consumer queues.
     * <p>
     * This endpoint accepts responses from the Requests Connector service for the DELAYED_to_IMMEDIATE
     * pattern. When the Requests Connector makes a synchronous call to an IMMEDIATE-only provider,
     * it receives the response and forwards it to this endpoint to be enqueued to the consumer's queue.
     * </p>
     * 
     * <p><b>Request Flow:</b></p>
     * <ol>
     *   <li>Requests Connector receives response from IMMEDIATE-only provider</li>
     *   <li>Requests Connector calls this internal endpoint with queueId and requestId</li>
     *   <li>Controller validates headers and request body</li>
     *   <li>Controller delegates to ResponseRoutingService</li>
     *   <li>Service constructs SIF message envelope (without environment context)</li>
     *   <li>Service enqueues message to consumer's queue</li>
     *   <li>Controller returns success response with messageId</li>
     * </ol>
     * 
     * <p><b>Example Request:</b></p>
     * <pre>
     * POST /internal/responses
     * Content-Type: application/json
     * queueId: 123e4567-e89b-12d3-a456-426614174000
     * requestId: 789e0123-e89b-12d3-a456-426614174000
     * 
     * {
     *   "serviceName": "StudentPersonal",
     *   "serviceType": "OBJECT",
     *   "payload": {
     *     "refId": "abc123",
     *     "name": {
     *       "givenName": "John",
     *       "familyName": "Doe"
     *     }
     *   }
     * }
     * </pre>
     * 
     * <p><b>Example Response:</b></p>
     * <pre>
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     * 
     * {
     *   "messageId": "456e7890-e89b-12d3-a456-426614174000",
     *   "status": "DELIVERED",
     *   "timestamp": "2024-01-15T10:30:00Z"
     * }
     * </pre>
     * 
     * <p><b>Requirements:</b></p>
     * <ul>
     *   <li>2.1: Accept response from Requests Connector via internal API endpoint</li>
     *   <li>2.2: Validate queueId is provided</li>
     *   <li>2.3: Validate requestId is provided for correlation</li>
     *   <li>2.4: Accept both success and error responses</li>
     *   <li>2.5: Process response identically to provider responses</li>
     * </ul>
     * 
     * @param queueId The UUID of the consumer's queue for receiving the response
     * @param requestId The UUID of the original request for correlation
     * @param response The response DTO containing service details and payload
     * @return ResponseEntity with ResponseResultDTO containing messageId, status, and timestamp
     * @throws org.springframework.web.bind.MissingRequestHeaderException if any required header is missing (400)
     * @throws org.springframework.web.bind.MethodArgumentNotValidException if request body validation fails (400)
     * @throws org.sifunity.responsesconnector.exception.QueueNotFoundException if the queue does not exist (404)
     * @throws org.sifunity.responsesconnector.exception.QueueServiceUnavailableException if Queue Service is unavailable (503)
     */
    @PostMapping
    public ResponseEntity<ResponseResultDTO> handleInternalResponse(
            @RequestHeader("queueId") UUID queueId,
            @RequestHeader("requestId") String requestId,
            @RequestHeader(value = "domainId", required = false, defaultValue = "_") String domainId,
            @RequestHeader(value = "solutionId", required = false, defaultValue = "_") String solutionId,
            @RequestHeader(value = "relativeServicePath", required = false) String relativeServicePath,
            @Valid @RequestBody ResponseDTO response,
            HttpServletRequest httpServletRequest) {
        
        log.info("Received internal response: queueId={}, requestId={}, serviceName={}, relativeServicePath={}", 
            queueId, requestId, response.getServiceName(), relativeServicePath);
        
        // Sanitize HTTP headers the same way as the public ResponseController —
        // this ensures filter-injected headers (e.g. x-sif-sequenceId) flow through
        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);
        
        ResponseResultDTO result = routingService.routeInternalResponse(
            domainId, solutionId,
            queueId, requestId, relativeServicePath, response, passthroughHeaders);
        
        log.info("Internal response handled successfully: messageId={}, status={}", 
            result.getMessageId(), result.getStatus());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Handles internal error responses from the Requests Connector and routes them to consumer queues.
     * <p>
     * This endpoint accepts error responses from the Requests Connector service. When the Requests
     * Connector encounters an error (e.g., provider timeout, provider unavailable, provider error),
     * it forwards the error to this endpoint to be enqueued to the consumer's queue as an error message.
     * </p>
     * 
     * <p><b>Request Flow:</b></p>
     * <ol>
     *   <li>Requests Connector encounters error (timeout, unavailable, provider error)</li>
     *   <li>Requests Connector calls this internal endpoint with queueId and requestId</li>
     *   <li>Controller validates headers and request body</li>
     *   <li>Controller delegates to ResponseRoutingService</li>
     *   <li>Service constructs SIF error message envelope (without environment context)</li>
     *   <li>Service enqueues error message to consumer's queue</li>
     *   <li>Controller returns success response with messageId</li>
     * </ol>
     * 
     * <p><b>Example Request:</b> {@code code} is the HTTP status integer, {@code subCode} the
     * internal identifier — see {@link org.sifunity.responsesconnector.dto.InternalErrorDetail}.
     * Only {@code message} is required; {@code code}, {@code subCode} and {@code scope} fall back to
     * 500 / {@code INTERNAL_ERROR} / {@code BROKER}.</p>
     * <pre>
     * POST /internal/responses/error
     * Content-Type: application/json
     * queueId: 123e4567-e89b-12d3-a456-426614174000
     * requestId: 789e0123-e89b-12d3-a456-426614174000
     *
     * {
     *   "error": {
     *     "code": 504,
     *     "subCode": "PROVIDER_TIMEOUT",
     *     "message": "Provider did not respond within timeout period",
     *     "description": "The provider failed to respond within the configured timeout of 30 seconds",
     *     "scope": "PROVIDER"
     *   }
     * }
     * </pre>
     *
     * <p>A plain-text body is <b>no longer accepted</b>. It previously bound the entire body to a
     * single {@code String}, which silently swallowed JSON objects: the whole blob became the
     * message text and the sender's classification was replaced by a hardcoded
     * {@code INTERNAL_ERROR} / {@code BROKER} / 500.</p>
     *
     * <p><b>Example Response:</b></p>
     * <pre>
     * HTTP/1.1 200 OK
     * Content-Type: application/json
     * 
     * {
     *   "messageId": "456e7890-e89b-12d3-a456-426614174000",
     *   "status": "ERROR_DELIVERED",
     *   "timestamp": "2024-01-15T10:30:00Z"
     * }
     * </pre>
     * 
     * <p><b>Requirements:</b></p>
     * <ul>
     *   <li>2.1: Accept error response from Requests Connector via internal API endpoint</li>
     *   <li>2.2: Validate queueId is provided</li>
     *   <li>2.3: Validate requestId is provided for correlation</li>
     *   <li>2.4: Accept both success and error responses</li>
     *   <li>2.5: Process error response identically to provider error responses</li>
     * </ul>
     * 
     * @param queueId The UUID of the consumer's queue for receiving the error response
     * @param requestId The UUID of the original request for correlation
     * @param errorMessage The error message as a plain string
     * @return ResponseEntity with ResponseResultDTO containing messageId, status, and timestamp
     * @throws org.springframework.web.bind.MissingRequestHeaderException if any required header is missing (400)
     * @throws org.sifunity.responsesconnector.exception.QueueNotFoundException if the queue does not exist (404)
     * @throws org.sifunity.responsesconnector.exception.QueueServiceUnavailableException if Queue Service is unavailable (503)
     */
    @PostMapping("/error")
    public ResponseEntity<ResponseResultDTO> handleInternalErrorResponse(
            @RequestHeader("queueId") UUID queueId,
            @RequestHeader("requestId") String requestId,
            @RequestHeader(value = "domainId", required = false, defaultValue = "_") String domainId,
            @RequestHeader(value = "solutionId", required = false, defaultValue = "_") String solutionId,
            @RequestBody @Valid org.sifunity.responsesconnector.dto.InternalErrorRequest errorRequest,
            HttpServletRequest httpServletRequest) {

        org.sifunity.responsesconnector.dto.InternalErrorDetail error = errorRequest.error();

        log.warn("Received internal error response: queueId={}, requestId={}, subCode={}, scope={}, message={}",
            queueId, requestId, error.subCodeOrDefault(), error.scopeOrDefault(), error.message());

        // Sanitize HTTP headers — ensures filter-injected headers flow through
        Map<String, String> passthroughHeaders = headerSanitizer.sanitize(httpServletRequest);

        // Constructs a SIF error envelope preserving the sender's code, subCode and scope
        ResponseResultDTO result = routingService.routeInternalErrorResponse(
            domainId, solutionId,
            queueId, requestId, error, passthroughHeaders);
        
        log.info("Internal error response handled successfully: messageId={}, status={}", 
            result.getMessageId(), result.getStatus());
        
        return ResponseEntity.ok(result);
    }
}
