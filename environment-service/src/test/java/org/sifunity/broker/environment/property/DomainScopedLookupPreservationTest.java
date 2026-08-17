// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.DomainDTO;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.SolutionDTO;
import org.sifunity.broker.environment.exception.DuplicateSolutionException;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Preservation property tests for the domain-scoped lookup bugfix.
 *
 * <p>These tests verify behaviors that MUST remain unchanged after the fix is applied.
 * They are run BEFORE the fix to establish the baseline, and again AFTER to confirm
 * no regressions.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DomainScopedLookupPreservationTest {

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ContextRepository contextRepository;

    private final Random random = new Random();

    /**
     * Preservation test 1: Unique SolutionId creation succeeds with zone bootstrapping.
     *
     * <p>For randomized globally-unique solutionId values, createSolution succeeds and
     * returns a valid SolutionDTO with environment-global zone and DEFAULT context
     * bootstrapped.</p>
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @RepeatedTest(3)
    @Tag("preservation")
    void uniqueSolutionIdCreationSucceedsWithZoneBootstrapping() {
        // Create a domain
        String domainId = generateValidDomainId();
        domainService.createDomain(new DomainCreateRequest(domainId, "Preservation test domain"));

        // Create a solution with a globally unique solutionId
        String solutionId = generateValidSolutionId();
        SolutionDTO solution = solutionService.createSolution(
                new SolutionCreateRequest(domainId, solutionId, "Unique solution"));

        // Assert solution was created correctly
        assertThat(solution).isNotNull();
        assertThat(solution.id()).isNotNull();
        assertThat(solution.solutionId()).isEqualTo(solutionId);
        assertThat(solution.domainId()).isEqualTo(domainId);

        // Assert environment-global zone was bootstrapped
        List<Zone> zones = zoneRepository.findBySolutionUuid(solution.id());
        assertThat(zones)
                .as("Solution should have environment-global zone bootstrapped")
                .anyMatch(z -> "environment-global".equals(z.getZoneId()));

        // Assert DEFAULT context exists in the environment-global zone
        Zone envGlobalZone = zones.stream()
                .filter(z -> "environment-global".equals(z.getZoneId()))
                .findFirst()
                .orElseThrow();

        assertThat(contextRepository.existsByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT"))
                .as("environment-global zone should have DEFAULT context")
                .isTrue();
    }

    /**
     * Preservation test 2: Same-domain duplicate rejection throws DuplicateSolutionException.
     *
     * <p>For a solutionId that already exists in the same domain, createSolution throws
     * DuplicateSolutionException.</p>
     *
     * <p><strong>Validates: Requirements 3.2</strong></p>
     */
    @RepeatedTest(3)
    @Tag("preservation")
    void sameDomainDuplicateSolutionIdIsRejected() {
        // Create a domain
        String domainId = generateValidDomainId();
        domainService.createDomain(new DomainCreateRequest(domainId, "Duplicate rejection test domain"));

        // Create a solution
        String solutionId = generateValidSolutionId();
        solutionService.createSolution(
                new SolutionCreateRequest(domainId, solutionId, "First solution"));

        // Attempt to create a solution with the same solutionId in the same domain
        assertThatThrownBy(() -> solutionService.createSolution(
                new SolutionCreateRequest(domainId, solutionId, "Duplicate solution")))
                .isInstanceOf(DuplicateSolutionException.class);
    }

    /**
     * Preservation test 3: Domain-scoped listing returns only solutions for that domain.
     *
     * <p>After creating solutions in two different domains, listSolutionsByDomain(domainId)
     * returns only solutions belonging to that domain.</p>
     *
     * <p>Uses DIFFERENT solutionIds per domain to avoid triggering the cross-domain bug.</p>
     *
     * <p><strong>Validates: Requirements 3.3, 3.4</strong></p>
     */
    @RepeatedTest(3)
    @Tag("preservation")
    void listSolutionsByDomainReturnsScopedResults() {
        // Create two domains
        String domainIdA = generateValidDomainId();
        String domainIdB = generateValidDomainId();
        domainService.createDomain(new DomainCreateRequest(domainIdA, "Domain A for listing test"));
        domainService.createDomain(new DomainCreateRequest(domainIdB, "Domain B for listing test"));

        // Create solutions with DIFFERENT solutionIds in each domain (avoids cross-domain bug)
        String solutionIdA = generateValidSolutionId();
        String solutionIdB = generateValidSolutionId();

        SolutionDTO solA = solutionService.createSolution(
                new SolutionCreateRequest(domainIdA, solutionIdA, "Solution in domain A"));
        SolutionDTO solB = solutionService.createSolution(
                new SolutionCreateRequest(domainIdB, solutionIdB, "Solution in domain B"));

        // List solutions for domain A
        List<SolutionDTO> domainASolutions = solutionService.listSolutionsByDomain(domainIdA);
        assertThat(domainASolutions)
                .as("Domain A listing should contain solution A")
                .anyMatch(s -> s.solutionId().equals(solutionIdA));
        assertThat(domainASolutions)
                .as("Domain A listing should NOT contain solution B")
                .noneMatch(s -> s.solutionId().equals(solutionIdB));

        // List solutions for domain B
        List<SolutionDTO> domainBSolutions = solutionService.listSolutionsByDomain(domainIdB);
        assertThat(domainBSolutions)
                .as("Domain B listing should contain solution B")
                .anyMatch(s -> s.solutionId().equals(solutionIdB));
        assertThat(domainBSolutions)
                .as("Domain B listing should NOT contain solution A")
                .noneMatch(s -> s.solutionId().equals(solutionIdA));
    }

    // ========== Test Data Generators ==========

    private String generateValidDomainId() {
        String[] prefixes = {"alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        int number = random.nextInt(1000);
        long timestamp = System.nanoTime() % 10000;
        return prefix + "-" + number + "-" + timestamp;
    }

    private String generateValidSolutionId() {
        String[] environments = {"production", "staging", "testing", "development", "qa", "uat", "demo", "sandbox"};
        String[] suffixes = {"env", "instance", "cluster", "deployment"};
        String environment = environments[random.nextInt(environments.length)];

        if (random.nextBoolean()) {
            String suffix = suffixes[random.nextInt(suffixes.length)];
            int number = random.nextInt(100);
            String separator = random.nextBoolean() ? "-" : "_";
            return environment + separator + suffix + separator + number;
        } else {
            int number = random.nextInt(100);
            return environment + "-" + number;
        }
    }
}
