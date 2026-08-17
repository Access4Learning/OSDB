// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.InOrder;
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
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based test for ordering guarantee.
 *
 * <p><strong>Property 5: Ordering guarantee</strong></p>
 *
 * <p>For any environment with owned queues, all queue DELETE requests SHALL be issued
 * BEFORE the call to {@code secretsBackend.delete(id)} and
 * {@code environmentRepository.deleteById(id)}.</p>
 *
 * <p><strong>Validates: Requirements 5.1</strong></p>
 */
class QueueCleanupOrderingPropertyTest {

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
     * Property 5: All queue DELETE requests are issued BEFORE secretsBackend.delete
     * and environmentRepository.deleteById.
     *
     * <p><strong>Validates: Requirements 5.1</strong></p>
     *
     * <p>Generates random environments with 1–10 owned queues. Uses Mockito InOrder
     * verification to confirm all DELETE requests on restTemplate precede
     * secretsBackend.delete and environmentRepository.deleteById.</p>
     */
    @Property(tries = 20)
    @Tag("Feature: environment-delete-queue-cleanup, Property 5: Ordering guarantee")
    void allDeleteRequestsPrecedeSecretAndRowDeletion(
            @ForAll("orderingScenarios") OrderingScenario scenario) {

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
        List<UUID> queueIds = scenario.queueIds();

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

        // Mock GET to return queue list with all queues owned by environment
        String jsonResponse = buildQueueListJson(queueIds, environmentId);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        // Mock DELETE to return 202 Accepted
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.ACCEPTED));

        // Call deleteEnvironment
        environmentService.deleteEnvironment(environmentId);

        // Use InOrder to verify ordering:
        // All DELETE calls on restTemplate BEFORE secretsBackend.delete and environmentRepository.deleteById
        InOrder inOrder = inOrder(restTemplate, secretsBackend, environmentRepository);

        // Verify all DELETE calls happened first (as a single batch)
        inOrder.verify(restTemplate, times(queueIds.size())).exchange(
                anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class));

        // Then secretsBackend.delete
        inOrder.verify(secretsBackend).delete(environmentId);

        // Then environmentRepository.deleteById
        inOrder.verify(environmentRepository).deleteById(environmentId);
    }

    // ========== Helpers ==========

    private String buildQueueListJson(List<UUID> queueIds, UUID environmentId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"queues\":{\"queue\":[");
        for (int i = 0; i < queueIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(queueIds.get(i)).append("\",")
              .append("\"ownerId\":\"").append(environmentId).append("\",")
              .append("\"name\":\"queue-").append(i).append("\"}");
        }
        sb.append("]}}");
        return sb.toString();
    }

    // ========== Arbitraries ==========

    /**
     * Generates ordering test scenarios with random environmentId, solutionUuid,
     * domainUuid, domainId, solutionId, sessionToken, and 1–10 owned queue UUIDs.
     */
    @Provide
    Arbitrary<OrderingScenario> orderingScenarios() {
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
        Arbitrary<List<UUID>> queueIdLists = Arbitraries.randomValue(random -> UUID.randomUUID())
                .list()
                .ofMinSize(1)
                .ofMaxSize(10);

        return Combinators.combine(envIds, solutionUuids, domainUuids, domainIds, solutionIds, sessionTokens, queueIdLists)
                .as(OrderingScenario::new);
    }

    // ========== Records ==========

    record OrderingScenario(UUID environmentId, UUID solutionUuid, UUID domainUuid,
                            String domainId, String solutionId, String sessionToken,
                            List<UUID> queueIds) {}
}
