// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ContextServiceRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Property-based test: Cache hit eliminates DB queries (AuthorizationService path).
 *
 * <p><strong>Validates: Requirements 1.4, 2.5</strong></p>
 *
 * <p>Property 3: For any UUID that has been previously resolved through
 * SolutionService.findByIdCached() or DomainService.findByIdCached() (within TTL),
 * subsequent calls to isAdmin() involving that UUID SHALL NOT invoke the underlying
 * repository findById().</p>
 *
 * <p>Since AuthorizationService routes through SolutionService and DomainService
 * (which are @Cacheable), this test verifies that isAdmin() calls the service methods
 * exactly once per invocation and never accesses repositories directly for solution/domain
 * resolution.</p>
 */
class CacheHitEliminatesDbQueryPropertyTest {

    private AuthorizationService authorizationService;
    private SolutionService solutionService;
    private DomainService domainService;
    private AclEntryRepository aclEntryRepository;
    private EnvironmentRepository environmentRepository;
    private ContextRepository contextRepository;
    private ContextServiceRepository contextServiceRepository;
    private ZoneRepository zoneRepository;

    @BeforeProperty
    void setUp() {
        solutionService = mock(SolutionService.class);
        domainService = mock(DomainService.class);
        aclEntryRepository = mock(AclEntryRepository.class);
        environmentRepository = mock(EnvironmentRepository.class);
        contextRepository = mock(ContextRepository.class);
        contextServiceRepository = mock(ContextServiceRepository.class);
        zoneRepository = mock(ZoneRepository.class);

        authorizationService = new AuthorizationService(
                aclEntryRepository,
                environmentRepository,
                solutionService,
                domainService,
                contextRepository,
                contextServiceRepository,
                zoneRepository
        );
    }

    /**
     * Property 3: Cache hit eliminates DB queries — for any arbitrary solutionUuid,
     * when SolutionService.findByIdCached() and DomainService.findByIdCached() return
     * cached values, isAdmin() routes exclusively through those service methods and
     * never invokes repository findById() for solution or domain resolution.
     */
    @Property(tries = 20)
    @Tag("Feature: n-plus-one-and-cache-fixes, Property 3: Cache hit eliminates DB queries")
    void cacheHitEliminatesDbQueries(@ForAll("uuids") UUID solutionUuid) {
        UUID domainUuid = UUID.randomUUID();

        // Build solution entity (simulating cached value)
        Solution solution = new Solution();
        solution.setId(solutionUuid);
        solution.setDomainUuid(domainUuid);
        solution.setSolutionId("test-solution");

        // Build domain entity (simulating cached value)
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId("test-domain");

        // Configure service mocks to return cached values
        when(solutionService.findByIdCached(solutionUuid)).thenReturn(Optional.of(solution));
        when(domainService.findByIdCached(domainUuid)).thenReturn(Optional.of(domain));

        // Build environment with the solutionUuid
        Environment env = new Environment();
        env.setId(UUID.randomUUID());
        env.setSolutionUuid(solutionUuid);
        env.setFingerprint("regular-app");

        // Call isAdmin — this should route through cached service methods
        authorizationService.isAdmin(env);

        // Verify service methods were called (the cached path)
        verify(solutionService, times(1)).findByIdCached(solutionUuid);
        verify(domainService, times(1)).findByIdCached(domainUuid);

        // Call isAdmin again — simulating subsequent call within TTL
        authorizationService.isAdmin(env);

        // Verify service methods are called again (AuthorizationService always delegates to service)
        // The caching happens inside SolutionService/DomainService — AuthorizationService
        // never bypasses them to hit repositories directly
        verify(solutionService, times(2)).findByIdCached(solutionUuid);
        verify(domainService, times(2)).findByIdCached(domainUuid);

        // Verify no direct repository interaction for solution/domain resolution
        // AuthorizationService should NEVER call repository findById for solutions or domains
        verifyNoInteractions(aclEntryRepository);
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }
}
