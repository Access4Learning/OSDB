// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.testprovider.config.ConfigSnapshot;
import org.sifunity.testprovider.config.ProviderConfig;
import org.sifunity.testprovider.dto.PollResult;
import org.sifunity.testprovider.util.HmacAuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Objects;

/**
 * On-demand runner for the DELAYED choreography. Polls the broker queue for DELAYED requests and
 * submits responses, running <strong>exactly one cycle per explicit trigger</strong>.
 *
 * <p>This bean is always present (no {@code @ConditionalOnProperty}) and owns no {@code TaskScheduler}
 * or {@code @Scheduled} task. Each cycle is gated at runtime by whether a complete credential set and a
 * non-blank {@code queueId} are configured, and is invoked by {@code TestControlController} in response
 * to {@code POST /test-control/poll}.</p>
 *
 * <p>Flow per cycle (SIF ACK-and-GET-NEXT preserved):</p>
 * <ol>
 *   <li>GET next message from queue (with deleteMessageId to ACK previous message)</li>
 *   <li>Extract request details from headers</li>
 *   <li>POST response via Responses Connector</li>
 *   <li>Store messageId for ACK on next poll (SIF ACK-and-GET-NEXT pattern)</li>
 * </ol>
 */
@Component
@Slf4j
public class DelayedPollRunner implements PollRunnerNotifier {

    private final ProviderConfig config;
    private final TestDataGenerator dataGenerator;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * Stores the messageId of the last successfully processed message.
     * Passed as {@code deleteMessageId} matrix param on the next poll to ACK it
     * per the SIF ACK-and-GET-NEXT pattern.
     */
    private volatile String pendingAckMessageId = null;

    @Autowired
    public DelayedPollRunner(ProviderConfig config,
                             TestDataGenerator dataGenerator,
                             ObjectMapper objectMapper) {
        this(config, dataGenerator, objectMapper, RestClient.create());
    }

    /**
     * Test seam: construct the runner with an injected {@link RestClient} so unit tests can supply a
     * client backed by {@code MockRestServiceServer}. Production code uses the three-argument
     * constructor, which creates a default {@code RestClient.create()} — behavior is identical.
     *
     * @param config        live provider config
     * @param dataGenerator test data generator
     * @param objectMapper  Jackson object mapper
     * @param restClient    the HTTP client to use for broker calls
     */
    public DelayedPollRunner(ProviderConfig config,
                             TestDataGenerator dataGenerator,
                             ObjectMapper objectMapper,
                             RestClient restClient) {
        this.config = config;
        this.dataGenerator = dataGenerator;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        log.info("DelayedPollRunner initialized — on-demand polling (no background timer)");
    }

    /**
     * Run exactly one poll cycle on demand. If a complete credential set is not present or
     * {@code queueId} is blank, performs no queue interaction and reports
     * {@code { polled: false, messageProcessed: false }}. Otherwise runs the poll/submit/ACK logic
     * once and reports the outcome. Transport/auth errors are contained (logged) and reported as
     * {@code { polled: true, messageProcessed: false }} — they are never propagated to the caller.
     *
     * @return the outcome of the cycle
     */
    public PollResult triggerOnce() {
        String queueId = config.getQueueId();
        if (!config.hasCompleteCredentials() || !StringUtils.hasText(queueId)) {
            log.debug("Delayed polling disabled (incomplete credentials or blank queueId) — skipping");
            return new PollResult(false, false);
        }
        return pollOnce();
    }

    /**
     * Reset any pending ACK when the {@code queueId} changes, so a stale ACK from the old queue is
     * never replayed against a new queue.
     *
     * @param before snapshot captured before the configure call
     * @param after  snapshot captured after the configure call
     */
    @Override
    public void onConfigChanged(ConfigSnapshot before, ConfigSnapshot after) {
        if (!Objects.equals(before.queueId(), after.queueId())) {
            pendingAckMessageId = null;
            log.info("Queue changed ({} -> {}) — reset pending ACK", before.queueId(), after.queueId());
        }
    }

    /**
     * Poll the queue for the next pending message exactly once.
     * Preserves the SIF ACK-and-GET-NEXT semantics: the previously processed messageId is passed as a
     * {@code deleteMessageId} matrix param to ACK it on this poll.
     */
    private PollResult pollOnce() {
        // SIF ACK-and-GET-NEXT: pass deleteMessageId as matrix param to ACK previous message
        String queueUrl;
        if (pendingAckMessageId != null) {
            queueUrl = String.format("%s/%s/%s/queues/%s/messages;deleteMessageId=%s",
                    config.getGatewayUrl(),
                    config.getDomainId(),
                    config.getSolutionId(),
                    config.getQueueId(),
                    pendingAckMessageId);
        } else {
            queueUrl = String.format("%s/%s/%s/queues/%s/messages",
                    config.getGatewayUrl(),
                    config.getDomainId(),
                    config.getSolutionId(),
                    config.getQueueId());
        }

        log.info("▶ Polling: GET {}", queueUrl);

        try {
            String[] auth = HmacAuthUtil.generateAuthHeaders(
                    config.getSessionToken(), config.getSharedSecret());

            var response = restClient.get()
                    .uri(queueUrl)
                    .header(HttpHeaders.AUTHORIZATION, auth[0])
                    .header("timestamp", auth[1])
                    .header("connectionId", "delayedPoller")
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .toEntity(String.class);

            // If we got here, the server accepted the request (including any ACK via deleteMessageId)
            if (pendingAckMessageId != null) {
                log.info("✓ ACK'd message {} via deleteMessageId", pendingAckMessageId);
                pendingAckMessageId = null;
            }

            if (response.getStatusCode().value() == 204 || response.getBody() == null || response.getBody().isBlank()) {
                log.info("◻ Queue empty (status={})", response.getStatusCode().value());
                return new PollResult(true, false);
            }

            String responseBody = response.getBody();
            log.info("✓ Got message from queue! Status={}, Body length={}", response.getStatusCode().value(), responseBody.length());
            log.info("  Response headers: {}", response.getHeaders().toSingleValueMap());
            log.info("  Raw body: {}", responseBody.substring(0, Math.min(responseBody.length(), 500)));

            // Extract SIF headers from the HTTP response
            String messageId = response.getHeaders().getFirst("messageid");
            String zoneId = response.getHeaders().getFirst("zoneId");
            if (zoneId == null) zoneId = response.getHeaders().getFirst("zoneid");
            String contextId = response.getHeaders().getFirst("contextId");
            if (contextId == null) contextId = response.getHeaders().getFirst("contextid");
            String serviceName = response.getHeaders().getFirst("serviceName");
            if (serviceName == null) serviceName = response.getHeaders().getFirst("servicename");
            String queueIdHeader = response.getHeaders().getFirst("queueId");
            if (queueIdHeader == null) queueIdHeader = response.getHeaders().getFirst("queueid");
            String requestId = response.getHeaders().getFirst("requestId");
            if (requestId == null) requestId = response.getHeaders().getFirst("requestid");
            String relativeServicePath = response.getHeaders().getFirst("relativeServicePath");
            if (relativeServicePath == null) relativeServicePath = response.getHeaders().getFirst("relativeservicepath");

            log.info("  SIF headers: messageId={}, zoneId={}, contextId={}, serviceName={}, queueId={}, requestId={}, relativeServicePath={}",
                    messageId, zoneId, contextId, serviceName, queueIdHeader, requestId, relativeServicePath);

            // If no relativeServicePath from broker, build one from serviceName
            if (relativeServicePath == null && serviceName != null) {
                relativeServicePath = serviceName;
            }

            boolean processed = processMessage(responseBody, messageId, zoneId, contextId, serviceName, queueIdHeader, requestId, relativeServicePath);
            return new PollResult(true, processed, requestId);

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("204") || msg.contains("404") || msg.contains("No Content")) {
                log.info("◻ Queue empty ({})", msg.substring(0, Math.min(msg.length(), 120)));
            } else {
                log.error("✗ Error polling queue: {}", msg, e);
            }
            // Contain transport/auth errors — never propagate to the controller
            return new PollResult(true, false);
        }
    }

    private boolean processMessage(String messageJson, String messageId, String zoneId,
                                   String contextId, String serviceName, String consumerQueueId,
                                   String requestId, String relativeServicePath) {
        try {
            log.info("📋 Processing message: messageId={}, serviceName={}, zoneId={}, contextId={}, consumerQueueId={}, requestId={}",
                    messageId, serviceName, zoneId, contextId, consumerQueueId, requestId);

            // If serviceName not in headers, try to extract from body
            if (serviceName == null || serviceName.isBlank()) {
                JsonNode message = objectMapper.readTree(messageJson);
                serviceName = extractField(message, "serviceName", "objectName", "service");
                log.info("  Extracted serviceName from body: {}", serviceName);
            }

            // If consumerQueueId not in headers, try to extract from body
            if (consumerQueueId == null || consumerQueueId.isBlank()) {
                JsonNode message = objectMapper.readTree(messageJson);
                consumerQueueId = extractField(message, "queueId");
                log.info("  Extracted consumerQueueId from body: {}", consumerQueueId);
            }

            // If requestId not in headers, try to extract from body
            if (requestId == null || requestId.isBlank()) {
                JsonNode message = objectMapper.readTree(messageJson);
                requestId = extractField(message, "requestId");
                log.info("  Extracted requestId from body: {}", requestId);
            }

            // Generate test data response
            Map<String, Object> testData = dataGenerator.generateSingle(java.util.Collections.emptyMap(), null);
            log.info("📋 Generated test data: {}", testData);

            // Submit response to broker
            boolean submitted = submitResponse(serviceName, consumerQueueId, requestId, zoneId, contextId, relativeServicePath, testData);

            // Store messageId for ACK on next poll (SIF ACK-and-GET-NEXT pattern)
            if (messageId != null) {
                pendingAckMessageId = messageId;
                log.info("✓ Will ACK messageId={} on next poll", messageId);
            } else {
                log.warn("⚠ No messageId found — cannot acknowledge message");
            }

            return submitted;

        } catch (Exception e) {
            log.error("✗ Failed to process delayed message: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean submitResponse(String serviceName, String queueId, String requestId,
                                   String zoneId, String contextId, String relativeServicePath,
                                   Map<String, Object> payload) {
        // SIF 3.5+ spec: POST to /{domainId}/{solutionId}/responses (no service name in path)
        // Service metadata goes in HTTP headers instead
        String responseUrl = String.format("%s/%s/%s/responses",
                config.getGatewayUrl(),
                config.getDomainId(),
                config.getSolutionId());

        // Echo the relativeServicePath from the original request, or build from serviceName
        String echoRelativeServicePath = relativeServicePath != null ? relativeServicePath :
                (serviceName != null ? serviceName : "TESTOBJECT");

        log.info("▶ Submitting response: POST {}", responseUrl);
        log.info("  Headers: relativeServicePath={}, queueId={}, requestId={}, zoneId={}, contextId={}",
                echoRelativeServicePath, queueId, requestId, zoneId, contextId);

        String[] auth = HmacAuthUtil.generateAuthHeaders(
                config.getSessionToken(), config.getSharedSecret());

        try {
            String body = objectMapper.writeValueAsString(payload);
            log.info("  Body: {}", body.substring(0, Math.min(body.length(), 300)));

            RestClient.RequestBodySpec request = restClient.post()
                    .uri(responseUrl)
                    .header(HttpHeaders.AUTHORIZATION, auth[0])
                    .header("timestamp", auth[1])
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header("relativeServicePath", echoRelativeServicePath)
                    .header("serviceType", "OBJECT")
                    .header("messageType", "RESPONSE")
                    .header("responseAction", "QUERY");

            if (queueId != null) {
                request = request.header("queueId", queueId);
            }
            if (requestId != null) {
                request = request.header("requestId", requestId);
            }
            if (zoneId != null) {
                request = request.header("zoneId", zoneId);
            }
            if (contextId != null) {
                request = request.header("contextId", contextId);
            }

            request.body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("✓ Response submitted successfully for service={}, queueId={}, requestId={}",
                    serviceName, queueId, requestId);
            return true;

        } catch (Exception e) {
            log.error("✗ Failed to submit response: url={}, error={}", responseUrl, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Try multiple field names to extract a value from a JSON node.
     */
    private String extractField(JsonNode node, String... fieldNames) {
        for (String name : fieldNames) {
            JsonNode field = node.get(name);
            if (field != null && !field.isNull()) {
                return field.asText();
            }
        }
        return null;
    }
}
