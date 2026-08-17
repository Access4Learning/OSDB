// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EntityTagRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for cascade cleanup wiring in {@link SolutionService#deleteSolution(String, String)}.
 *
 * <p>Validates:
 * <ul>
 *   <li>CascadeCleanupClient is called with correct domainId and solutionId after deletion</li>
 *   <li>Deletion succeeds even if cascade client throws</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SolutionServiceCascadeTest {

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private ContextRepository contextRepository;

    @Mock
    private ContextServiceRepository contextServiceRepository;

    @Mock
    private EntityTagRepository entityTagRepository;

    @Mock
    private ProviderRegistrationRepository providerRegistrationRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private ContextServiceService contextServiceService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private CascadeCleanupClient cascadeCleanupClient;

    @Mock
    private Cache authSolutionCache;

    private SolutionService solutionService;

    private Solution solution;
    private Domain domain;
    private final String solutionId = "test-solution";
    private final String domainId = "test-domain";
    private final UUID solutionUuid = UUID.randomUUID();
    private final UUID domainUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        solutionService = new SolutionService(solutionRepository, domainRepository,
                zoneRepository, contextRepository, contextServiceRepository,
                entityTagRepository, providerRegistrationRepository, environmentRepository,
                contextServiceService, cacheManager, cascadeCleanupClient);

        solution = new Solution();
        solution.setId(solutionUuid);
        solution.setSolutionId(solutionId);
        solution.setDomainUuid(domainUuid);
        solution.setCreatedAt(Instant.now());

        domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(domainId);
    }

    @Test
    void deleteSolution_callsCascadeCleanupClientWithCorrectArgs() {
        when(domainRepository.findByDomainId(domainId)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)).thenReturn(Optional.of(solution));
        when(cacheManager.getCache("auth-solution")).thenReturn(authSolutionCache);
        when(entityTagRepository.findBySolutionId(solutionUuid)).thenReturn(Collections.emptyList());

        solutionService.deleteSolution(domainId, solutionId);

        // Verify cascade client called after repository delete with correct args
        InOrder inOrder = inOrder(solutionRepository, cascadeCleanupClient);
        inOrder.verify(solutionRepository).delete(solution);
        inOrder.verify(cascadeCleanupClient).triggerSolutionCleanup(domainId, solutionId);
    }

    @Test
    void deleteSolution_succeedsEvenIfCascadeClientThrows() {
        when(domainRepository.findByDomainId(domainId)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)).thenReturn(Optional.of(solution));
        when(cacheManager.getCache("auth-solution")).thenReturn(authSolutionCache);
        when(entityTagRepository.findBySolutionId(solutionUuid)).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("Queue service unavailable"))
                .when(cascadeCleanupClient).triggerSolutionCleanup(domainId, solutionId);

        assertThatCode(() -> solutionService.deleteSolution(domainId, solutionId))
                .doesNotThrowAnyException();

        // Verify deletion still happened
        verify(solutionRepository).delete(solution);
    }

    @Test
    void deleteSolution_throwsSolutionNotFound_whenSolutionDoesNotExist() {
        when(domainRepository.findByDomainId(domainId)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, "nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionService.deleteSolution(domainId, "nonexistent"))
                .isInstanceOf(SolutionNotFoundException.class);

        // Cascade client never called
        verifyNoInteractions(cascadeCleanupClient);
    }
}
