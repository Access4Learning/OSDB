// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.requestsconnector.client.EnvironmentServiceClient;
import org.sifunity.requestsconnector.client.QueueServiceClient;
import org.sifunity.requestsconnector.client.SubscriptionServiceClient;
import org.sifunity.requestsconnector.service.LegacyTagLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * End-to-end integration test for the DELAYED queue-ownership gate.
 *
 * <p>Boots the real Spring context against a Testcontainers Redis, so the entire path the
 * vulnerability travelled is exercised for real: {@code RequestController} →
 * {@code RoutingService} → {@code ConsumerQueueOwnershipValidator} → {@code QueueOwnerCache}
 * (with {@code @Cacheable} genuinely proxied) → {@code DelayedRequestHandler}. Only the outbound
 * HTTP boundaries are mocked ({@code queue-service}, {@code environment-service},
 * {@code subscription-service}).</p>
 *
 * <p><strong>The two assertions that matter:</strong></p>
 * <ol>
 *   <li>Environment A naming environment B's queue is rejected with 404 and <em>nothing is
 *       enqueued</em> — no response can ever land in B's queue.</li>
 *   <li>Environment A naming its own queue still completes: 202, enqueued to the provider's
 *       queue, and the envelope carries A's queue as the response destination.</li>
 * </ol>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 2.1, 2.4, 3.1</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("DELAYED Queue Ownership E2E Integration Tests")
class DelayedQueueOwnershipE2EIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    private static final String DOMAIN_ID = "domain1";
    private static final String SOLUTION_ID = "solution1";
    private static final String SERVICE_NAME = "StudentPersonal";
    private static final String ZONE_ID = "test-zone";

    /** Environment A — the caller. */
    private static final UUID ENV_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    /** Environment B — an unrelated tenant whose queue must be unreachable. */
    private static final UUID ENV_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private static final UUID QUEUE_OF_A = UUID.fromString("00000000-000a-0000-0000-000000000001");
    private static final UUID QUEUE_OF_B = UUID.fromString("00000000-000b-0000-0000-000000000002");
    private static final UUID PROVIDER_QUEUE = UUID.fromString("00000000-00c0-0000-0000-000000000003");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private QueueServiceClient queueServiceClient;

    @MockBean
    private EnvironmentServiceClient environmentServiceClient;

    @MockBean
    private SubscriptionServiceClient subscriptionServiceClient;

    @MockBean
    private LegacyTagLookupService legacyTagLookupService;

    @BeforeEach
    void setUp() {
        clearCache("queue-owner");
        clearCache("provider-registry");
        reset(queueServiceClient, environmentServiceClient, subscriptionServiceClient, legacyTagLookupService);

        // A DELAYED-capable provider is registered for the service.
        ProviderRegistrationDTO provider = new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SERVICE_NAME,
                ZONE_ID,
                "DEFAULT",
                "ProviderApp",
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://provider.example.com/" + SERVICE_NAME),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of())))),
                Instant.now()
        );
        when(environmentServiceClient.lookupProvider(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of(provider));
        when(environmentServiceClient.resolveContextUuid(anyString(), anyString(), anyString(), any()))
                .thenReturn(UUID.randomUUID());

        // The provider has a DELAYED request subscription pointing at its own queue.
        when(subscriptionServiceClient.getDelayedRequestSubscription(anyString(), any(UUID.class)))
                .thenReturn(new SubscriptionDTO(
                        UUID.randomUUID(), PROVIDER_QUEUE, UUID.randomUUID(), SERVICE_NAME,
                        ZONE_ID, "DEFAULT", SubscriptionType.DELAYED_REQUEST, Instant.now()));

        when(legacyTagLookupService.isLegacyEnvironment(anyString(), anyString(), any()))
                .thenReturn(false);

        // Queue ownership: QUEUE_OF_A belongs to A, QUEUE_OF_B belongs to B.
        when(queueServiceClient.findQueueOwner(DOMAIN_ID, SOLUTION_ID, QUEUE_OF_A))
                .thenReturn(Optional.of(ENV_A));
        when(queueServiceClient.findQueueOwner(DOMAIN_ID, SOLUTION_ID, QUEUE_OF_B))
                .thenReturn(Optional.of(ENV_B));
        when(queueServiceClient.enqueueMessage(
                any(UUID.class), any(SifMessageEnvelope.class), anyString(), anyString()))
                .thenReturn(UUID.randomUUID());
    }

    private void clearCache(String name) {
        if (cacheManager.getCache(name) != null) {
            cacheManager.getCache(name).clear();
        }
    }

    private MvcResult delayedRequestNaming(UUID callerEnvId, UUID queueId) throws Exception {
        return mockMvc.perform(get("/{d}/{s}/requests/{obj}", DOMAIN_ID, SOLUTION_ID, SERVICE_NAME)
                        .header("X-Environment-Id", callerEnvId.toString())
                        .header("X-Environment-Name", "env-a")
                        .header("zoneId", ZONE_ID)
                        .header("contextId", "DEFAULT")
                        .header("requestType", "DELAYED")
                        .header("queueId", queueId.toString())
                        .header("serviceType", "OBJECT"))
                .andReturn();
    }

    private static String bodyOf(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * The originally reported scenario: A cannot land a response in B's queue.
     */
    @Test
    @DisplayName("Environment A naming environment B's queue is rejected with 404 and nothing is enqueued")
    void environmentACannotTargetEnvironmentBQueue() throws Exception {
        MvcResult result = delayedRequestNaming(ENV_A, QUEUE_OF_B);

        assertThat(result.getResponse().getStatus())
                .as("Naming another environment's queue must be rejected with 404")
                .isEqualTo(404);
        assertThat(bodyOf(result)).contains("QUEUE_NOT_FOUND");

        // The decisive assertion: nothing was enqueued anywhere.
        verify(queueServiceClient, never()).enqueueMessage(
                any(UUID.class), any(SifMessageEnvelope.class), anyString(), anyString());
    }

    /**
     * Requirement 2.4: the rejection must not disclose that B's queue exists, nor who owns it.
     */
    @Test
    @DisplayName("Rejection body discloses neither the queue owner nor queue existence")
    void rejectionDisclosesNothingAboutOwner() throws Exception {
        MvcResult result = delayedRequestNaming(ENV_A, QUEUE_OF_B);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(bodyOf(result))
                .as("404 must collapse 'absent' and 'not yours' — no owner or queue identifiers")
                .doesNotContain(ENV_B.toString())
                .doesNotContain(QUEUE_OF_B.toString());
    }

    /**
     * Requirement 3.1: the legitimate path is untouched — A naming its own queue still works,
     * and the envelope still carries A's queue as the response destination.
     */
    @Test
    @DisplayName("Environment A naming its OWN queue succeeds with 202 and enqueues to the provider")
    void environmentACanTargetItsOwnQueue() throws Exception {
        MvcResult result = delayedRequestNaming(ENV_A, QUEUE_OF_A);

        assertThat(result.getResponse().getStatus())
                .as("Owner-matched DELAYED request must still be accepted")
                .isEqualTo(202);
        assertThat(bodyOf(result)).contains("ACCEPTED");

        // Enqueued to the PROVIDER's queue...
        ArgumentCaptor<UUID> targetQueue = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<SifMessageEnvelope> envelope = ArgumentCaptor.forClass(SifMessageEnvelope.class);
        verify(queueServiceClient).enqueueMessage(
                targetQueue.capture(), envelope.capture(), eq(DOMAIN_ID), eq(SOLUTION_ID));

        assertThat(targetQueue.getValue())
                .as("DELAYED_to_DELAYED enqueues to the provider's subscription queue")
                .isEqualTo(PROVIDER_QUEUE);

        // ...carrying A's own queue as the response destination.
        assertThat(envelope.getValue().getQueueId())
                .as("The envelope must name A's queue as the response destination")
                .isEqualTo(QUEUE_OF_A);
    }

    /**
     * The gate is backed by a real, proxied cache: a repeated request for the same queue must not
     * issue a second owner lookup.
     */
    @Test
    @DisplayName("Repeated requests for the same queue issue only one owner lookup (cache active)")
    void repeatedRequestsHitTheCache() throws Exception {
        for (int i = 0; i < 2; i++) {
            MvcResult result = delayedRequestNaming(ENV_A, QUEUE_OF_A);
            assertThat(result.getResponse().getStatus()).isEqualTo(202);
        }

        verify(queueServiceClient, times(1)).findQueueOwner(DOMAIN_ID, SOLUTION_ID, QUEUE_OF_A);
    }

    /**
     * Fail-closed end to end, plus cache Rule 1: an unresolvable owner rejects and enqueues
     * nothing, and the empty result is NOT cached — so once the blip clears, the same queue is
     * admitted rather than being pinned into 404s for the whole TTL.
     */
    @Test
    @DisplayName("Unresolvable owner rejects, and the empty result is not cached")
    void unresolvableOwnerRejectsAndIsNotCached() throws Exception {
        UUID transientQueue = UUID.fromString("00000000-00dd-0000-0000-000000000004");

        // First: lookup fails (queue-service blip) → reject, nothing enqueued.
        when(queueServiceClient.findQueueOwner(DOMAIN_ID, SOLUTION_ID, transientQueue))
                .thenReturn(Optional.empty());

        MvcResult rejected = delayedRequestNaming(ENV_A, transientQueue);
        assertThat(rejected.getResponse().getStatus()).isEqualTo(404);
        verify(queueServiceClient, never()).enqueueMessage(
                any(UUID.class), any(SifMessageEnvelope.class), anyString(), anyString());

        // Then: the blip clears and the queue resolves to A → must be admitted, proving the
        // empty result was never cached.
        when(queueServiceClient.findQueueOwner(DOMAIN_ID, SOLUTION_ID, transientQueue))
                .thenReturn(Optional.of(ENV_A));

        MvcResult admitted = delayedRequestNaming(ENV_A, transientQueue);
        assertThat(admitted.getResponse().getStatus())
                .as("An empty lookup must not be cached — a momentary outage must not become sticky")
                .isEqualTo(202);
    }
}
