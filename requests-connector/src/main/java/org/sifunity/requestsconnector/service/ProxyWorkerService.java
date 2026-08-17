// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.extension.ProviderRequestContext;
import org.sifunity.broker.common.extension.ProviderRequestInterceptor;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.requestsconnector.client.ProviderHttpClient;
import org.sifunity.requestsconnector.client.ResponsesConnectorClient;
import org.sifunity.requestsconnector.config.ProxyStreamConfig;
import org.sifunity.requestsconnector.dto.ProxyRequestMetadata;
import org.sifunity.requestsconnector.dto.ProviderRawResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Background worker service for processing DELAYED_to_IMMEDIATE proxy requests.
 *
 * <p>This service implements the background processing component of the DELAYED_to_IMMEDIATE
 * pattern using Redis Streams consumer groups. When a consumer sends a DELAYED request but
 * the provider only supports IMMEDIATE pattern, the Requests Connector acts as a proxy:</p>
 *
 * <ol>
 *   <li>The request metadata is stored in the proxy-requests-stream via XADD</li>
 *   <li>HTTP 202 Accepted is returned to the consumer immediately</li>
 *   <li>This background worker reads from the stream via XREADGROUP</li>
 *   <li>The worker makes a synchronous HTTP call to the provider</li>
 *   <li>The worker routes the provider's response to the Responses Connector</li>
 *   <li>On success: XACK + XDEL (acknowledged and trimmed)</li>
 *   <li>On failure: message stays in PEL — XAUTOCLAIM redelivers after idle threshold</li>
 * </ol>
 *
 * <p>This design structurally eliminates the LREM-before-rightPush bug (Defect 4) —
 * there is no two-step sequence that can lose entries. If the worker crashes mid-processing,
 * the unacknowledged message remains in the PEL and is reclaimed by XAUTOCLAIM.</p>
 *
 * <p>Requirements: 2.1, 2.4, 3.1</p>
 */
@Service
@Slf4j
public class ProxyWorkerService {

    private static final String CONNECTOR_NAME = "requests-connector";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ProviderHttpClient providerClient;
    private final ResponsesConnectorClient responsesClient;
    private final ConnectorMetricsService connectorMetricsService;
    private final TracingUtilsBase tracingUtils;
    private final List<ProviderRequestInterceptor> providerInterceptors;
    private final ProxyStreamConfig proxyStreamConfig;
    private final int maxRetries;
    private final int maxPages;
    private final String consumerName;

    /**
     * Constructs a ProxyWorkerService with required dependencies.
     *
     * @param stringRedisTemplate StringRedisTemplate for Redis Streams operations
     * @param objectMapper Jackson ObjectMapper for deserializing stream fields
     * @param providerClient HTTP client for making calls to providers
     * @param responsesClient HTTP client for routing responses to Responses Connector
     * @param connectorMetricsService service for recording connector and paging metrics
     * @param tracingUtils tracing utilities for creating OpenTelemetry spans
     * @param providerInterceptors optional interceptors invoked before/after provider calls
     * @param proxyStreamConfig configuration for stream keys, idle threshold, and max retries
     * @param maxRetries maximum number of retry attempts (default: 3)
     * @param maxPages maximum number of pages to retrieve in a paging loop (default: 1000)
     */
    @Autowired
    public ProxyWorkerService(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            ProviderHttpClient providerClient,
            ResponsesConnectorClient responsesClient,
            ConnectorMetricsService connectorMetricsService,
            TracingUtilsBase tracingUtils,
            @Autowired(required = false) List<ProviderRequestInterceptor> providerInterceptors,
            ProxyStreamConfig proxyStreamConfig,
            @Value("${proxy.worker.max-retries:3}") int maxRetries,
            @Value("${proxy.worker.max-pages:1000}") int maxPages) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.providerClient = providerClient;
        this.responsesClient = responsesClient;
        this.connectorMetricsService = connectorMetricsService;
        this.tracingUtils = tracingUtils;
        this.providerInterceptors = providerInterceptors != null ? providerInterceptors : List.of();
        this.proxyStreamConfig = proxyStreamConfig;
        this.maxRetries = maxRetries;
        this.maxPages = maxPages;

        // Consumer name = hostname for observability. Uniqueness is not required by consumer groups.
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        this.consumerName = host;

        log.info("ProxyWorkerService initialized with maxRetries={}, maxPages={}, consumerName={}",
                maxRetries, maxPages, this.consumerName);
        if (!this.providerInterceptors.isEmpty()) {
            log.info("ProxyWorkerService: {} provider request interceptor(s) registered",
                    this.providerInterceptors.size());
        }
    }

    /**
     * Processes pending proxy requests from the Redis Stream via XREADGROUP.
     *
     * <p>This method is scheduled to run at fixed intervals (default: every 1 second).
     * It polls the Redis Stream using XREADGROUP with BLOCK, processes messages by making
     * synchronous HTTP calls to providers, and routes responses to the Responses Connector.</p>
     *
     * <p>Processing flow:</p>
     * <ol>
     *   <li>XREADGROUP reads the next undelivered message from the stream</li>
     *   <li>Deserialize stream field map back to ProxyRequestMetadata</li>
     *   <li>Make synchronous HTTP call to provider endpoint</li>
     *   <li>Route successful response to Responses Connector</li>
     *   <li>XACK + XDEL on success (acknowledged and trimmed)</li>
     *   <li>On failure: don't ACK — message stays in PEL for XAUTOCLAIM recovery</li>
     * </ol>
     *
     * <p>Validates Requirements 2.1, 2.4, 3.1</p>
     */
    @SuppressWarnings("unchecked")
    @Scheduled(fixedDelayString = "${proxy.worker.poll-interval:1000}")
    public void processProxyRequests() {
        List<MapRecord<String, Object, Object>> records;
        try {
            records = stringRedisTemplate.opsForStream().read(
                    Consumer.from(ProxyStreamConfig.CONSUMER_GROUP, consumerName),
                    StreamReadOptions.empty().count(1).block(Duration.ofMillis(1000)),
                    StreamOffset.create(ProxyStreamConfig.STREAM_KEY, ReadOffset.lastConsumed()));
        } catch (Exception e) {
            log.warn("Error reading from proxy-requests-stream: {}", e.getMessage());
            return;
        }

        if (records == null || records.isEmpty()) {
            return;
        }

        MapRecord<String, Object, Object> record = records.get(0);
        String streamId = record.getId().getValue();
        // StringRedisTemplate serializes all keys/values as Strings
        Map<String, String> fieldMap = new HashMap<>();
        record.getValue().forEach((k, v) -> fieldMap.put(k.toString(), v != null ? v.toString() : null));

        ProxyRequestMetadata metadata;
        try {
            metadata = parseStreamFieldMap(fieldMap);
        } catch (Exception e) {
            log.warn("Failed to deserialize stream entry streamId={}: {}. ACKing to prevent redelivery loop.",
                    streamId, e.getMessage());
            // ACK + DEL unreadable entries to prevent infinite redelivery
            stringRedisTemplate.opsForStream().acknowledge(
                    ProxyStreamConfig.STREAM_KEY, ProxyStreamConfig.CONSUMER_GROUP, streamId);
            stringRedisTemplate.opsForStream().delete(ProxyStreamConfig.STREAM_KEY, streamId);
            return;
        }

        log.debug("Processing proxy request from stream: messageId={}, streamId={}",
                metadata.getMessageId(), streamId);

        processMessage(metadata, streamId);
    }

    /**
     * XAUTOCLAIM recovery sweep — reclaims orphaned messages from crashed workers.
     *
     * <p>Runs every 30s (configurable via {@code proxy.worker.recovery-interval}).
     * Uses XPENDING to find messages idle beyond the configured threshold, then XCLAIM
     * to atomically reassign them to this consumer.</p>
     *
     * <p>For each claimed message:</p>
     * <ul>
     *   <li>If delivery count >= maxRetries: move to DLQ stream (XADD + XACK + XDEL)</li>
     *   <li>If delivery count < maxRetries: process normally via {@link #processMessage}</li>
     * </ul>
     *
     * <p>Key properties:</p>
     * <ul>
     *   <li>Atomic and idempotent — safe for multiple replicas to run concurrently.
     *       Once a message is claimed, its idle time resets, so other workers won't re-claim it.</li>
     *   <li>Only reclaims messages idle > threshold (default 120s) — live workers' in-flight
     *       messages (idle < threshold) are never stolen.</li>
     *   <li>Eliminates the entire class of "orphaned processing list" bugs — no scan,
     *       no heartbeat, no position assumptions.</li>
     * </ul>
     *
     * <p>Validates Requirements 2.1, 2.4</p>
     */
    @Scheduled(fixedDelayString = "${proxy.worker.recovery-interval:30000}")
    public void recoverOrphanedMessages() {
        try {
            // Step 1: XPENDING — find messages idle beyond the threshold
            PendingMessages pendingMessages = stringRedisTemplate.opsForStream()
                    .pending(ProxyStreamConfig.STREAM_KEY, ProxyStreamConfig.CONSUMER_GROUP,
                            Range.unbounded(), 10);

            if (pendingMessages == null || pendingMessages.size() == 0) {
                return;
            }

            long idleThresholdMs = proxyStreamConfig.getIdleThresholdMs();
            List<RecordId> expiredIds = new ArrayList<>();
            Map<String, Long> deliveryCountMap = new HashMap<>();

            for (PendingMessage entry : pendingMessages) {
                Duration elapsed = entry.getElapsedTimeSinceLastDelivery();
                if (elapsed != null && elapsed.toMillis() >= idleThresholdMs) {
                    expiredIds.add(RecordId.of(entry.getIdAsString()));
                    deliveryCountMap.put(entry.getIdAsString(), entry.getTotalDeliveryCount());
                }
            }

            if (expiredIds.isEmpty()) {
                return;
            }

            // Step 2: XCLAIM — atomically reassign idle messages to this consumer
            List<MapRecord<String, Object, Object>> claimed = stringRedisTemplate.opsForStream().claim(
                    ProxyStreamConfig.STREAM_KEY,
                    ProxyStreamConfig.CONSUMER_GROUP,
                    consumerName,
                    Duration.ofMillis(idleThresholdMs),
                    expiredIds.toArray(new RecordId[0]));

            if (claimed == null || claimed.isEmpty()) {
                return;
            }

            log.info("XAUTOCLAIM recovery: claimed {} orphaned message(s)", claimed.size());

            // Step 3: Process each claimed message
            for (MapRecord<String, Object, Object> record : claimed) {
                String streamId = record.getId().getValue();
                long deliveryCount = deliveryCountMap.getOrDefault(streamId, 1L);

                if (deliveryCount >= proxyStreamConfig.getMaxRetries()) {
                    // Exceeded max retries — move to DLQ
                    moveToDeadLetterQueue(record, streamId, deliveryCount);
                } else {
                    // Process normally
                    Map<String, String> fieldMap = new HashMap<>();
                    record.getValue().forEach((k, v) -> fieldMap.put(k.toString(), v != null ? v.toString() : null));

                    ProxyRequestMetadata metadata;
                    try {
                        metadata = parseStreamFieldMap(fieldMap);
                    } catch (Exception e) {
                        log.warn("Recovery: failed to deserialize claimed entry streamId={}: {}. "
                                + "Moving to DLQ.", streamId, e.getMessage());
                        moveToDeadLetterQueue(record, streamId, deliveryCount);
                        continue;
                    }

                    log.info("Recovery: reprocessing orphaned message messageId={}, streamId={}, deliveryCount={}",
                            metadata.getMessageId(), streamId, deliveryCount);
                    processMessage(metadata, streamId);
                }
            }
        } catch (Exception e) {
            log.warn("Error during XAUTOCLAIM recovery sweep: {}", e.getMessage());
        }
    }

    /**
     * Moves a message to the dead-letter queue stream after exceeding max delivery attempts.
     *
     * <p>Performs XADD to DLQ stream + XACK + XDEL on the main stream — atomically
     * removing the poison message from further redelivery.</p>
     *
     * @param record the stream record to dead-letter
     * @param streamId the stream entry ID
     * @param deliveryCount the number of times this message has been delivered
     */
    private void moveToDeadLetterQueue(MapRecord<String, Object, Object> record,
                                       String streamId, long deliveryCount) {
        try {
            // XADD to DLQ stream with original fields + metadata
            Map<String, String> dlqFields = new HashMap<>();
            record.getValue().forEach((k, v) -> dlqFields.put(k.toString(), v != null ? v.toString() : null));
            dlqFields.put("originalStreamId", streamId);
            dlqFields.put("deliveryCount", String.valueOf(deliveryCount));
            dlqFields.put("movedToDlqAt", Instant.now().toString());

            stringRedisTemplate.opsForStream().add(
                    MapRecord.create(ProxyStreamConfig.DLQ_STREAM_KEY, dlqFields));

            // XACK + XDEL on main stream
            stringRedisTemplate.opsForStream().acknowledge(
                    ProxyStreamConfig.STREAM_KEY, ProxyStreamConfig.CONSUMER_GROUP, streamId);
            stringRedisTemplate.opsForStream().delete(ProxyStreamConfig.STREAM_KEY, streamId);

            log.warn("Moved poison message to DLQ: streamId={}, deliveryCount={}", streamId, deliveryCount);
        } catch (Exception e) {
            log.error("Failed to move message to DLQ: streamId={}", streamId, e);
        }
    }

    /**
     * Processes a single message read from the stream.
     *
     * <p>On success: XACK + XDEL. On failure: don't ACK — message stays in PEL
     * and XAUTOCLAIM will redeliver after idle threshold.</p>
     *
     * <p>Package-private for testability.</p>
     *
     * @param metadata the deserialized proxy request metadata
     * @param streamId the Redis Stream entry ID (for XACK/XDEL)
     */
    void processMessage(ProxyRequestMetadata metadata, String streamId) {
        tracingUtils.traceOperation(
            "sif.proxy.process",
            Map.of(
                "sif.message_id", metadata.getMessageId().toString(),
                "sif.provider_endpoint", metadata.getProviderEndpoint(),
                "sif.http_method", metadata.getHttpMethod() != null ? metadata.getHttpMethod() : "POST",
                "sif.stream_id", streamId
            ),
            () -> {
        // Build interceptor context
        SifMessageEnvelope<?> reqEnvForCtx = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
        Map<String, String> reqHeadersForCtx = reqEnvForCtx != null ? reqEnvForCtx.getHeaders() : Collections.emptyMap();
        if (reqHeadersForCtx == null) reqHeadersForCtx = Collections.emptyMap();
        final ProviderRequestContext interceptorContext = metadata.getProviderEnvironmentId() != null
                ? new ProviderRequestContext(
                        metadata.getDomainId(), metadata.getSolutionId(),
                        metadata.getProviderEnvironmentId(), metadata.getProviderEndpoint(), reqHeadersForCtx)
                : null;

        try {
            // Invoke provider request interceptors before the outbound call
            if (interceptorContext != null) {
                providerInterceptors.forEach(i -> i.beforeProviderRequest(interceptorContext));
            }

            // Make synchronous call to provider
            SifMessageEnvelope<?> requestEnvelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
            Map<String, String> headers = requestEnvelope.getHeaders() != null
                    ? requestEnvelope.getHeaders() : Collections.emptyMap();
            ProviderRawResponse firstResponse = tracingUtils.traceOperation(
                "sif.proxy.provider.call",
                Map.of(
                    "sif.provider_endpoint", metadata.getProviderEndpoint(),
                    "sif.http_method", metadata.getHttpMethod() != null ? metadata.getHttpMethod() : "POST",
                    "sif.page", "1"
                ),
                () -> providerClient.sendRequestRawWithHeaders(
                    metadata.getProviderEndpoint(),
                    requestEnvelope.getPayload(),
                    headers,
                    metadata.getHttpMethod())
            );

            // Check if paging is required
            if (isPagingRequired(metadata, firstResponse)) {
                executePagedQuery(metadata, requestEnvelope, headers, firstResponse);
            } else {
                // Single-response path
                Map<String, String> responseHeaders = new HashMap<>();
                if (firstResponse.getHeaders() != null) {
                    for (String key : NAVIGATION_HEADER_KEYS) {
                        String value = getHeaderCaseInsensitive(firstResponse.getHeaders(), key);
                        if (value != null) {
                            responseHeaders.put(key, value);
                        }
                    }
                }
                String zoneId = getHeaderCaseInsensitive(headers, "zoneId");
                String contextId = getHeaderCaseInsensitive(headers, "contextId");
                if (zoneId != null) responseHeaders.put("zoneId", zoneId);
                if (contextId != null) responseHeaders.put("contextId", contextId);

                SifMessageEnvelope<Object> responseEnvelope = SifMessageEnvelope.<Object>builder()
                    .messageType(MessageType.RESPONSE)
                    .serviceName(requestEnvelope.getServiceName())
                    .serviceType(requestEnvelope.getServiceType())
                    .requestId(requestEnvelope.getRequestId())
                    .headers(responseHeaders)
                    .payload(firstResponse.getBody())
                    .build();

                responsesClient.routeResponse(
                    metadata.getEnvironmentId(),
                    metadata.getEnvironmentName(),
                    metadata.getDomainId(),
                    metadata.getSolutionId(),
                    metadata.getConsumerQueueId(),
                    requestEnvelope.getRequestId(),
                    responseEnvelope,
                    metadata.getRelativeServicePath());
            }

            log.info("Successfully processed proxy request: messageId={}, streamId={}",
                metadata.getMessageId(), streamId);

            // Success: XACK + XDEL — acknowledged and memory reclaimed
            stringRedisTemplate.opsForStream().acknowledge(
                    ProxyStreamConfig.STREAM_KEY, ProxyStreamConfig.CONSUMER_GROUP, streamId);
            stringRedisTemplate.opsForStream().delete(ProxyStreamConfig.STREAM_KEY, streamId);

        } catch (Exception e) {
            // Failure: DO NOT ACK — message stays in PEL.
            // XAUTOCLAIM will redeliver after idle threshold.
            // This structurally eliminates the LREM-before-rightPush bug (Defect 4).
            log.error("Failed to process proxy request: messageId={}, streamId={}. "
                    + "Message remains in PEL for XAUTOCLAIM redelivery.",
                    metadata.getMessageId(), streamId, e);
        } finally {
            if (interceptorContext != null) {
                providerInterceptors.forEach(i -> i.afterProviderRequest(interceptorContext));
            }
        }
            }
        );
    }

    /**
     * Parses a Redis Stream field map back into a ProxyRequestMetadata instance.
     *
     * <p>This is the inverse of {@code DelayedRequestHandler.buildStreamFieldMap()}.
     * Complex fields (requestEnvelope) are deserialized from JSON strings.</p>
     *
     * @param fieldMap the stream entry's field map
     * @return the deserialized ProxyRequestMetadata
     */
    ProxyRequestMetadata parseStreamFieldMap(Map<String, String> fieldMap) {
        try {
            Object requestEnvelope = objectMapper.readValue(
                    fieldMap.get("requestEnvelope"), SifMessageEnvelope.class);

            return ProxyRequestMetadata.builder()
                    .messageId(parseUuid(fieldMap.get("messageId")))
                    .consumerQueueId(parseUuid(fieldMap.get("consumerQueueId")))
                    .environmentId(parseUuid(fieldMap.get("environmentId")))
                    .environmentName(nullIfEmpty(fieldMap.get("environmentName")))
                    .domainId(nullIfEmpty(fieldMap.get("domainId")))
                    .solutionId(nullIfEmpty(fieldMap.get("solutionId")))
                    .httpMethod(nullIfEmpty(fieldMap.get("httpMethod")))
                    .providerEnvironmentId(parseUuid(fieldMap.get("providerEnvironmentId")))
                    .relativeServicePath(nullIfEmpty(fieldMap.get("relativeServicePath")))
                    .providerEndpoint(nullIfEmpty(fieldMap.get("providerEndpoint")))
                    .createdAt(fieldMap.get("createdAt") != null && !fieldMap.get("createdAt").isEmpty()
                            ? Instant.parse(fieldMap.get("createdAt")) : Instant.now())
                    .requestEnvelope(requestEnvelope)
                    .retryCount(0) // Delivery count tracked natively by Redis Streams
                    .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize stream entry requestEnvelope", e);
        }
    }

    /**
     * Parses a UUID from a stream field value, returning null for empty or null strings.
     */
    private static UUID parseUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return UUID.fromString(value);
    }

    /**
     * Returns null if the string is null or empty, otherwise returns the string as-is.
     */
    private static String nullIfEmpty(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value;
    }

    /**
     * Checks if paging conditions are met for a proxy request.
     *
     * <p>Returns true if and only if ALL of the following conditions are satisfied:</p>
     * <ol>
     *   <li>HTTP method is GET</li>
     *   <li>The original request contains a queryIntention header with value "ALL"</li>
     *   <li>Response status code is 200</li>
     *   <li>Response headers contain both "navigationPage" and "navigationLastPage"</li>
     *   <li>navigationPage value does NOT equal navigationLastPage value</li>
     * </ol>
     *
     * <p>Package-private for testability by property-based tests in the same package.</p>
     */
    boolean isPagingRequired(ProxyRequestMetadata metadata, ProviderRawResponse response) {
        if (!"GET".equalsIgnoreCase(metadata.getHttpMethod())) {
            return false;
        }

        SifMessageEnvelope<?> requestEnvelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
        Map<String, String> requestHeaders = requestEnvelope != null ? requestEnvelope.getHeaders() : Collections.emptyMap();
        if (requestHeaders == null || !"ALL".equals(getHeaderCaseInsensitive(requestHeaders, "queryIntention"))) {
            return false;
        }

        if (response.getStatusCode() != 200) {
            return false;
        }

        Map<String, String> responseHeaders = response.getHeaders();
        if (responseHeaders == null) {
            return false;
        }
        String navigationPage = getHeaderCaseInsensitive(responseHeaders, "navigationPage");
        String navigationLastPage = getHeaderCaseInsensitive(responseHeaders, "navigationLastPage");
        if (navigationPage == null || navigationLastPage == null) {
            return false;
        }

        return !navigationPage.equals(navigationLastPage);
    }

    /**
     * Builds HTTP headers for the next page request in a paging loop.
     *
     * <p>Package-private for testability.</p>
     */
    Map<String, String> buildNextPageHeaders(Map<String, String> originalHeaders,
            ProviderRawResponse previousResponse, int nextPage, int lastPage) {
        Map<String, String> headers = new HashMap<>(originalHeaders);

        headers.put("navigationPage", String.valueOf(nextPage));

        Map<String, String> prevHeaders = previousResponse.getHeaders();
        String navId = prevHeaders != null ? getHeaderCaseInsensitive(prevHeaders, "navigationId") : null;
        if (navId != null) {
            headers.put("navigationId", navId);
        }

        if (nextPage < lastPage) {
            headers.put("queryIntention", "ALL");
        } else {
            headers.remove("queryIntention");
        }

        return headers;
    }

    /**
     * Executes the paging loop: delivers the first page, then iterates pages 2..N.
     *
     * <p>Package-private for testability.</p>
     */
    void executePagedQuery(ProxyRequestMetadata metadata,
            SifMessageEnvelope<?> requestEnvelope, Map<String, String> originalHeaders,
            ProviderRawResponse firstPageResponse) {

        int lastPage = Integer.parseInt(getHeaderCaseInsensitive(firstPageResponse.getHeaders(), "navigationLastPage"));
        int effectiveLastPage = Math.min(lastPage, maxPages);

        String serviceName = requestEnvelope.getServiceName();
        String zone = originalHeaders != null ? getHeaderCaseInsensitive(originalHeaders, "zoneId") : null;

        log.info("Starting paged query: lastPage={}, maxPages={}", lastPage, maxPages);

        connectorMetricsService.recordPagingRequest(CONNECTOR_NAME, serviceName, zone);

        long pagingStartTime = System.currentTimeMillis();

        // Deliver first page with original messageId
        tracingUtils.traceOperation(
            "sif.proxy.deliver.page",
            Map.of(
                "sif.page", "1",
                "sif.message_id", metadata.getMessageId().toString(),
                "sif.consumer_queue_id", metadata.getConsumerQueueId().toString()
            ),
            () -> { deliverPageResponse(metadata, requestEnvelope, firstPageResponse, metadata.getMessageId()); return null; }
        );
        int pagesDelivered = 1;

        ProviderRawResponse previousResponse = firstPageResponse;

        // Loop pages 2..effectiveLastPage
        for (int currentPage = 2; currentPage <= effectiveLastPage; currentPage++) {
            final int page = currentPage;
            try {
                Map<String, String> nextHeaders = buildNextPageHeaders(
                        originalHeaders, previousResponse, page, lastPage);

                ProviderRawResponse pageResponse = tracingUtils.traceOperation(
                    "sif.proxy.provider.call",
                    Map.of(
                        "sif.provider_endpoint", metadata.getProviderEndpoint(),
                        "sif.page", String.valueOf(page),
                        "sif.last_page", String.valueOf(effectiveLastPage)
                    ),
                    () -> providerClient.sendRequestRawWithHeaders(
                        metadata.getProviderEndpoint(), null, nextHeaders, "GET")
                );

                if (pageResponse.getStatusCode() != 200) {
                    String errorMessage = String.format(
                            "Paging failed on page %d of %d: provider returned status %d",
                            page, lastPage, pageResponse.getStatusCode());
                    log.error(errorMessage);
                    long pagingDurationMs = System.currentTimeMillis() - pagingStartTime;
                    connectorMetricsService.recordPagingPagesPerLoop(CONNECTOR_NAME, serviceName, pagesDelivered);
                    connectorMetricsService.recordPagingLoopDuration(CONNECTOR_NAME, serviceName, pagingDurationMs);
                    deliverError(metadata, requestEnvelope, errorMessage);
                    return;
                }

                Map<String, String> pageHeaders = pageResponse.getHeaders();
                if (pageHeaders == null
                        || getHeaderCaseInsensitive(pageHeaders, "navigationPage") == null
                        || getHeaderCaseInsensitive(pageHeaders, "navigationLastPage") == null) {
                    String errorMessage = String.format(
                            "Paging failed on page %d of %d: provider dropped navigation headers",
                            page, lastPage);
                    log.error(errorMessage);
                    long pagingDurationMs = System.currentTimeMillis() - pagingStartTime;
                    connectorMetricsService.recordPagingPagesPerLoop(CONNECTOR_NAME, serviceName, pagesDelivered);
                    connectorMetricsService.recordPagingLoopDuration(CONNECTOR_NAME, serviceName, pagingDurationMs);
                    deliverError(metadata, requestEnvelope, errorMessage);
                    return;
                }

                UUID pageMessageId = UUID.randomUUID();
                String pageNumStr = String.valueOf(page);
                tracingUtils.traceOperation(
                    "sif.proxy.deliver.page",
                    Map.of(
                        "sif.page", pageNumStr,
                        "sif.message_id", pageMessageId.toString(),
                        "sif.consumer_queue_id", metadata.getConsumerQueueId().toString()
                    ),
                    () -> { deliverPageResponse(metadata, requestEnvelope, pageResponse, pageMessageId); return null; }
                );
                pagesDelivered++;
                previousResponse = pageResponse;

            } catch (Exception e) {
                String errorMessage = String.format(
                        "Paging failed on page %d of %d: %s",
                        page, lastPage, e.getMessage());
                log.error(errorMessage, e);
                long pagingDurationMs = System.currentTimeMillis() - pagingStartTime;
                connectorMetricsService.recordPagingPagesPerLoop(CONNECTOR_NAME, serviceName, pagesDelivered);
                connectorMetricsService.recordPagingLoopDuration(CONNECTOR_NAME, serviceName, pagingDurationMs);
                deliverError(metadata, requestEnvelope, errorMessage);
                return;
            }
        }

        long pagingDurationMs = System.currentTimeMillis() - pagingStartTime;
        connectorMetricsService.recordPagingPagesPerLoop(CONNECTOR_NAME, serviceName, pagesDelivered);
        connectorMetricsService.recordPagingLoopDuration(CONNECTOR_NAME, serviceName, pagingDurationMs);

        if (lastPage > maxPages) {
            String errorMessage = String.format(
                    "Paging terminated: reached maximum page limit of %d (provider indicated %d total pages)",
                    maxPages, lastPage);
            log.warn(errorMessage);
            connectorMetricsService.recordPagingTimeout(CONNECTOR_NAME, serviceName);
            deliverError(metadata, requestEnvelope, errorMessage);
        }

        log.info("Paged query complete: delivered {} pages", pagesDelivered);
    }

    /**
     * Delivers an error message to the consumer's queue.
     */
    private void deliverError(ProxyRequestMetadata metadata,
            SifMessageEnvelope<?> requestEnvelope, String errorMessage) {
        try {
            responsesClient.routeErrorResponse(
                    metadata.getEnvironmentId(),
                    metadata.getEnvironmentName(),
                    metadata.getDomainId(),
                    metadata.getSolutionId(),
                    metadata.getConsumerQueueId(),
                    requestEnvelope.getRequestId(),
                    requestEnvelope.getServiceName(),
                    errorMessage);
        } catch (Exception e) {
            log.error("Failed to deliver error response for paged query: messageId={}, error={}",
                    metadata.getMessageId(), errorMessage, e);
        }
    }

    /**
     * Navigation header keys that are passed through from provider response to consumer envelope.
     */
    private static final List<String> NAVIGATION_HEADER_KEYS = List.of(
        "navigationPage", "navigationLastPage", "navigationPageSize",
        "navigationId", "navigationCount"
    );

    /**
     * Wraps a page response in a SifMessageEnvelope and routes it to the consumer queue.
     *
     * <p>Package-private for testability.</p>
     */
    void deliverPageResponse(ProxyRequestMetadata metadata,
            SifMessageEnvelope<?> requestEnvelope, ProviderRawResponse pageResponse,
            UUID messageId) {

        Map<String, String> navigationHeaders = new HashMap<>();
        Map<String, String> providerHeaders = pageResponse.getHeaders();
        if (providerHeaders != null) {
            for (String key : NAVIGATION_HEADER_KEYS) {
                String value = getHeaderCaseInsensitive(providerHeaders, key);
                if (value != null) {
                    navigationHeaders.put(key, value);
                }
            }
        }
        Map<String, String> reqHeaders = requestEnvelope.getHeaders();
        if (reqHeaders != null) {
            String zoneId = getHeaderCaseInsensitive(reqHeaders, "zoneId");
            String contextId = getHeaderCaseInsensitive(reqHeaders, "contextId");
            if (zoneId != null) navigationHeaders.put("zoneId", zoneId);
            if (contextId != null) navigationHeaders.put("contextId", contextId);
        }

        SifMessageEnvelope<Object> responseEnvelope = SifMessageEnvelope.<Object>builder()
            .messageId(messageId)
            .messageType(MessageType.RESPONSE)
            .serviceName(requestEnvelope.getServiceName())
            .serviceType(requestEnvelope.getServiceType())
            .payload(pageResponse.getBody())
            .headers(navigationHeaders)
            .timestamp(Instant.now())
            .build();

        responsesClient.routeResponse(
            metadata.getEnvironmentId(),
            metadata.getEnvironmentName(),
            metadata.getDomainId(),
            metadata.getSolutionId(),
            metadata.getConsumerQueueId(),
            requestEnvelope.getRequestId(),
            responseEnvelope,
            metadata.getRelativeServicePath());

        String pageNum = providerHeaders != null ? getHeaderCaseInsensitive(providerHeaders, "navigationPage") : "?";
        log.info("Delivered page response: page={}, messageId={}, consumerQueueId={}",
            pageNum, messageId, metadata.getConsumerQueueId());
    }

    /**
     * Case-insensitive header lookup from a Map.
     *
     * @param headers the header map to search
     * @param name the header name to find (case-insensitive)
     * @return the header value, or null if not found
     */
    static String getHeaderCaseInsensitive(Map<String, String> headers, String name) {
        if (headers == null || name == null) return null;
        String value = headers.get(name);
        if (value != null) return value;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
