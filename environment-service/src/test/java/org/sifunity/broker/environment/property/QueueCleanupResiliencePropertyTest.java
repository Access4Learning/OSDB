// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.ArgumentCaptor;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.QueueServiceClient;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based test for best-effort resilience.
 *
 * <p><strong>Property 4: Best-effort resilience</strong></p>
 *
 * <p>For any combination of queue delete outcomes (202, 404, 5xx, connection timeout,
 * connection refused), the QueueServiceClient SHALL attempt deletion of ALL owned queues
 * (never short-circuit on failure), and the EnvironmentService SHALL always complete
 * environment deletion (cache eviction, secret deletion, row deletion).</p>
 *
 * <p><strong>Validates: Requirements 2.4, 3.1, 3.2, 5.2, 5.3</strong></p>
 */
class QueueCleanupResiliencePropertyTest {

    private static final String BASE_URL = "http://queue-service:8080";

    // QueueServiceClient-level mocks
    private RestTemplate restTemplate;
    private QueueServiceClient queueServiceClient;

    // EnvironmentService-level mocks
    private EnvironmentRepository environmentRepository;
    private SolutionRepository solutionRepository;
    private DomainRepository domainRepository;
    private SecretsBackend secretsBackend;
    private CacheManager cacheManager;
    private Cache authEnvCache;
    private Cache authSecretCache;
    private EnvironmentService environmentService;

    @BeforeProperty
    void setUp() {
        // Set up QueueServiceClient with mocked RestTemplate
        restTemplate = mock(RestTemplate.class);

        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        when(restTemplateBuilder.setConnectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        ObjectMapper objectMapper = new ObjectMapper();

        queueServiceClient = new QueueServiceClient(
                BASE_URL,
                restTemplateBuilder,
                5000,
                10000,
                objectMapper);

        // Set up EnvironmentService dependencies
        environmentRepository = mock(EnvironmentRepository.class);
        solutionRepository = mock(SolutionRepository.class);
        domainRepository = mock(DomainRepository.class);
        secretsBackend = mock(SecretsBackend.class);
        cacheManager = mock(CacheManager.class);
        authEnvCache = mock(Cache.class);
        authSecretCache = mock(Cache.class);

        when(cacheManager.getCache("auth-environment")).thenReturn(authEnvCache);
        when(cacheManager.getCache("auth-shared-secret")).thenReturn(authSecretCache);

        environmentService = new EnvironmentService(
                environmentRepository,
                solutionRepository,
                domainRepository,
                mock(org.sifunity.broker.environment.repository.AclEntryRepository.class),
                secretsBackend,
                mock(org.sifunity.broker.environment.service.EnvironmentResponseMapper.class),
                cacheManager,
                queueServiceClient,
                mock(ZoneRepository.class));
    }

    /**
     * Property 4a: QueueServiceClient attempts all queues regardless of individual failures.
     *
     * <p><strong>Validates: Requirements 2.4, 3.1, 3.2</strong></p>
     *
     * <p>Generates random failure patterns per queue (202, 404, 5xx, connection timeout,
     * connection refused). Verifies all owned queues are attempted (never short-circuits
     * on failure).</p>
     */
    @Property(tries = 20)
    @Tag("Feature: environment-delete-queue-cleanup, Property 4: Best-effort resilience")
    void allQueuesAttemptedRegardlessOfFailures(
            @ForAll("clientScenarios") ClientScenario scenario) {

        // Reset mock to isolate each try
        reset(restTemplate);

        UUID environmentId = scenario.environmentId();
        String domainId = scenario.domainId();
        String solutionId = scenario.solutionId();
        List<QueueWithOutcome> queuesWithOutcomes = scenario.queuesWithOutcomes();

        // Build JSON response with all queues owned by environmentId
        String jsonResponse = buildQueueListJson(queuesWithOutcomes, environmentId);

        // Mock GET to return queue list
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        // Mock DELETE to return different outcomes per queue based on URL
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    for (QueueWithOutcome qwo : queuesWithOutcomes) {
                        if (url.contains(qwo.queueId().toString())) {
                            return applyOutcome(qwo.outcome());
                        }
                    }
                    return new ResponseEntity<>(HttpStatus.ACCEPTED);
                });

        // Call method under test - should not throw
        queueServiceClient.deleteQueuesForEnvironment(environmentId, domainId, solutionId);

        // Verify DELETE was attempted for ALL queues
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(queuesWithOutcomes.size()))
                .exchange(urlCaptor.capture(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class));

        List<String> capturedUrls = urlCaptor.getAllValues();

        // Verify each queue had a DELETE attempt
        for (QueueWithOutcome qwo : queuesWithOutcomes) {
            String expectedUrlFragment = "/queues/" + qwo.queueId();
            assertThat(capturedUrls)
                    .as("DELETE should be attempted for queue %s", qwo.queueId())
                    .anyMatch(url -> url.contains(expectedUrlFragment));
        }
    }

    /**
     * Property 4b: EnvironmentService completes deletion regardless of queue cleanup outcome.
     *
     * <p><strong>Validates: Requirements 5.2, 5.3</strong></p>
     *
     * <p>Generates random failure patterns for queue cleanup. Verifies environment deletion
     * always completes (cache eviction, secret deletion, row deletion all called).</p>
     */
    @Property(tries = 20)
    @Tag("Feature: environment-delete-queue-cleanup, Property 4: Best-effort resilience")
    void environmentDeletionCompletesRegardlessOfQueueCleanupOutcome(
            @ForAll("serviceScenarios") ServiceScenario scenario) {

        // Reset mocks to isolate each try
        reset(environmentRepository, solutionRepository, domainRepository,
                secretsBackend, authEnvCache, authSecretCache, restTemplate);

        when(cacheManager.getCache("auth-environment")).thenReturn(authEnvCache);
        when(cacheManager.getCache("auth-shared-secret")).thenReturn(authSecretCache);

        UUID environmentId = scenario.environmentId();
        UUID solutionUuid = scenario.solutionUuid();
        UUID domainUuid = scenario.domainUuid();
        String domainId = scenario.domainId();
        String solutionId = scenario.solutionId();
        String sessionToken = scenario.sessionToken();
        List<QueueWithOutcome> queuesWithOutcomes = scenario.queuesWithOutcomes();

        // Set up environment entity
        Environment environment = new Environment();
        environment.setId(environmentId);
        environment.setSolutionUuid(solutionUuid);
        environment.setSessionToken(sessionToken);
        environment.setApplicationName("test-app");
        environment.setType(EnvironmentType.DIRECT);
        environment.setCreatedAt(Instant.now());

        // Set up solution entity
        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId(solutionId);
        solution.setDomainUuid(domainUuid);

        // Set up domain entity
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(domainId);

        // Mock repository lookups
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(solutionRepository.findById(solutionUuid)).thenReturn(Optional.of(solution));
        when(domainRepository.findById(domainUuid)).thenReturn(Optional.of(domain));

        // Mock GET to return queue list
        String jsonResponse = buildQueueListJson(queuesWithOutcomes, environmentId);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        // Mock DELETE with random failure patterns
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    for (QueueWithOutcome qwo : queuesWithOutcomes) {
                        if (url.contains(qwo.queueId().toString())) {
                            return applyOutcome(qwo.outcome());
                        }
                    }
                    return new ResponseEntity<>(HttpStatus.ACCEPTED);
                });

        // Call deleteEnvironment - should always complete
        environmentService.deleteEnvironment(environmentId);

        // Verify environment deletion always completes:
        // 1. Cache eviction happened
        verify(authEnvCache).evict(sessionToken);
        verify(authSecretCache).evict(environmentId);

        // 2. Secret deletion called
        verify(secretsBackend).delete(environmentId);

        // 3. Row deletion called
        verify(environmentRepository).deleteById(environmentId);
    }

    // ========== Helpers ==========

    private ResponseEntity<Void> applyOutcome(FailurePattern outcome) {
        switch (outcome) {
            case SUCCESS_202:
                return new ResponseEntity<>(HttpStatus.ACCEPTED);
            case NOT_FOUND_404:
                throw new org.springframework.web.client.HttpClientErrorException(HttpStatus.NOT_FOUND);
            case SERVER_ERROR_500:
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
            case CONNECTION_TIMEOUT:
                throw new ResourceAccessException("Connection timed out");
            case CONNECTION_REFUSED:
                throw new ResourceAccessException("Connection refused");
            default:
                return new ResponseEntity<>(HttpStatus.ACCEPTED);
        }
    }

    private String buildQueueListJson(List<QueueWithOutcome> queues, UUID environmentId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"queues\":{\"queue\":[");
        for (int i = 0; i < queues.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(queues.get(i).queueId()).append("\",")
              .append("\"ownerId\":\"").append(environmentId).append("\",")
              .append("\"name\":\"queue-").append(i).append("\"}");
        }
        sb.append("]}}");
        return sb.toString();
    }

    // ========== Arbitraries ==========

    /**
     * Generates client-level test scenarios with random environmentId, domainId, solutionId,
     * and 1–10 queues each with a random failure pattern.
     */
    @Provide
    Arbitrary<ClientScenario> clientScenarios() {
        Arbitrary<UUID> envIds = Arbitraries.randomValue(random -> UUID.randomUUID());
        Arbitrary<String> domainIds = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(15);
        Arbitrary<String> solutionIds = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(15);
        Arbitrary<List<QueueWithOutcome>> queueLists = queuesWithOutcomes()
                .list()
                .ofMinSize(1)
                .ofMaxSize(10);

        return Combinators.combine(envIds, domainIds, solutionIds, queueLists)
                .as(ClientScenario::new);
    }

    /**
     * Generates service-level test scenarios with full environment/solution/domain setup
     * and 1–10 queues each with a random failure pattern.
     */
    @Provide
    Arbitrary<ServiceScenario> serviceScenarios() {
        Arbitrary<UUID> envIds = Arbitraries.randomValue(random -> UUID.randomUUID());
        Arbitrary<UUID> solutionUuids = Arbitraries.randomValue(random -> UUID.randomUUID());
        Arbitrary<UUID> domainUuids = Arbitraries.randomValue(random -> UUID.randomUUID());
        Arbitrary<String> domainIds = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(15);
        Arbitrary<String> solutionIds = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(15);
        Arbitrary<String> sessionTokens = Arbitraries.randomValue(random -> UUID.randomUUID().toString());
        Arbitrary<List<QueueWithOutcome>> queueLists = queuesWithOutcomes()
                .list()
                .ofMinSize(1)
                .ofMaxSize(10);

        return Combinators.combine(envIds, solutionUuids, domainUuids, domainIds, solutionIds, sessionTokens, queueLists)
                .as(ServiceScenario::new);
    }

    private Arbitrary<QueueWithOutcome> queuesWithOutcomes() {
        Arbitrary<UUID> queueIds = Arbitraries.randomValue(random -> UUID.randomUUID());
        Arbitrary<FailurePattern> patterns = Arbitraries.of(FailurePattern.values());

        return Combinators.combine(queueIds, patterns).as(QueueWithOutcome::new);
    }

    // ========== Records and Enums ==========

    enum FailurePattern {
        SUCCESS_202,
        NOT_FOUND_404,
        SERVER_ERROR_500,
        CONNECTION_TIMEOUT,
        CONNECTION_REFUSED
    }

    record QueueWithOutcome(UUID queueId, FailurePattern outcome) {}

    record ClientScenario(UUID environmentId, String domainId, String solutionId,
                          List<QueueWithOutcome> queuesWithOutcomes) {}

    record ServiceScenario(UUID environmentId, UUID solutionUuid, UUID domainUuid,
                           String domainId, String solutionId, String sessionToken,
                           List<QueueWithOutcome> queuesWithOutcomes) {}
}
