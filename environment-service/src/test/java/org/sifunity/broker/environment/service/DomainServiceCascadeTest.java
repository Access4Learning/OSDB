// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.exception.DomainNotFoundException;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for cascade cleanup wiring in {@link DomainService#deleteDomain(String)}.
 *
 * <p>Validates:
 * <ul>
 *   <li>CascadeCleanupClient is called with correct domainId after deletion</li>
 *   <li>Deletion succeeds even if cascade client throws</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DomainServiceCascadeTest {

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private CascadeCleanupClient cascadeCleanupClient;

    @Mock
    private Cache authDomainCache;

    private DomainService domainService;

    private Domain domain;
    private final String domainId = "test-domain";
    private final UUID domainUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        domainService = new DomainService(domainRepository, cacheManager, cascadeCleanupClient);

        domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(domainId);
        domain.setCreatedAt(Instant.now());
    }

    @Test
    void deleteDomain_callsCascadeCleanupClientWithCorrectDomainId() {
        when(domainRepository.findByDomainId(domainId)).thenReturn(Optional.of(domain));
        when(cacheManager.getCache("auth-domain")).thenReturn(authDomainCache);

        domainService.deleteDomain(domainId);

        // Verify cascade client called after repository delete
        InOrder inOrder = inOrder(domainRepository, cascadeCleanupClient);
        inOrder.verify(domainRepository).delete(domain);
        inOrder.verify(cascadeCleanupClient).triggerDomainCleanup(domainId);
    }

    @Test
    void deleteDomain_succeedsEvenIfCascadeClientThrows() {
        when(domainRepository.findByDomainId(domainId)).thenReturn(Optional.of(domain));
        when(cacheManager.getCache("auth-domain")).thenReturn(authDomainCache);
        doThrow(new RuntimeException("Queue service unavailable"))
                .when(cascadeCleanupClient).triggerDomainCleanup(domainId);

        assertThatCode(() -> domainService.deleteDomain(domainId))
                .doesNotThrowAnyException();

        // Verify deletion still happened
        verify(domainRepository).delete(domain);
    }

    @Test
    void deleteDomain_throwsDomainNotFound_whenDomainDoesNotExist() {
        when(domainRepository.findByDomainId("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> domainService.deleteDomain("nonexistent"))
                .isInstanceOf(DomainNotFoundException.class);

        // Cascade client never called
        verifyNoInteractions(cascadeCleanupClient);
    }
}
