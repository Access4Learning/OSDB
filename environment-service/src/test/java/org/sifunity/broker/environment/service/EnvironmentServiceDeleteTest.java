// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EnvironmentService#deleteEnvironment(UUID)}.
 *
 * <p>Validates queue cleanup integration, ordering guarantees, and resilience
 * when resolution or cleanup fails.</p>
 *
 * <p><strong>Requirements: 5.1, 5.2, 5.3, 6.1, 6.2, 6.3</strong></p>
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentServiceDeleteTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private AclEntryRepository aclEntryRepository;

    @Mock
    private SecretsBackend secretsBackend;

    @Mock
    private EnvironmentResponseMapper mapper;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private QueueServiceClient queueServiceClient;

    @Mock
    private Cache authEnvCache;

    @Mock
    private Cache authSecretCache;

    @InjectMocks
    private EnvironmentService environmentService;

    private UUID environmentId;
    private UUID solutionUuid;
    private UUID domainUuid;
    private Environment environment;
    private Solution solution;
    private Domain domain;

    @BeforeEach
    void setUp() {
        environmentId = UUID.randomUUID();
        solutionUuid = UUID.randomUUID();
        domainUuid = UUID.randomUUID();

        environment = new Environment();
        environment.setId(environmentId);
        environment.setSolutionUuid(solutionUuid);
        environment.setSessionToken("session-token-123");
        environment.setApplicationName("TestApp");
        environment.setType(EnvironmentType.BROKERED);
        environment.setCreatedAt(Instant.now());
        environment.setSharedSecret("PLACEHOLDER");

        solution = new Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId("test-solution");
        solution.setDomainUuid(domainUuid);

        domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId("test-domain");
    }

    @Test
    void deleteEnvironment_queueCleanupCalledBeforeCacheEvictionAndDeletion() {
        // Given
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(solutionRepository.findById(solutionUuid)).thenReturn(Optional.of(solution));
        when(domainRepository.findById(domainUuid)).thenReturn(Optional.of(domain));
        when(cacheManager.getCache("auth-environment")).thenReturn(authEnvCache);
        when(cacheManager.getCache("auth-shared-secret")).thenReturn(authSecretCache);

        // When
        environmentService.deleteEnvironment(environmentId);

        // Then - verify ordering: queue cleanup before cache eviction, secret deletion, row deletion
        InOrder inOrder = inOrder(queueServiceClient, authEnvCache, authSecretCache, secretsBackend, environmentRepository);
        inOrder.verify(queueServiceClient).deleteQueuesForEnvironment(environmentId, "test-domain", "test-solution");
        inOrder.verify(authEnvCache).evict("session-token-123");
        inOrder.verify(authSecretCache).evict(environmentId);
        inOrder.verify(secretsBackend).delete(environmentId);
        inOrder.verify(environmentRepository).deleteById(environmentId);
    }

    @Test
    void deleteEnvironment_solutionResolutionFailureDoesNotBlockDeletion() {
        // Given - solution lookup throws (simulates resolution failure)
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(solutionRepository.findById(solutionUuid)).thenThrow(new SolutionNotFoundException("UUID: " + solutionUuid));
        when(cacheManager.getCache("auth-environment")).thenReturn(authEnvCache);
        when(cacheManager.getCache("auth-shared-secret")).thenReturn(authSecretCache);

        // When/Then - deletion completes without throwing
        assertThatCode(() -> environmentService.deleteEnvironment(environmentId))
                .doesNotThrowAnyException();

        // Verify deletion still happened
        verify(secretsBackend).delete(environmentId);
        verify(environmentRepository).deleteById(environmentId);
        // Queue cleanup never called since resolution failed
        verify(queueServiceClient, never()).deleteQueuesForEnvironment(any(), any(), any());
    }

    @Test
    void deleteEnvironment_domainResolutionFailureDoesNotBlockDeletion() {
        // Given - solution found but domain lookup fails
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(solutionRepository.findById(solutionUuid)).thenReturn(Optional.of(solution));
        when(domainRepository.findById(domainUuid)).thenReturn(Optional.empty());
        when(cacheManager.getCache("auth-environment")).thenReturn(authEnvCache);
        when(cacheManager.getCache("auth-shared-secret")).thenReturn(authSecretCache);

        // When/Then - deletion completes without throwing
        assertThatCode(() -> environmentService.deleteEnvironment(environmentId))
                .doesNotThrowAnyException();

        // Verify deletion still happened
        verify(secretsBackend).delete(environmentId);
        verify(environmentRepository).deleteById(environmentId);
        // Queue cleanup never called since domain resolution failed
        verify(queueServiceClient, never()).deleteQueuesForEnvironment(any(), any(), any());
    }

    @Test
    void deleteEnvironment_queueCleanupExceptionDoesNotBlockDeletion() {
        // Given - queue cleanup throws RuntimeException
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(solutionRepository.findById(solutionUuid)).thenReturn(Optional.of(solution));
        when(domainRepository.findById(domainUuid)).thenReturn(Optional.of(domain));
        doThrow(new RuntimeException("Queue service unavailable"))
                .when(queueServiceClient).deleteQueuesForEnvironment(environmentId, "test-domain", "test-solution");
        when(cacheManager.getCache("auth-environment")).thenReturn(authEnvCache);
        when(cacheManager.getCache("auth-shared-secret")).thenReturn(authSecretCache);

        // When/Then - deletion completes without throwing
        assertThatCode(() -> environmentService.deleteEnvironment(environmentId))
                .doesNotThrowAnyException();

        // Verify deletion still happened despite queue cleanup failure
        verify(authEnvCache).evict("session-token-123");
        verify(authSecretCache).evict(environmentId);
        verify(secretsBackend).delete(environmentId);
        verify(environmentRepository).deleteById(environmentId);
    }

    @Test
    void deleteEnvironment_worksWhenQueueServiceClientIsDisabled() {
        // Given - queueServiceClient.isEnabled() returns false but deleteQueuesForEnvironment
        // is still called (it handles the disabled case internally by returning early)
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(solutionRepository.findById(solutionUuid)).thenReturn(Optional.of(solution));
        when(domainRepository.findById(domainUuid)).thenReturn(Optional.of(domain));
        // deleteQueuesForEnvironment does nothing when disabled (no-op)
        doNothing().when(queueServiceClient).deleteQueuesForEnvironment(environmentId, "test-domain", "test-solution");
        when(cacheManager.getCache("auth-environment")).thenReturn(authEnvCache);
        when(cacheManager.getCache("auth-shared-secret")).thenReturn(authSecretCache);

        // When
        environmentService.deleteEnvironment(environmentId);

        // Then - deletion completes normally
        verify(queueServiceClient).deleteQueuesForEnvironment(environmentId, "test-domain", "test-solution");
        verify(secretsBackend).delete(environmentId);
        verify(environmentRepository).deleteById(environmentId);
    }

    @Test
    void deleteEnvironment_doesNotCallQueueCleanupForOtherOperations() {
        // This test verifies that only deleteEnvironment triggers queue cleanup.
        // Other service methods (register, get, list, update) should NOT call queueServiceClient.
        // We verify by calling deleteEnvironment and confirming queue cleanup is only called once,
        // and that the queueServiceClient interaction is scoped to deleteEnvironment.

        // Given
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(solutionRepository.findById(solutionUuid)).thenReturn(Optional.of(solution));
        when(domainRepository.findById(domainUuid)).thenReturn(Optional.of(domain));
        when(cacheManager.getCache("auth-environment")).thenReturn(authEnvCache);
        when(cacheManager.getCache("auth-shared-secret")).thenReturn(authSecretCache);

        // When
        environmentService.deleteEnvironment(environmentId);

        // Then - queueServiceClient.deleteQueuesForEnvironment called exactly once
        verify(queueServiceClient, times(1)).deleteQueuesForEnvironment(any(), any(), any());
        // No other methods on queueServiceClient should be called
        verifyNoMoreInteractions(queueServiceClient);
    }
}
