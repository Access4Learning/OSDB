// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based test: isAdmin behavioral equivalence.
 *
 * <p><strong>Feature: n-plus-one-and-cache-fixes, Property 1: isAdmin behavioral equivalence</strong></p>
 *
 * <p>For any Environment entity with any combination of null/non-null solutionUuid,
 * existing/missing Solution, existing/missing Domain, and any fingerprint value,
 * {@code AuthorizationService.isAdmin(env)} SHALL return the same boolean result
 * when using {@code SolutionService.findByIdCached()} and {@code DomainService.findByIdCached()}
 * as the previous direct-repository implementation.</p>
 *
 * <p><strong>Validates: Requirements 1.3, 1.4, 1.5</strong></p>
 */
@Tag("n-plus-one-and-cache-fixes")
@Tag("property-1-isAdmin-behavioral-equivalence")
class IsAdminBehavioralEquivalencePropertyTest {

    private AuthorizationService authorizationService;
    private SolutionService solutionService;
    private DomainService domainService;

    @BeforeTry
    void setUp() {
        AclEntryRepository aclEntryRepository = mock(AclEntryRepository.class);
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        solutionService = mock(SolutionService.class);
        domainService = mock(DomainService.class);
        ContextRepository contextRepository = mock(ContextRepository.class);
        ContextServiceRepository contextServiceRepository = mock(ContextServiceRepository.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);

        authorizationService = new AuthorizationService(
                aclEntryRepository, environmentRepository, solutionService, domainService,
                contextRepository, contextServiceRepository, zoneRepository);
    }

    /**
     * Property 1: isAdmin behavioral equivalence.
     *
     * <p>Generates arbitrary Environment/Solution/Domain combinations and asserts
     * the refactored isAdmin() matches the expected legacy logic for every combination.</p>
     *
     * <p><strong>Validates: Requirements 1.3, 1.4, 1.5</strong></p>
     */
    @Property(tries = 20)
    @Label("isAdmin returns expected result for arbitrary Environment/Solution/Domain combinations")
    void isAdminMatchesExpectedLegacyLogic(@ForAll("isAdminScenarios") IsAdminScenario scenario) {
        // Build the environment under test
        Environment env = new Environment();
        env.setId(UUID.randomUUID());
        env.setType(EnvironmentType.BROKERED);
        env.setApplicationName("test-app");
        env.setSessionToken(UUID.randomUUID().toString());
        env.setSharedSecret("secret");
        env.setDefaultZoneId("default");
        env.setSolutionUuid(scenario.solutionUuid());
        env.setFingerprint(scenario.fingerprint());

        // Configure mocks based on scenario
        if (scenario.solutionUuid() != null) {
            if (scenario.solutionPresent()) {
                Solution solution = new Solution();
                solution.setId(scenario.solutionUuid());
                solution.setDomainUuid(scenario.domainUuid());
                solution.setSolutionId(scenario.solutionId());
                when(solutionService.findByIdCached(scenario.solutionUuid()))
                        .thenReturn(Optional.of(solution));

                if (scenario.domainPresent()) {
                    Domain domain = new Domain();
                    domain.setId(scenario.domainUuid());
                    domain.setDomainId(scenario.domainId());
                    when(domainService.findByIdCached(scenario.domainUuid()))
                            .thenReturn(Optional.of(domain));
                } else {
                    when(domainService.findByIdCached(scenario.domainUuid()))
                            .thenReturn(Optional.empty());
                }
            } else {
                when(solutionService.findByIdCached(scenario.solutionUuid()))
                        .thenReturn(Optional.empty());
            }
        }

        // Compute expected result using legacy logic:
        // 1. null solutionUuid → false
        // 2. missing solution → false
        // 3. check GlobalAdminConstants.isGlobalAdmin(domainId, solutionId) → true if match
        // 4. check "broker-services".equals(fingerprint) → true if match
        // 5. otherwise → false
        boolean expected = computeExpectedIsAdmin(scenario);

        // Act
        boolean actual = authorizationService.isAdmin(env);

        // Assert
        assertThat(actual)
                .as("isAdmin for scenario: solutionUuid=%s, solutionPresent=%s, domainPresent=%s, " +
                                "domainId=%s, solutionId=%s, fingerprint=%s → expected=%s",
                        scenario.solutionUuid(), scenario.solutionPresent(), scenario.domainPresent(),
                        scenario.domainId(), scenario.solutionId(), scenario.fingerprint(), expected)
                .isEqualTo(expected);
    }

    private boolean computeExpectedIsAdmin(IsAdminScenario scenario) {
        // Step 1: null solutionUuid → false
        if (scenario.solutionUuid() == null) {
            return false;
        }
        // Step 2: compute globalAdmin from solution/domain lookup
        boolean globalAdmin = false;
        if (scenario.solutionPresent()) {
            // Step 3: resolve domain and check global admin
            String domainId = null;
            if (scenario.domainPresent()) {
                domainId = scenario.domainId();
            }
            globalAdmin = GlobalAdminConstants.isGlobalAdmin(domainId, scenario.solutionId());
        }
        // Step 4: if global admin, return true
        if (globalAdmin) {
            return true;
        }
        // Step 5: broker-services fingerprint check (independent of solution lookup result)
        return "broker-services".equals(scenario.fingerprint());
    }

    @Provide
    Arbitrary<IsAdminScenario> isAdminScenarios() {
        // solutionUuid: null or random UUID
        Arbitrary<UUID> nullableSolutionUuid = Arbitraries.of(
                (UUID) null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
        );

        Arbitrary<Boolean> solutionPresent = Arbitraries.of(true, false);
        Arbitrary<Boolean> domainPresent = Arbitraries.of(true, false);

        // domainId: global admin, or arbitrary non-admin
        Arbitrary<String> domainId = Arbitraries.of(
                GlobalAdminConstants.GLOBAL_DOMAIN,
                "regular-domain",
                "test-domain-xyz",
                "another-domain"
        );

        // solutionId: global admin, or arbitrary non-admin
        Arbitrary<String> solutionId = Arbitraries.of(
                GlobalAdminConstants.GLOBAL_SOLUTION,
                "regular-solution",
                "prod-solution",
                "test-solution-abc"
        );

        // fingerprint: broker-services, null, or arbitrary
        Arbitrary<String> fingerprint = Arbitraries.of(
                "broker-services",
                null,
                "some-fingerprint",
                "another-app"
        );

        return Combinators.combine(nullableSolutionUuid, solutionPresent, domainPresent,
                        domainId, solutionId, fingerprint)
                .as((solUuid, solPresent, domPresent, domId, solId, fp) ->
                        new IsAdminScenario(
                                solUuid,
                                solPresent,
                                domPresent,
                                solUuid != null ? UUID.randomUUID() : null,
                                domId,
                                solId,
                                fp
                        ));
    }

    /**
     * Test scenario capturing all dimensions of isAdmin logic.
     */
    record IsAdminScenario(
            UUID solutionUuid,
            boolean solutionPresent,
            boolean domainPresent,
            UUID domainUuid,
            String domainId,
            String solutionId,
            String fingerprint
    ) {}
}
