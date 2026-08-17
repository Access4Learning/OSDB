// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.environment.config.BrokerInfrastructureConfig;
import org.sifunity.broker.environment.domain.Alert;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.AlertDTO;

import java.util.*;

/**
 * Publishes alert creation events to eligible subscriber queues using targeted delivery.
 *
 * <p>Unlike the normal events-connector broadcast (which delivers to ALL subscribers),
 * this publisher uses ownership-scoped delivery: non-admin subscribers only receive
 * alerts about their own messages. This ensures the outstanding counter matches
 * actual delivery targets, so ACK counting and storage cleanup work correctly.</p>
 *
 * <p>Delivery pattern:</p>
 * <ol>
 *   <li>Query subscriptions for {@code alerts} service from the subscription-service</li>
 *   <li>For each subscription, resolve the subscriber's environment via the queue's ownerId</li>
 *   <li>Filter: keep subscribers whose environment matches {@code sourceEnvironmentId}
 *       OR who are admin environments</li>
 *   <li>Set the outstanding counter to the eligible subscriber count (not total)</li>
 *   <li>Deliver the alert event directly to each eligible subscriber's queue</li>
 * </ol>
 *
 * <p>Error handling: catches all exceptions per subscriber, logs warning, continues
 * to next subscriber. Overall failure does not propagate (best-effort).</p>
 *
 * <p><strong>Requirements:</strong> 5.1, 5.2, 5.3, 5.4, 5.5, 5.6</p>
 *
 * @see AlertsService
 */
@Service
public class AlertEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertEventPublisher.class);

    private static final String ALERTS_SERVICE_NAME = "alerts";

    private final BrokerInfrastructureConfig infrastructureConfig;
    private final EnvironmentService environmentService;
    private final SolutionService solutionService;
    private final DomainService domainService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String internalServiceToken;

    public AlertEventPublisher(BrokerInfrastructureConfig infrastructureConfig,
                               EnvironmentService environmentService,
                               SolutionService solutionService,
                               DomainService domainService,
                               RestTemplate restTemplate,
                               ObjectMapper objectMapper,
                               @org.springframework.beans.factory.annotation.Value("${broker.internal-service-token:}") String internalServiceToken) {
        this.infrastructureConfig = infrastructureConfig;
        this.environmentService = environmentService;
        this.solutionService = solutionService;
        this.domainService = domainService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.internalServiceToken = internalServiceToken;
    }

    /**
     * Publishes an alert creation event to eligible subscribers.
     *
     * <p>Eligible subscribers are those whose environment matches the alert's
     * {@code sourceEnvironmentId} OR who are admin environments.</p>
     *
     * @param alert      the persisted alert entity
     * @param alertDTO   the alert DTO for the event payload
     * @param domainId   the domain ID from the request path
     * @param solutionId the solution ID from the request path
     */
    public void publishAlertCreatedEvent(Alert alert, AlertDTO alertDTO,
                                         String domainId, String solutionId) {
        log.debug("Publishing alert created event: alertId={}, domainId={}, solutionId={}",
                alert.getId(), domainId, solutionId);

        try {
            // 1. Query subscriptions for "alerts" service from subscription-service
            List<SubscriptionDTO> subscriptions = queryAlertSubscriptions(domainId, solutionId);

            if (subscriptions.isEmpty()) {
                log.info("No alert subscriptions found — skipping event publication: alertId={}",
                        alert.getId());
                return;
            }

            log.debug("Found {} total alert subscription(s)", subscriptions.size());

            // 2. Resolve sourceEnvironmentId from alert's extended elements
            String sourceEnvironmentId = resolveSourceEnvironmentId(alert);

            // 3. For each subscription, resolve subscriber environment and filter for eligibility
            List<EligibleSubscriber> eligibleSubscribers = new ArrayList<>();
            for (SubscriptionDTO subscription : subscriptions) {
                try {
                    UUID subscriberEnvironmentId = resolveSubscriberEnvironmentId(
                            subscription.queueId(), domainId, solutionId);

                    if (subscriberEnvironmentId == null) {
                        log.warn("Could not resolve environment for queue: queueId={}",
                                subscription.queueId());
                        continue;
                    }

                    boolean isEligible = isEligibleSubscriber(
                            subscriberEnvironmentId, sourceEnvironmentId);

                    if (isEligible) {
                        eligibleSubscribers.add(new EligibleSubscriber(
                                subscription.queueId(), subscriberEnvironmentId));
                    }
                } catch (Exception e) {
                    log.warn("Failed to evaluate eligibility for subscription: subscriptionId={}, error={}",
                            subscription.id(), e.getMessage());
                }
            }

            if (eligibleSubscribers.isEmpty()) {
                log.info("No eligible subscribers for alert event: alertId={}, totalSubscriptions={}",
                        alert.getId(), subscriptions.size());
                return;
            }

            log.info("Delivering alert event to {} eligible subscriber(s) out of {} total: alertId={}",
                    eligibleSubscribers.size(), subscriptions.size(), alert.getId());

            // 4. Build the event payload
            Map<String, Object> eventPayload = buildEventPayload(alertDTO);

            // 5. Deliver to each eligible subscriber's queue (Req 5.4: outstanding = eligible count)
            int deliveredCount = 0;
            for (EligibleSubscriber subscriber : eligibleSubscribers) {
                try {
                    deliverToQueue(subscriber.queueId(), eventPayload,
                            domainId, solutionId, alert.getId());
                    deliveredCount++;
                } catch (Exception e) {
                    log.warn("Failed to deliver alert event to queue: queueId={}, alertId={}, error={}",
                            subscriber.queueId(), alert.getId(), e.getMessage());
                }
            }

            log.info("Alert event publication complete: alertId={}, delivered={}/{} eligible",
                    alert.getId(), deliveredCount, eligibleSubscribers.size());

        } catch (Exception e) {
            log.warn("Alert event publication failed (best-effort): alertId={}, error={}",
                    alert.getId(), e.getMessage());
        }
    }

    /**
     * Queries subscriptions for the "alerts" service from the subscription-service.
     */
    private List<SubscriptionDTO> queryAlertSubscriptions(String domainId, String solutionId) {
        String url = infrastructureConfig.subscriptions()
                + "/" + domainId + "/" + solutionId
                + "/subscriptions/events?serviceName=" + ALERTS_SERVICE_NAME;

        log.debug("Querying alert subscriptions: url={}", url);

        try {
            ResponseEntity<SubscriptionQueryResponse> response = restTemplate.getForEntity(
                    url, SubscriptionQueryResponse.class);

            if (response.getBody() == null || response.getBody().isEmpty()) {
                return List.of();
            }

            return response.getBody().asList();
        } catch (Exception e) {
            log.warn("Failed to query alert subscriptions: url={}, error={}", url, e.getMessage());
            return List.of();
        }
    }

    /**
     * Resolves the subscriber's environment ID by looking up the queue's ownerId
     * from the queue-service.
     */
    private UUID resolveSubscriberEnvironmentId(UUID queueId, String domainId, String solutionId) {
        String url = infrastructureConfig.queues()
                + "/" + domainId + "/" + solutionId
                + "/queues/" + queueId;

        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("X-Environment-Fingerprint", "broker-services");
            headers.set("X-Internal-Service-Token", internalServiceToken);

            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, entity, JsonNode.class);

            if (response.getBody() != null) {
                // Handle wrapped response: {"queue": {"ownerId": "..."}}
                JsonNode queueNode = response.getBody().has("queue")
                        ? response.getBody().get("queue")
                        : response.getBody();
                if (queueNode.has("ownerId")) {
                    return UUID.fromString(queueNode.get("ownerId").asText());
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to resolve queue owner: queueId={}, error={}", queueId, e.getMessage());
            return null;
        }
    }

    /**
     * Determines if a subscriber is eligible to receive the alert event.
     *
     * <p>A subscriber is eligible if:</p>
     * <ul>
     *   <li>The alert has no {@code sourceEnvironmentId} (broadcast to all subscribers), OR</li>
     *   <li>Their environment matches the alert's {@code sourceEnvironmentId}, OR</li>
     *   <li>They are an admin environment (global-admin domain/solution)</li>
     * </ul>
     */
    private boolean isEligibleSubscriber(UUID subscriberEnvironmentId, String sourceEnvironmentId) {
        // If no sourceEnvironmentId, broadcast to all subscribers (per SIF spec:
        // all authorized subscribers can receive alert creation events)
        if (sourceEnvironmentId == null || sourceEnvironmentId.isBlank()) {
            return true;
        }

        // Check if subscriber's environment matches sourceEnvironmentId
        if (subscriberEnvironmentId.toString().equals(sourceEnvironmentId)) {
            return true;
        }

        // Check if subscriber is an admin environment
        return isAdminEnvironment(subscriberEnvironmentId);
    }

    /**
     * Checks if an environment belongs to the global-admin domain/solution.
     */
    private boolean isAdminEnvironment(UUID environmentId) {
        Environment env = environmentService.findByUuidCached(environmentId);
        if (env == null || env.getSolutionUuid() == null) {
            return false;
        }
        return solutionService.findByIdCached(env.getSolutionUuid())
                .map(solution -> {
                    String domainId = domainService.findByIdCached(solution.getDomainUuid())
                            .map(Domain::getDomainId)
                            .orElse(null);
                    return GlobalAdminConstants.isGlobalAdmin(domainId, solution.getSolutionId());
                })
                .orElse(false);
    }

    /**
     * Resolves the sourceEnvironmentId from the alert's extended elements.
     */
    private String resolveSourceEnvironmentId(Alert alert) {
        if (alert.getExtendedElements() == null) {
            return null;
        }
        return alert.getExtendedElements().stream()
                .filter(e -> "sourceEnvironmentId".equals(e.name()))
                .map(org.sifunity.broker.environment.dto.ExtendedElement::value)
                .findFirst()
                .orElse(null);
    }

    /**
     * Builds the event payload for the alert creation event.
     */
    private Map<String, Object> buildEventPayload(AlertDTO alertDTO) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alert", objectMapper.convertValue(alertDTO, Map.class));
        return payload;
    }

    /**
     * Delivers the alert event to a specific subscriber's queue via the queue-service.
     */
    private void deliverToQueue(UUID queueId, Map<String, Object> eventPayload,
                                String domainId, String solutionId, UUID alertId) {
        String url = infrastructureConfig.queues()
                + "/" + domainId + "/" + solutionId
                + "/queues/" + queueId + "/messages";

        Map<String, Object> enqueueRequest = new LinkedHashMap<>();
        enqueueRequest.put("messageId", UUID.randomUUID());
        enqueueRequest.put("zone", "environment-global");
        enqueueRequest.put("context", "DEFAULT");
        enqueueRequest.put("service", ALERTS_SERVICE_NAME);
        enqueueRequest.put("messageType", "event");
        enqueueRequest.put("serviceType", "UTILITY");
        enqueueRequest.put("payload", eventPayload);

        log.debug("Delivering alert event to queue: queueId={}, alertId={}", queueId, alertId);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Environment-Fingerprint", "broker-services");
        headers.set("X-Internal-Service-Token", internalServiceToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        org.springframework.http.HttpEntity<Map<String, Object>> entity =
                new org.springframework.http.HttpEntity<>(enqueueRequest, headers);

        restTemplate.postForEntity(url, entity, Void.class);
    }

    /**
     * Internal record for tracking eligible subscribers.
     */
    private record EligibleSubscriber(UUID queueId, UUID environmentId) {}

    /**
     * DTO for deserializing the SIF collection response from subscription-service.
     *
     * <p>Response format:</p>
     * <pre>{@code
     * {
     *   "subscriptions": {
     *     "subscription": [ {...}, {...} ]
     *   }
     * }
     * }</pre>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SubscriptionQueryResponse(
            @JsonProperty("subscriptions") SubscriptionsWrapper subscriptions
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record SubscriptionsWrapper(
                @JsonProperty("subscription") List<SubscriptionDTO> subscription
        ) {
            SubscriptionsWrapper {
                if (subscription == null) {
                    subscription = List.of();
                }
            }
        }

        SubscriptionQueryResponse {
            if (subscriptions == null) {
                subscriptions = new SubscriptionsWrapper(List.of());
            }
        }

        List<SubscriptionDTO> asList() {
            return subscriptions.subscription();
        }

        boolean isEmpty() {
            return subscriptions.subscription().isEmpty();
        }
    }
}
