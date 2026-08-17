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
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug condition exploration test for cross-domain same solutionId crash.
 *
 * <p><strong>Property 1: Bug Condition</strong> - Cross-Domain Same SolutionId Crashes
 * bootstrapEnvironmentGlobalZone</p>
 *
 * <p>This test encodes the EXPECTED (correct) behavior: creating a solution with a
 * solutionId that already exists in a different domain should succeed and bootstrap
 * the environment-global zone and DEFAULT context.</p>
 *
 * <p>On UNFIXED code, this test is expected to FAIL with
 * {@code IncorrectResultSizeDataAccessException} because
 * {@code bootstrapEnvironmentGlobalZone} calls {@code findBySolutionId(solutionId)}
 * which returns multiple rows when the same solutionId exists across domains.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 2.1, 2.2</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CrossDomainSolutionIdBugConditionExplorationTest {

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
     * Property 1: Bug Condition - Cross-Domain Same SolutionId Creation Succeeds
     *
     * <p><strong>Validates: Requirements 1.1, 1.2, 2.1, 2.2</strong></p>
     *
     * <p>Bug condition: solutionId exists in another domain but NOT in the target domain.
     * Expected behavior: createSolution succeeds and bootstraps environment-global zone
     * and DEFAULT context.</p>
     *
     * <p>On unfixed code, this will throw IncorrectResultSizeDataAccessException at
     * bootstrapEnvironmentGlobalZone because findBySolutionId returns 2 rows.</p>
     */
    @RepeatedTest(3)
    @Tag("bug-condition-exploration")
    void crossDomainSameSolutionIdShouldSucceed() {
        // Generate unique domain IDs for this iteration
        String domainIdA = generateValidDomainId();
        String domainIdB = generateValidDomainId();

        // Create two distinct domains
        DomainDTO domainA = domainService.createDomain(
                new DomainCreateRequest(domainIdA, "Domain A for bug exploration"));
        DomainDTO domainB = domainService.createDomain(
                new DomainCreateRequest(domainIdB, "Domain B for bug exploration"));

        // Generate a shared solutionId that will be used in both domains
        String sharedSolutionId = generateValidSolutionId();

        // Step 1: Create solution in domain A — this should always succeed
        SolutionDTO solutionA = solutionService.createSolution(
                new SolutionCreateRequest(domainIdA, sharedSolutionId, "Solution in domain A"));

        assertThat(solutionA).isNotNull();
        assertThat(solutionA.solutionId()).isEqualTo(sharedSolutionId);
        assertThat(solutionA.domainId()).isEqualTo(domainIdA);

        // Step 2: Create solution with SAME solutionId in domain B
        // BUG CONDITION: solutionId exists in domain A but NOT in domain B
        // On unfixed code: IncorrectResultSizeDataAccessException thrown here
        // On fixed code: should succeed
        SolutionDTO solutionB = solutionService.createSolution(
                new SolutionCreateRequest(domainIdB, sharedSolutionId, "Solution in domain B"));

        // Assert expected behavior: second creation returns valid SolutionDTO
        assertThat(solutionB).isNotNull();
        assertThat(solutionB.solutionId()).isEqualTo(sharedSolutionId);
        assertThat(solutionB.domainId()).isEqualTo(domainIdB);
        assertThat(solutionB.id()).isNotEqualTo(solutionA.id());

        // Assert environment-global zone and DEFAULT context were created for solution B
        // Use repositories directly to avoid ZoneService.listZonesBySolution which also
        // uses the buggy findBySolutionId path
        List<Zone> zonesB = zoneRepository.findBySolutionUuid(solutionB.id());
        assertThat(zonesB)
                .as("Solution B should have environment-global zone bootstrapped")
                .anyMatch(z -> "environment-global".equals(z.getZoneId()));

        Zone envGlobalZoneB = zonesB.stream()
                .filter(z -> "environment-global".equals(z.getZoneId()))
                .findFirst()
                .orElseThrow();

        assertThat(contextRepository.existsByZoneUuidAndContextId(envGlobalZoneB.getId(), "DEFAULT"))
                .as("environment-global zone for solution B should have DEFAULT context")
                .isTrue();
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
