// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.sifunity.broker.environment.EnvironmentServiceApplication;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.SolutionDTO;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Solution listing scoped to domain.
 *
 * <p><strong>Property 9: Solution Listing Scoped to Domain</strong></p>
 *
 * <p>For any domainId, listing solutions via GET /{domainId}/solutions shall
 * return only solutions whose domainId matches the URL path parameter. No
 * solutions from other domains shall appear in the result.</p>
 *
 * <p><strong>Validates: Requirements 2.6</strong></p>
 */
class SolutionListingScopePropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;
    private DomainService domainService;
    private SolutionService solutionService;
    private DomainRepository domainRepository;
    private SolutionRepository solutionRepository;

    @BeforeContainer
    static void startContainer() {
        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
        postgres.start();

        SpringApplication app = new SpringApplication(EnvironmentServiceApplication.class);
        app.setAdditionalProfiles("test");
        context = app.run(
            "--spring.datasource.url=" + postgres.getJdbcUrl(),
            "--spring.datasource.username=" + postgres.getUsername(),
            "--spring.datasource.password=" + postgres.getPassword(),
            "--spring.datasource.driver-class-name=org.postgresql.Driver",
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
            "--spring.flyway.enabled=true",
            "--spring.flyway.locations=classpath:db/migration",
            "--spring.flyway.baseline-on-migrate=true"
        );
    }

    @AfterContainer
    static void stopContainer() {
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeTry
    void setUp() {
        domainService = context.getBean(DomainService.class);
        solutionService = context.getBean(SolutionService.class);
        domainRepository = context.getBean(DomainRepository.class);
        solutionRepository = context.getBean(SolutionRepository.class);
        // Clean up before each try to avoid uniqueness conflicts
        solutionRepository.deleteAll();
        domainRepository.deleteAll();
    }

    /**
     * Property 9: Solution Listing Scoped to Domain
     *
     * <p><strong>Validates: Requirements 2.6</strong></p>
     *
     * <p>For any domainId, listing solutions via GET /{domainId}/solutions shall
     * return only solutions whose domainId matches the URL path parameter. We
     * create solutions across two distinct domains and verify that listing by
     * one domain returns only that domain's solutions.</p>
     */
    @Property(tries = 10)
    @Tag("domain-solution-url-prefix-property-9")
    void solutionListingScopedToDomain(
            @ForAll("validDomainIds") String domainIdA,
            @ForAll("validDomainIds") String domainIdB,
            @ForAll("solutionIdSets") Set<String> solutionIdsForA,
            @ForAll("solutionIdSets") Set<String> solutionIdsForB) {

        // Ensure the two domains are distinct
        Assume.that(!domainIdA.equals(domainIdB));
        // Ensure no overlap in solution IDs between domains
        Assume.that(solutionIdsForA.stream().noneMatch(solutionIdsForB::contains));

        // Create both domains
        domainService.createDomain(new DomainCreateRequest(domainIdA, "Domain A"));
        domainService.createDomain(new DomainCreateRequest(domainIdB, "Domain B"));

        // Create solutions in domain A
        for (String solId : solutionIdsForA) {
            solutionService.createSolution(new SolutionCreateRequest(domainIdA, solId, null));
        }

        // Create solutions in domain B
        for (String solId : solutionIdsForB) {
            solutionService.createSolution(new SolutionCreateRequest(domainIdB, solId, null));
        }

        // List solutions for domain A (simulates GET /{domainIdA}/solutions)
        List<SolutionDTO> listedForA = solutionService.listSolutionsByDomain(domainIdA);

        // All returned solutions must belong to domain A
        assertThat(listedForA)
            .as("All listed solutions must belong to the queried domain")
            .allMatch(s -> s.domainId().equals(domainIdA));

        // The count must match the number of solutions created in domain A
        Set<String> returnedSolutionIds = listedForA.stream()
            .map(SolutionDTO::solutionId)
            .collect(Collectors.toSet());

        assertThat(returnedSolutionIds)
            .as("Listed solutions must contain exactly the solutions created in domain A")
            .isEqualTo(solutionIdsForA);

        // No solutions from domain B should appear
        assertThat(listedForA)
            .as("No solutions from other domains should appear")
            .noneMatch(s -> s.domainId().equals(domainIdB));

        // Verify domain B listing is also correct
        List<SolutionDTO> listedForB = solutionService.listSolutionsByDomain(domainIdB);
        Set<String> returnedForB = listedForB.stream()
            .map(SolutionDTO::solutionId)
            .collect(Collectors.toSet());

        assertThat(returnedForB)
            .as("Domain B listing must contain exactly domain B solutions")
            .isEqualTo(solutionIdsForB);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> validDomainIds() {
        Arbitrary<Character> alphaNum = Arbitraries.chars()
            .range('a', 'z')
            .range('A', 'Z')
            .range('0', '9');

        Arbitrary<String> middle = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('_', '-')
            .ofMinLength(0)
            .ofMaxLength(20);

        Arbitrary<String> singleChar = alphaNum.map(String::valueOf);

        Arbitrary<String> multiChar = Combinators.combine(alphaNum, middle, alphaNum)
            .as((first, mid, last) -> first + mid + last);

        return Arbitraries.oneOf(singleChar, multiChar);
    }

    /**
     * Generates small sets of valid solution IDs (1-3 solutions per domain).
     */
    @Provide
    Arbitrary<Set<String>> solutionIdSets() {
        Arbitrary<String> solutionId = validSolutionId();
        return solutionId.set().ofMinSize(1).ofMaxSize(3);
    }

    private Arbitrary<String> validSolutionId() {
        Arbitrary<Character> alphaNum = Arbitraries.chars()
            .range('a', 'z')
            .range('A', 'Z')
            .range('0', '9');

        Arbitrary<String> middle = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('_', '-')
            .ofMinLength(0)
            .ofMaxLength(20);

        Arbitrary<String> singleChar = alphaNum.map(String::valueOf);

        Arbitrary<String> multiChar = Combinators.combine(alphaNum, middle, alphaNum)
            .as((first, mid, last) -> first + mid + last);

        return Arbitraries.oneOf(singleChar, multiChar);
    }
}
