// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.queueservice.config.WebMvcConfig;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.queueservice.dto.*;
import org.sifunity.broker.queueservice.service.DeadLetterService;
import org.sifunity.broker.queueservice.service.DeliveryService;
import org.sifunity.broker.queueservice.service.MessageService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for message operations.
 *
 * <p>Provides endpoints for enqueuing messages, delivering messages via the
 * SIF "ACK and GET NEXT" pattern, and managing dead-letter queue messages.</p>
 */
@Tag(name = "SIF API", description = "SIF 3.7 specification endpoints")
@RestController
@RequestMapping("/{domainId}/{solutionId}")
public class MessageController {

    private static final Logger log = LoggerFactory.getLogger(MessageController.class);
    private static final String DEFAULT_CONSUMER = "X_DEFAULT_Q_CONSUMER";

    private final MessageService messageService;
    private final DeliveryService deliveryService;
    private final DeadLetterService deadLetterService;
    private final QueueService queueService;
    private final String internalServiceToken;

    public MessageController(MessageService messageService,
                             DeliveryService deliveryService,
                             DeadLetterService deadLetterService,
                             QueueService queueService,
                             @Value("${broker.internal-service-token:}") String internalServiceToken) {
        this.messageService = messageService;
        this.deliveryService = deliveryService;
        this.deadLetterService = deadLetterService;
        this.queueService = queueService;
        this.internalServiceToken = internalServiceToken;
    }

    /**
     * Enqueue a message to a queue.
     *
     * <p>Passes domainId, solutionId, and messageType to MessageService for
     * hierarchical storage key generation.</p>
     */
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Message enqueued"),
        @ApiResponse(responseCode = "400", description = "Malformed queue or message id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Queue or message does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "503", description = "Message storage is unavailable",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PostMapping("/queues/{queueId}/messages")
    public ResponseEntity<?> enqueueMessage(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID queueId,
            @Valid @RequestBody EnqueueMessageRequest request,
            @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken) {

        // Verify queue ownership — fail-closed: deny if environmentId is absent or mismatched.
        // broker-services fingerprint bypass is only honored with a valid Internal_Service_Token.
        QueueDTO queue = queueService.getQueueForRouting(queueId);
        boolean isBrokerServices = "broker-services".equals(fingerprint) && isValidInternalToken(serviceToken);
        if (!isBrokerServices
                && (environmentId == null || !queue.ownerId().toString().equals(environmentId))) {
            log.warn("Ownership violation: environment {} attempted to access queue {}", environmentId, queueId);
            return forbiddenResponse(environmentId);
        }

        log.info("Enqueuing message to queue: queueId={}, zone={}, context={}, service={}",
                queueId, request.zone(), request.context(), request.service());

        // For storage: use sourceId (provider) for shared payload storage across consumer queues.
        // For stream: always use queue owner (consumer) so each consumer's stream is independent.
        UUID sourceEnvUuid;
        if (request.environment() != null && !request.environment().isBlank()) {
            try {
                sourceEnvUuid = UUID.fromString(request.environment());
            } catch (IllegalArgumentException e) {
                sourceEnvUuid = queue.ownerId();
            }
        } else {
            sourceEnvUuid = queue.ownerId();
        }

        // Derive messageType from request or default to "event"
        String messageType = request.messageType() != null ? request.messageType() : "event";

        // Merge SIF structured headers into passthrough headers map for storage
        Map<String, String> allHeaders = new java.util.HashMap<>();
        if (request.headers() != null) {
            allHeaders.putAll(request.headers());
        }
        // Add structured SIF fields as headers (these get returned on delivery)
        if (request.requestId() != null) allHeaders.put("requestId", request.requestId());
        if (request.queueId() != null) allHeaders.put("queueId", request.queueId());
        if (request.serviceType() != null) allHeaders.put("serviceType", request.serviceType());
        allHeaders.put("messageType", messageType);
        if (request.service() != null) allHeaders.put("serviceName", request.service());
        if (request.zone() != null) allHeaders.put("zoneId", request.zone());
        if (request.context() != null) allHeaders.put("contextId", request.context());

        MessageMetadata metadata = messageService.enqueueMessage(
                queueId,
                request.messageId(),
                domainId,
                solutionId,
                queue.ownerId(),
                sourceEnvUuid,
                request.zone(),
                request.context(),
                messageType,
                request.service(),
                request.payload(),
                allHeaders
        );

        EnqueueMessageResponse response = new EnqueueMessageResponse(
                metadata.getMessageId(),
                metadata.getQueueId(),
                metadata.getStorageKey(),
                metadata.getEnqueuedAt(),
                metadata.getStatus()
        );

        log.info("Message enqueued: messageId={}, queueId={}", metadata.getMessageId(), queueId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get next message from queue using SIF 3.x "Get Next and Pop" pattern (§9.3.1).
     *
     * <p>Reads the {@code connectionId} header to identify the consumer (defaults to
     * {@code X_DEFAULT_Q_CONSUMER}).</p>
     *
     * <p>The {@code deleteMessageId} identifies the previously delivered message to
     * acknowledge. Per SIF 3.7+, it is passed as an HTTP header. The legacy matrix
     * parameter syntax ({@code ;deleteMessageId=...}) is also supported but deprecated.</p>
     *
     * <p>Per SIF spec, the consumer saves the {@code messageid} response header and passes
     * it back as {@code deleteMessageId} on the next call to ACK-and-get-next.</p>
     *
     * @param queueId       the queue ID
     * @param headerDeleteId the deleteMessageId from HTTP header (SIF 3.7+, preferred)
     * @param connectionId  the consumer connection identifier
     * @return 200 with message, 204 if empty, 404 on PEL validation failure, or 409 on conflict
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The next message, with its SIF response headers"),
        @ApiResponse(responseCode = "204", description = "The queue is empty"),
        @ApiResponse(responseCode = "409", description = "Another consumer currently holds the next message",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "410", description = "The message has already been consumed or deleted (D7: 410, not 404)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "400", description = "Malformed queue or message id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Queue or message does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "503", description = "Message storage is unavailable",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping("/queues/{queueId}/messages")
    public ResponseEntity<?> getNextMessage(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID queueId,
            @RequestHeader(value = "deleteMessageId", required = false) String headerDeleteId,
            @RequestHeader(value = "connectionId", required = false, defaultValue = DEFAULT_CONSUMER) String connectionId,
            @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken,
            HttpServletRequest request,
            @RequestHeader(value = "requestAction", required = false) String requestAction) {

        // SIF 3.7+: prefer HTTP header for deleteMessageId
        // Legacy (deprecated): extract from matrix parameter ;deleteMessageId=... in the URI
        String matrixDeleteId = extractMatrixParam(request, "deleteMessageId");
        String deleteMessageId = "";

        if (headerDeleteId != null && !headerDeleteId.isBlank() && matrixDeleteId != null && !matrixDeleteId.isBlank()) {
            SifErrorResponse error = new SifErrorResponse(
                    UUID.randomUUID().toString(), HttpStatus.CONFLICT.value(),
                    "MSG_409", "Conflict",
                    "Both deleteMessageId header and matrix parameter provided. Use one or the other.",
                    "BROKER", Instant.now(), null, null);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(SifErrorResponseEnvelope.of(error));
        }
        if (headerDeleteId != null && !headerDeleteId.isBlank()) {
            deleteMessageId = headerDeleteId;
        } else if (matrixDeleteId != null && !matrixDeleteId.isBlank()) {
            deleteMessageId = matrixDeleteId;
        }

        // Verify queue ownership — fail-closed: deny if environmentId is absent or mismatched.
        // broker-services fingerprint bypass is only honored with a valid Internal_Service_Token.
        QueueDTO queue = queueService.getQueueForRouting(queueId);
        boolean isBrokerServices = "broker-services".equals(fingerprint) && isValidInternalToken(serviceToken);
        if (!isBrokerServices
                && (environmentId == null || !queue.ownerId().toString().equals(environmentId))) {
            log.warn("Ownership violation: environment {} attempted to access queue {}", environmentId, queueId);
            return forbiddenResponse(environmentId);
        }

        UUID envUuid = resolveEnvironmentId(environmentId, queue.ownerId());
        String consumerName = (connectionId != null && !connectionId.isEmpty()) ? connectionId : DEFAULT_CONSUMER;

        log.debug("getNextMessage: queueId={}, deleteMessageId={}, connectionId={}",
                queueId, deleteMessageId.isEmpty() ? "(empty)" : deleteMessageId, consumerName);

        // Resolve deleteMessageId (messageId UUID) → Redis stream entry ID for ACK
        String resolvedStreamId = "";
        if (deleteMessageId != null && !deleteMessageId.isEmpty()) {
            resolvedStreamId = deliveryService.resolveStreamIdFromMessageId(
                    domainId, solutionId, envUuid, queueId, consumerName, deleteMessageId);

            if (resolvedStreamId == null) {
                log.warn("PEL validation failed: messageId {} not in PEL for consumer {}", deleteMessageId, consumerName);
                // D7: 410 GONE, not 404. queueMessageGet declares no 404, and the semantics are
                // "this message is no longer pending" rather than "this resource never existed".
                // QueueNotFoundException -> 404 is declared and is deliberately left untouched.
                SifErrorResponse error = new SifErrorResponse(
                        UUID.randomUUID().toString(), HttpStatus.GONE.value(),
                        "MSG_410", "Message no longer in pending list",
                        "Message " + deleteMessageId + " not found in pending list for consumer " + consumerName,
                        "BROKER", Instant.now(), null, null);
                return withSifResponseHeaders(
                        ResponseEntity.status(HttpStatus.GONE), HttpStatus.GONE, requestAction, deleteMessageId, null)
                        .body(SifErrorResponseEnvelope.of(error));
            }
        }

        // Deliver next message via DeliveryService (pass the resolved stream ID for ACK)
        Optional<MessageMetadata> result = deliveryService.deliverNextMessage(
                queueId, consumerName, resolvedStreamId, domainId, solutionId, envUuid);

        if (result.isEmpty()) {
            log.debug("Queue is empty: queueId={}", queueId);
            // A 204 is still a SIF response and still declares the required headers.
            return withSifResponseHeaders(
                    ResponseEntity.status(HttpStatus.NO_CONTENT), HttpStatus.NO_CONTENT, requestAction, null, null)
                    .build();
        }

        MessageMetadata msg = result.get();
        log.info("Delivered message: queueId={}, messageId={}, consumer={}", queueId, msg.getMessageId(), consumerName);

        // Per SIF §9.3.1: return messageid header — consumer uses this as deleteMessageId on next call
        // Headers that must NOT be forwarded as response headers (request-only or controlled by Tomcat).
        // Note "timestamp" and "authorization" are already stripped upstream by HeaderSanitizer at
        // connector ingress, so their presence here is defence-in-depth rather than the only guard.
        // "messagetype" is listed because withSifResponseHeaders(...) sets it below — forwarding the
        // stored copy as well would emit the header twice.
        java.util.Set<String> RESPONSE_HEADER_BLACKLIST = java.util.Set.of(
                "content-length", "content-type", "transfer-encoding", "connection",
                "host", "cookie", "accept", "accept-encoding", "user-agent",
                "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-port",
                "x-forwarded-proto", "x-forwarded-prefix",
                "messageid", "authorization", "timestamp", "fingerprint",
                "x-environment-id", "x-environment-name", "x-correlation-id",
                "connectionid", "request-start-time",
                "messagetype", "responseaction"
        );

        // The message's own messageType wins over a computed one — see withSifResponseHeaders. Read it
        // case-insensitively, because stored header casing is not guaranteed.
        String storedMessageType = msg.getSifHeaders() == null ? null
                : msg.getSifHeaders().entrySet().stream()
                        .filter(e -> "messagetype".equalsIgnoreCase(e.getKey()))
                        .map(Map.Entry::getValue)
                        .filter(v -> v != null && !v.isBlank())
                        .findFirst().orElse(null);

        // messageId is passed as null: the lowercase "messageid" below is the long-standing header
        // consumers feed back as deleteMessageId, and HTTP header names are case-insensitive, so
        // setting both would duplicate it.
        var builder = withSifResponseHeaders(
                ResponseEntity.ok(), HttpStatus.OK, requestAction, null, storedMessageType)
                .header("messageid", msg.getMessageId().toString());

        if (msg.getSifHeaders() != null) {
            for (Map.Entry<String, String> h : msg.getSifHeaders().entrySet()) {
                if (h.getValue() != null && !RESPONSE_HEADER_BLACKLIST.contains(h.getKey().toLowerCase())) {
                    builder = builder.header(h.getKey(), h.getValue());
                }
            }
        }

        return builder.body(msg.getPayload());
    }

    /**
     * Lists messages currently reserved by any consumer connection in the queue's
     * consumer-group pending entries list (PEL).
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Messages currently held by a consumer"),
        @ApiResponse(responseCode = "400", description = "Malformed queue or message id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Queue or message does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "503", description = "Message storage is unavailable",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping("/queues/{queueId}/messages/pending")
    public ResponseEntity<?> getPendingMessages(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID queueId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken) {

        QueueDTO queue = queueService.getQueueForRouting(queueId);
        boolean isBrokerServices = "broker-services".equals(fingerprint)
                && isValidInternalToken(serviceToken);
        if (!isBrokerServices
                && (environmentId == null || !queue.ownerId().toString().equals(environmentId))) {
            return forbiddenResponse(environmentId);
        }

        int boundedLimit = Math.max(1, Math.min(limit, 1000));
        var pendingMessages = deliveryService.getPendingMessages(
                queueId, domainId, solutionId, queue.ownerId(), boundedLimit);
        return ResponseEntity.ok(new PendingQueueMessagesResponse(pendingMessages));
    }

    /**
     * Acknowledges and deletes the globally oldest pending message regardless of
     * which consumer connection currently owns it.
     */
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Oldest pending message discarded"),
        @ApiResponse(responseCode = "400", description = "Malformed queue or message id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Queue or message does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "503", description = "Message storage is unavailable",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @DeleteMapping("/queues/{queueId}/messages/pending/oldest")
    public ResponseEntity<?> discardOldestPendingMessage(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID queueId,
            @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken) {

        QueueDTO queue = queueService.getQueueForRouting(queueId);
        boolean isBrokerServices = "broker-services".equals(fingerprint)
                && isValidInternalToken(serviceToken);
        if (!isBrokerServices
                && (environmentId == null || !queue.ownerId().toString().equals(environmentId))) {
            return forbiddenResponse(environmentId);
        }

        Optional<PendingQueueMessageResponse> discarded =
                deliveryService.discardOldestPendingMessage(
                        queueId, domainId, solutionId, queue.ownerId());
        return discarded.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Delete a specific message from a queue by its SIF-level messageId.
     *
     * <p>Per SIF 3.7, allows a queue owner to remove a specific message from their queue
     * without having to get-next first. Scans the stream for the entry, acknowledges it,
     * decrements the ref count, deletes the payload if unreferenced, and removes the entry.</p>
     *
     * @param queueId   the queue ID
     * @param messageId the SIF-level message UUID
     * @return 204 No Content on success, 404 if not found, 403 if not owner
     */
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Message deleted"),
        @ApiResponse(responseCode = "410", description = "The message has already been consumed or deleted (D7: 410, not 404)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "400", description = "Malformed queue or message id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Queue or message does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "503", description = "Message storage is unavailable",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @DeleteMapping("/queues/{queueId}/messages/{messageId}")
    public ResponseEntity<?> deleteMessage(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID queueId,
            @PathVariable String messageId,
            @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken,
            @RequestHeader(value = "requestAction", required = false) String requestAction) {

        log.info("DELETE message: queueId={}, messageId={}, environmentId={}", queueId, messageId, environmentId);

        // Verify queue ownership — fail-closed: deny if environmentId is absent or mismatched.
        // broker-services fingerprint bypass is only honored with a valid Internal_Service_Token.
        QueueDTO queue = queueService.getQueueForRouting(queueId);
        boolean isBrokerServices = "broker-services".equals(fingerprint) && isValidInternalToken(serviceToken);
        if (!isBrokerServices
                && (environmentId == null || !queue.ownerId().toString().equals(environmentId))) {
            log.warn("Ownership violation: environment {} attempted to delete message from queue {}",
                    environmentId, queueId);
            return forbiddenResponse(environmentId);
        }

        UUID envUuid = isBrokerServices
                ? queue.ownerId()
                : resolveEnvironmentId(environmentId, queue.ownerId());

        try {
            boolean deleted = deliveryService.deleteMessage(queueId, messageId, domainId, solutionId, envUuid);

            if (!deleted) {
                // D7: 410 GONE, not 404 — queueMessageDelete declares no 404 for an absent message.
                // The queue itself exists (ownership was verified above); only the message is gone.
                SifErrorResponse error = new SifErrorResponse(
                        UUID.randomUUID().toString(), HttpStatus.GONE.value(),
                        "MSG_410", "Message no longer in pending list",
                        "Message " + messageId + " not found in queue " + queueId,
                        "BROKER", Instant.now(), null, null);
                return withSifResponseHeaders(
                        ResponseEntity.status(HttpStatus.GONE), HttpStatus.GONE, requestAction, messageId, null)
                        .body(SifErrorResponseEnvelope.of(error));
            }

            return withSifResponseHeaders(
                    ResponseEntity.status(HttpStatus.NO_CONTENT), HttpStatus.NO_CONTENT, requestAction, messageId, null)
                    .build();
        } catch (Exception e) {
            log.error("Failed to delete message: queueId={}, messageId={}", queueId, messageId, e);
            SifErrorResponse error = new SifErrorResponse(
                    UUID.randomUUID().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "MSG_500", "Internal error deleting message",
                    e.getMessage(),
                    "BROKER", Instant.now(), null, null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(SifErrorResponseEnvelope.of(error));
        }
    }

    /**
     * Acknowledge a message (standalone endpoint — deprecated in favor of ACK-and-GET-NEXT).
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Message acknowledged"),
        @ApiResponse(responseCode = "400", description = "Malformed queue or message id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Queue or message does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "503", description = "Message storage is unavailable",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PostMapping("/messages/{messageId}/acknowledge")
    public ResponseEntity<AcknowledgeMessageResponse> acknowledgeMessage(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID messageId) {
        log.warn("Standalone acknowledge endpoint is deprecated — use ACK-and-GET-NEXT pattern via GET /queues/{queueId}/messages");

        AcknowledgeMessageResponse response = new AcknowledgeMessageResponse(
                messageId, "ACKNOWLEDGED", Instant.now());
        return ResponseEntity.ok(response);
    }

    /**
     * Get dead-letter queue messages.
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dead-letter messages for the queue"),
        @ApiResponse(responseCode = "400", description = "Malformed queue or message id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Queue or message does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "503", description = "Message storage is unavailable",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @GetMapping("/queues/{queueId}/dead-letter")
    public ResponseEntity<?> getDeadLetterMessages(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID queueId,
            @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken) {

        QueueDTO queue = queueService.getQueueDetails(queueId);
        // Fail-closed: deny if environmentId is absent or mismatched.
        // broker-services fingerprint bypass is only honored with a valid Internal_Service_Token.
        boolean isBrokerServices = "broker-services".equals(fingerprint) && isValidInternalToken(serviceToken);
        if (!isBrokerServices
                && (environmentId == null || !queue.ownerId().toString().equals(environmentId))) {
            log.warn("Ownership violation: environment {} attempted to access queue {}", environmentId, queueId);
            return forbiddenResponse(environmentId);
        }

        UUID envUuid = resolveEnvironmentId(environmentId, queue.ownerId());

        List<MessageMetadata> messages = deadLetterService.getDeadLetterMessages(
                queueId, domainId, solutionId, envUuid);

        List<MessageResponse> messageResponses = messages.stream()
                .map(msg -> new MessageResponse(
                        msg.getMessageId(), msg.getQueueId(), msg.getStorageKey(),
                        msg.getEnqueuedAt(), msg.getDeliveredAt(), msg.getDeliveryAttempts(),
                        msg.getStatus(), msg.getPayload()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(new DeliverMessagesResponse(messageResponses));
    }

    /**
     * Reprocess a dead-letter message.
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Message returned for reprocessing"),
        @ApiResponse(responseCode = "400", description = "Malformed queue or message id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Queue or message does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "503", description = "Message storage is unavailable",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PostMapping("/messages/{messageId}/reprocess")
    public ResponseEntity<Void> reprocessMessage(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID messageId) {
        log.info("Reprocessing dead-letter message: messageId={}", messageId);
        // Note: Full reprocess requires queueId, dlqStreamId, storageKey, and environment context.
        // This endpoint is a placeholder — the full implementation requires additional parameters
        // that would typically come from the DLQ query response.
        log.warn("Reprocess endpoint requires additional context (queueId, dlqStreamId, storageKey, environmentId)");
        return ResponseEntity.ok().build();
    }

    /**
     * Deliver messages from a queue (legacy batch endpoint).
     */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Delivery triggered"),
        @ApiResponse(responseCode = "403", description = "The caller does not own this queue",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "400", description = "Malformed queue or message id",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "404", description = "Queue or message does not exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected server error",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class))),
        @ApiResponse(responseCode = "503", description = "Message storage is unavailable",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SifErrorResponseEnvelope.class)))
    })
    @PostMapping("/queues/{queueId}/deliver")
    public ResponseEntity<?> deliverMessages(
            @PathVariable String domainId,
            @PathVariable String solutionId,
            @PathVariable UUID queueId,
            @Valid @RequestBody DeliverMessagesRequest request,
            @RequestHeader(value = "X-Environment-Id", required = false) String environmentId,
            @RequestHeader(value = "X-Environment-Fingerprint", required = false) String fingerprint,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String serviceToken) {

        QueueDTO queue = queueService.getQueueDetails(queueId);
        // Fail-closed: deny if environmentId is absent or mismatched.
        // broker-services fingerprint bypass is only honored with a valid Internal_Service_Token.
        boolean isBrokerServices = "broker-services".equals(fingerprint) && isValidInternalToken(serviceToken);
        if (!isBrokerServices
                && (environmentId == null || !queue.ownerId().toString().equals(environmentId))) {
            return forbiddenResponse(environmentId);
        }

        log.warn("Legacy deliver endpoint — use GET /queues/{queueId}/messages with ACK-and-GET-NEXT pattern");
        return ResponseEntity.ok(new DeliverMessagesResponse(List.of()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Applies the SIF response headers that {@code queueMessageGet} and {@code queueMessageDelete}
     * declare as required (D8, D9).
     *
     * <p><b>These are constructed, not forwarded.</b> The inbound {@code timestamp} is HMAC material
     * ({@code HMAC-SHA256({sessionToken}:{timestamp}, sharedSecret)}) and is stripped by
     * {@code HeaderSanitizer} at connector ingress, so it is never stored in the message's
     * {@code sifHeaders} and there is nothing to forward — relaxing
     * {@code RESPONSE_HEADER_BLACKLIST} would be a no-op. The response {@code timestamp} is a
     * different thing that happens to share the name: wire-format envelope metadata, not a
     * credential. Authentication is one-directional; the broker never authenticates itself to the
     * consumer.</p>
     *
     * <p>Applied ONLY to the queue-message operations. The infrastructure object operations
     * (`/environments/environment`, `/subscriptions/subscription`, `/providers/provider`, …) declare
     * no response headers at all, so adding them there would be non-conformant noise rather than a
     * fix.</p>
     *
     * <p><b>{@code messageType} is not blindly computed.</b> On a delivered message it describes what
     * kind of SIF message the payload <em>is</em> — a delivered event carries {@code EVENT} — so the
     * stored value wins when present. Only when the message carries none does this fall back to
     * {@code ERROR} for a {@code >= 300} status and {@code RESPONSE} otherwise. Overwriting a stored
     * {@code EVENT} with {@code RESPONSE} would satisfy the header requirement while corrupting the
     * message's own description of itself.</p>
     *
     * @param builder           the response builder to decorate
     * @param status            the status being returned, used only to pick the fallback messageType
     * @param requestAction     the request's {@code requestAction}, echoed back as {@code responseAction}
     * @param messageId         the message this response concerns, or {@code null} when there is none
     * @param storedMessageType the message's own {@code messageType}, or {@code null} to derive one
     * @return the same builder, for chaining
     */
    private static ResponseEntity.BodyBuilder withSifResponseHeaders(
            ResponseEntity.BodyBuilder builder,
            HttpStatus status,
            String requestAction,
            String messageId,
            String storedMessageType) {

        String messageType = (storedMessageType != null && !storedMessageType.isBlank())
                ? storedMessageType
                : (status.value() >= 300 ? "ERROR" : "RESPONSE");

        builder = builder
                .header("timestamp", Instant.now().toString())
                .header("messageType", messageType);

        // responseAction must echo the request's requestAction. Omitted when the request sent none,
        // rather than invented — a fabricated value would be worse than an absent one.
        if (requestAction != null && !requestAction.isBlank()) {
            builder = builder.header("responseAction", requestAction);
        }
        if (messageId != null && !messageId.isBlank()) {
            builder = builder.header("messageId", messageId);
        }
        return builder;
    }

    /**
     * Extracts a matrix parameter value from the original request URI.
     *
     * <p>Matrix params use semicolons: {@code /path;key=value;key2=value2}.
     * The filter in {@link WebMvcConfig} stores the original URI as a request
     * attribute before stripping the matrix portion for routing.</p>
     *
     * @param request   the current HTTP request
     * @param paramName the matrix parameter name to extract
     * @return the parameter value, or {@code null} if not present
     */
    private static String extractMatrixParam(HttpServletRequest request, String paramName) {
        String originalUri = (String) request.getAttribute(WebMvcConfig.ORIGINAL_URI_ATTR);
        if (originalUri == null) {
            return null;
        }
        int semiIdx = originalUri.indexOf(';');
        if (semiIdx < 0) {
            return null;
        }
        String matrixPart = originalUri.substring(semiIdx + 1);
        for (String param : matrixPart.split(";")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(paramName)) {
                String value = kv[1].trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private UUID resolveEnvironmentId(String environmentIdHeader, UUID fallback) {
        if (environmentIdHeader != null && !environmentIdHeader.isEmpty()) {
            try {
                return UUID.fromString(environmentIdHeader);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid environmentId header: {}", environmentIdHeader);
            }
        }
        return fallback;
    }

    private ResponseEntity<?> forbiddenResponse(String environmentId) {
        String detail = environmentId == null
                ? "Missing X-Environment-Id header. Caller identity is required."
                : "Environment " + environmentId + " is not authorized to access this resource.";
        SifErrorResponse error = new SifErrorResponse(
                UUID.randomUUID().toString(), HttpStatus.FORBIDDEN.value(),
                "AUTHZ_002", "Ownership violation",
                detail,
                "BROKER", Instant.now(), null, null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(SifErrorResponseEnvelope.of(error));
    }

    /**
     * Validates the presented internal service token using constant-time comparison.
     *
     * @param presentedToken the token from the X-Internal-Service-Token header
     * @return true if the token is valid, false otherwise
     */
    private boolean isValidInternalToken(String presentedToken) {
        if (internalServiceToken == null || internalServiceToken.isEmpty()
                || presentedToken == null || presentedToken.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                internalServiceToken.getBytes(StandardCharsets.UTF_8),
                presentedToken.getBytes(StandardCharsets.UTF_8));
    }
}
