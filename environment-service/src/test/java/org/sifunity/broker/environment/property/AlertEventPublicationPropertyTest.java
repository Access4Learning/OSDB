// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.Exchange;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.environment.config.BrokerInfrastructureConfig;
import org.sifunity.broker.environment.domain.Alert;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.AlertDTO;
import org.sifunity.broker.environment.dto.ExtendedElement;
import org.sifunity.broker.environment.service.AlertEventPublisher;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Property 12: Targeted event delivery.
 *
 * <p><strong>Property:</strong> For any successfully created alert with sourceEnvironmentId S,
 * and for any set of alert subscribers containing both admin and non-admin environments,
 * the AlertEventPublisher SHALL deliver the event only to subscribers whose environment
 * matches S or who are admin environments. The outstanding message counter SHALL equal
 * the number of eligible subscribers, not the total subscriber count.</p>
 *
 * <p>This is a unit-level test that mocks RestTemplate, EnvironmentService,
 * SolutionService, and DomainService to isolate the eligibility filtering
 * logic in AlertEventPublisher.</p>
 *
 * <p><strong>Validates: Requirements 5.3, 5.4, 5.5</strong></p>
 */
class AlertEventPublicationPropertyTest {

    private RestTemplate restTemplate;
    private EnvironmentService environmentService;
    private SolutionService solutionService;
    private DomainService domainService;
    private BrokerInfrastructureConfig infrastructureConfig;
    private ObjectMapper objectMapper;
    private AlertEventPublisher publisher;

    private final Random random = new Random();

    private static final String DOMAIN_ID = "test-domain";
    private static final String SOLUTION_ID = "test-solution";
    private static final String SUBSCRIPTIONS_BASE = "http://subscriptions:8086";
    private static final String QUEUES_BASE = "http://queues:8085";

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        environmentService = mock(EnvironmentService.class);
        solutionService = mock(SolutionService.class);
        domainService = mock(DomainService.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        infrastructureConfig = new BrokerInfrastructureConfig(
                "http://gateway:8080",
                "http://environment:8081",
                "http://provision:8081",
                "http://requests:8083",
                "http://events:8084",
                QUEUES_BASE,
                SUBSCRIPTIONS_BASE,
                "http://services:8087",
                "http://responses:8088"
        );

        publisher = new AlertEventPublisher(
                infrastructureConfig,
                environmentService,
                solutionService,
                domainService,
                restTemplate,
                objectMapper,
                "test-internal-token"
        );
    }

    /**
     * Property 12: Targeted event delivery — only eligible subscribers receive events.
     *
     * <p>Generates a random mix of subscribers: some matching sourceEnvironmentId,
     * some admin environments, some neither. Verifies that deliverToQueue is called
     * exactly for eligible subscribers (matching source OR admin) and not for
     * ineligible ones.</p>
     *
     * <p><strong>Validates: Requirements 5.3, 5.4, 5.5</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: alerts-service, Property 12: Targeted event delivery")
    void onlyEligibleSubscribersReceiveAlertEvents() {
        // Generate random sourceEnvironmentId
        UUID sourceEnvId = UUID.randomUUID();

        // Generate random subscriber sets
        int matchingCount = 1 + random.nextInt(3);   // 1-3 matching source env
        int adminCount = 1 + random.nextInt(3);       // 1-3 admin environments
        int ineligibleCount = 1 + random.nextInt(4);  // 1-4 ineligible environments

        List<SubscriberInfo> allSubscribers = new ArrayList<>();

        // Create matching-source subscribers (environment matches sourceEnvId)
        for (int i = 0; i < matchingCount; i++) {
            allSubscribers.add(new SubscriberInfo(
                    UUID.randomUUID(), UUID.randomUUID(), sourceEnvId, false, true));
        }

        // Create admin subscribers (different environment from source)
        for (int i = 0; i < adminCount; i++) {
            UUID adminEnvId = UUID.randomUUID();
            allSubscribers.add(new SubscriberInfo(
                    UUID.randomUUID(), UUID.randomUUID(), adminEnvId, true, true));
        }

        // Create ineligible subscribers (non-admin, non-matching)
        for (int i = 0; i < ineligibleCount; i++) {
            UUID otherEnvId = UUID.randomUUID();
            allSubscribers.add(new SubscriberInfo(
                    UUID.randomUUID(), UUID.randomUUID(), otherEnvId, false, false));
        }

        // Shuffle to randomize processing order
        Collections.shuffle(allSubscribers, random);

        // Mock subscription query — return all subscribers
        mockSubscriptionQueryResponse(allSubscribers);

        // Mock queue owner resolution for each subscriber
        for (SubscriberInfo sub : allSubscribers) {
            mockQueueOwnerResponse(sub.queueId, sub.environmentId);
        }

        // Mock admin detection for each subscriber's environment
        for (SubscriberInfo sub : allSubscribers) {
            mockAdminDetection(sub.environmentId, sub.isAdmin);
        }

        // Mock delivery calls (accept all)
        when(restTemplate.postForEntity(contains("/messages"), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok(null));

        // Build alert with sourceEnvironmentId in extended elements
        Alert alert = buildAlertWithSourceEnvId(sourceEnvId);
        AlertDTO alertDTO = buildAlertDTO();

        // Execute
        publisher.publishAlertCreatedEvent(alert, alertDTO, DOMAIN_ID, SOLUTION_ID);

        // Verify: capture all delivery URLs
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        int expectedEligible = matchingCount + adminCount;

        verify(restTemplate, times(expectedEligible))
                .postForEntity(urlCaptor.capture(), any(HttpEntity.class), eq(Void.class));

        // Verify delivered queue IDs match eligible subscribers
        Set<UUID> eligibleQueueIds = allSubscribers.stream()
                .filter(s -> s.eligible)
                .map(s -> s.queueId)
                .collect(Collectors.toSet());

        Set<UUID> deliveredQueueIds = urlCaptor.getAllValues().stream()
                .map(this::extractQueueIdFromUrl)
                .collect(Collectors.toSet());

        assertThat(deliveredQueueIds)
                .as("Delivered queue IDs should match eligible subscribers exactly")
                .containsExactlyInAnyOrderElementsOf(eligibleQueueIds);

        // Verify ineligible subscribers did NOT receive delivery
        Set<UUID> ineligibleQueueIds = allSubscribers.stream()
                .filter(s -> !s.eligible)
                .map(s -> s.queueId)
                .collect(Collectors.toSet());

        assertThat(deliveredQueueIds)
                .as("Ineligible subscribers should not receive events")
                .doesNotContainAnyElementsOf(ineligibleQueueIds);

        // Verify outstanding counter equals eligible count (Req 5.4)
        assertThat(deliveredQueueIds.size())
                .as("Outstanding counter (delivery count) should equal eligible subscriber count")
                .isEqualTo(expectedEligible);
    }

    /**
     * Property 12: Targeted event delivery — delivery count matches eligible count
     * even when all subscribers are eligible.
     *
     * <p>Edge case: all subscribers are either matching source or admin.
     * Verifies delivery count equals total subscriber count.</p>
     *
     * <p><strong>Validates: Requirements 5.3, 5.4, 5.5</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: alerts-service, Property 12: Targeted event delivery")
    void deliveryCountMatchesEligibleCountWhenAllEligible() {
        UUID sourceEnvId = UUID.randomUUID();

        int matchingCount = 1 + random.nextInt(3);
        int adminCount = 1 + random.nextInt(3);

        List<SubscriberInfo> allSubscribers = new ArrayList<>();

        for (int i = 0; i < matchingCount; i++) {
            allSubscribers.add(new SubscriberInfo(
                    UUID.randomUUID(), UUID.randomUUID(), sourceEnvId, false, true));
        }

        for (int i = 0; i < adminCount; i++) {
            UUID adminEnvId = UUID.randomUUID();
            allSubscribers.add(new SubscriberInfo(
                    UUID.randomUUID(), UUID.randomUUID(), adminEnvId, true, true));
        }

        Collections.shuffle(allSubscribers, random);

        mockSubscriptionQueryResponse(allSubscribers);

        for (SubscriberInfo sub : allSubscribers) {
            mockQueueOwnerResponse(sub.queueId, sub.environmentId);
        }

        for (SubscriberInfo sub : allSubscribers) {
            mockAdminDetection(sub.environmentId, sub.isAdmin);
        }

        when(restTemplate.postForEntity(contains("/messages"), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok(null));

        Alert alert = buildAlertWithSourceEnvId(sourceEnvId);
        AlertDTO alertDTO = buildAlertDTO();

        publisher.publishAlertCreatedEvent(alert, alertDTO, DOMAIN_ID, SOLUTION_ID);

        int expectedTotal = matchingCount + adminCount;
        verify(restTemplate, times(expectedTotal))
                .postForEntity(contains("/messages"), any(HttpEntity.class), eq(Void.class));
    }

    /**
     * Unit test: isAdminEnvironment returns false when environment not found.
     *
     * <p>When {@code environmentService.findByUuidCached()} returns null for a subscriber's
     * environment, that subscriber should NOT be considered admin and should be ineligible
     * (assuming it doesn't match the sourceEnvironmentId).</p>
     *
     * <p><strong>Validates: Requirements 2.4, 2.6</strong></p>
     */
    @RepeatedTest(1)
    @Tag("Feature: n-plus-one-and-cache-fixes, isAdminEnvironment returns false when environment not found")
    void isAdminEnvironmentReturnsFalseWhenEnvironmentNotFound() {
        UUID sourceEnvId = UUID.randomUUID();

        // One matching subscriber (eligible because it matches sourceEnvId)
        UUID matchingQueueId = UUID.randomUUID();
        UUID matchingSubId = UUID.randomUUID();

        // One subscriber whose environment cannot be found (should be ineligible)
        UUID notFoundQueueId = UUID.randomUUID();
        UUID notFoundSubId = UUID.randomUUID();
        UUID notFoundEnvId = UUID.randomUUID();

        List<SubscriberInfo> allSubscribers = List.of(
                new SubscriberInfo(matchingSubId, matchingQueueId, sourceEnvId, false, true),
                new SubscriberInfo(notFoundSubId, notFoundQueueId, notFoundEnvId, false, false)
        );

        mockSubscriptionQueryResponse(allSubscribers);

        // Mock queue owner resolution
        mockQueueOwnerResponse(matchingQueueId, sourceEnvId);
        mockQueueOwnerResponse(notFoundQueueId, notFoundEnvId);

        // Matching subscriber: mock environment found (non-admin, but matches source)
        Environment matchingEnv = new Environment();
        matchingEnv.setId(sourceEnvId);
        matchingEnv.setSolutionUuid(UUID.randomUUID());
        when(environmentService.findByUuidCached(sourceEnvId)).thenReturn(matchingEnv);

        // Not-found subscriber: findByUuidCached returns null (environment doesn't exist)
        when(environmentService.findByUuidCached(notFoundEnvId)).thenReturn(null);

        when(restTemplate.postForEntity(contains("/messages"), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok(null));

        Alert alert = buildAlertWithSourceEnvId(sourceEnvId);
        AlertDTO alertDTO = buildAlertDTO();

        publisher.publishAlertCreatedEvent(alert, alertDTO, DOMAIN_ID, SOLUTION_ID);

        // Only the matching subscriber should receive the event (1 delivery)
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(1))
                .postForEntity(urlCaptor.capture(), any(HttpEntity.class), eq(Void.class));

        UUID deliveredQueueId = extractQueueIdFromUrl(urlCaptor.getValue());
        assertThat(deliveredQueueId)
                .as("Only the source-matching subscriber should receive event; " +
                        "not-found environment subscriber should be ineligible (isAdminEnvironment=false)")
                .isEqualTo(matchingQueueId);

        // Verify findByUuidCached was called for the not-found environment
        verify(environmentService).findByUuidCached(notFoundEnvId);
    }

    // ========== Helper Methods ==========

    @SuppressWarnings("unchecked")
    private void mockSubscriptionQueryResponse(List<SubscriberInfo> subscribers) {
        String subscriptionsUrl = SUBSCRIPTIONS_BASE + "/" + DOMAIN_ID + "/" + SOLUTION_ID
                + "/subscriptions/events?serviceName=alerts";

        // Build the nested JSON structure that matches SubscriptionQueryResponse format
        List<Map<String, Object>> subList = subscribers.stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.subscriptionId.toString());
                    m.put("queueId", s.queueId.toString());
                    m.put("serviceName", "alerts");
                    m.put("serviceType", "UTILITY");
                    m.put("zoneId", "environment-global");
                    m.put("contextId", "DEFAULT");
                    m.put("messageType", "EVENT");
                    m.put("createdAt", Instant.now().toString());
                    return m;
                })
                .toList();

        Map<String, Object> subscriptionsObj = new LinkedHashMap<>();
        subscriptionsObj.put("subscription", subList);

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("subscriptions", subscriptionsObj);

        // The RestTemplate mock needs to return the deserialized response type.
        // Since SubscriptionQueryResponse is package-private in AlertEventPublisher,
        // we use argThat to match any Class parameter and convert via ObjectMapper.
        when(restTemplate.getForEntity(eq(subscriptionsUrl), any(Class.class)))
                .thenAnswer(invocation -> {
                    Class<?> responseType = invocation.getArgument(1);
                    Object converted = objectMapper.convertValue(wrapper, responseType);
                    return ResponseEntity.ok(converted);
                });
    }

    private void mockQueueOwnerResponse(UUID queueId, UUID ownerId) {
        String queueUrl = QUEUES_BASE + "/" + DOMAIN_ID + "/" + SOLUTION_ID
                + "/queues/" + queueId;

        ObjectNode queueNode = objectMapper.createObjectNode();
        queueNode.put("ownerId", ownerId.toString());

        when(restTemplate.exchange(eq(queueUrl), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(queueNode));
    }

    private void mockAdminDetection(UUID environmentId, boolean isAdmin) {
        if (isAdmin) {
            // Admin: environment -> solution (GLOBAL_SOLUTION) -> domain (GLOBAL_DOMAIN)
            UUID solutionUuid = UUID.randomUUID();
            UUID domainUuid = UUID.randomUUID();

            Environment env = new Environment();
            env.setId(environmentId);
            env.setSolutionUuid(solutionUuid);

            Solution solution = new Solution();
            solution.setId(solutionUuid);
            solution.setDomainUuid(domainUuid);
            solution.setSolutionId(GlobalAdminConstants.GLOBAL_SOLUTION);

            Domain domain = new Domain();
            domain.setId(domainUuid);
            domain.setDomainId(GlobalAdminConstants.GLOBAL_DOMAIN);

            when(environmentService.findByUuidCached(environmentId)).thenReturn(env);
            when(solutionService.findByIdCached(solutionUuid)).thenReturn(Optional.of(solution));
            when(domainService.findByIdCached(domainUuid)).thenReturn(Optional.of(domain));
        } else {
            // Non-admin: environment -> solution (regular) -> domain (regular)
            UUID solutionUuid = UUID.randomUUID();
            UUID domainUuid = UUID.randomUUID();

            Environment env = new Environment();
            env.setId(environmentId);
            env.setSolutionUuid(solutionUuid);

            Solution solution = new Solution();
            solution.setId(solutionUuid);
            solution.setDomainUuid(domainUuid);
            solution.setSolutionId("regular-solution");

            Domain domain = new Domain();
            domain.setId(domainUuid);
            domain.setDomainId("regular-domain");

            when(environmentService.findByUuidCached(environmentId)).thenReturn(env);
            when(solutionService.findByIdCached(solutionUuid)).thenReturn(Optional.of(solution));
            when(domainService.findByIdCached(domainUuid)).thenReturn(Optional.of(domain));
        }
    }

    private Alert buildAlertWithSourceEnvId(UUID sourceEnvId) {
        Alert alert = new Alert();
        alert.setId(UUID.randomUUID());
        alert.setReporter("TestReporter-" + random.nextInt(10000));
        alert.setExchange(Exchange.values()[random.nextInt(Exchange.values().length)]);
        alert.setLevel(AlertLevel.values()[random.nextInt(AlertLevel.values().length)]);
        alert.setCreatedAt(Instant.now());
        alert.setExtendedElements(List.of(
                new ExtendedElement("sourceEnvironmentId", sourceEnvId.toString()),
                new ExtendedElement("originatingZoneId", "environment-global"),
                new ExtendedElement("originatingContextId", "DEFAULT"),
                new ExtendedElement("createdAt", Instant.now().toString())
        ));
        return alert;
    }

    private AlertDTO buildAlertDTO() {
        return new AlertDTO(
                UUID.randomUUID(),
                "TestReporter",
                null,
                Exchange.REQUEST,
                AlertLevel.ERROR,
                "Test alert description",
                null, null, null, null,
                null, null, null, null
        );
    }

    private UUID extractQueueIdFromUrl(String url) {
        // URL format: .../queues/{queueId}/messages
        String[] parts = url.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("queues".equals(parts[i])) {
                return UUID.fromString(parts[i + 1]);
            }
        }
        throw new IllegalArgumentException("Could not extract queueId from URL: " + url);
    }

    /**
     * Internal record tracking subscriber test data.
     */
    private record SubscriberInfo(
            UUID subscriptionId,
            UUID queueId,
            UUID environmentId,
            boolean isAdmin,
            boolean eligible
    ) {}
}
