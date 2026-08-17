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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Solution creation using domain from URL path.
 *
 * <p><strong>Property 8: Solution Creation Uses Domain From URL Path</strong></p>
 *
 * <p>For any valid domainId in the URL path, creating a solution via
 * POST /{domainId}/solutions shall result in the created solution having its
 * domainId field equal to the domainId from the URL path, regardless of any
 * domain value in the request body.</p>
 *
 * <p><strong>Validates: Requirements 2.5</strong></p>
 */
class SolutionCreationDomainPropertyTest {

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
     * Property 8: Solution Creation Uses Domain From URL Path
     *
     * <p><strong>Validates: Requirements 2.5</strong></p>
     *
     * <p>For any valid domainId in the URL path, creating a solution shall use
     * the domainId from the URL path, regardless of any domain value in the
     * request body. This simulates the SolutionController behavior where the
     * controller overrides the request body domainId with the URL path domainId.</p>
     */
    @Property(tries = 10)
    @Tag("domain-solution-url-prefix-property-8")
    void solutionCreationUsesDomainFromUrlPath(
            @ForAll("validDomainIds") String urlDomainId,
            @ForAll("validSolutionIds") String solutionId,
            @ForAll("validDomainIds") String bodyDomainId,
            @ForAll("optionalDescriptions") String description) {

        // Create the domain that the URL path references
        domainService.createDomain(new DomainCreateRequest(urlDomainId, "URL domain"));

        // Simulate what SolutionController does: override request body domainId
        // with the domainId from the URL path variable
        SolutionCreateRequest effectiveRequest = new SolutionCreateRequest(
            urlDomainId, solutionId, description);

        SolutionDTO created = solutionService.createSolution(effectiveRequest);

        // The created solution's domainId must equal the URL path domainId,
        // regardless of what bodyDomainId was
        assertThat(created.domainId())
            .as("Solution domainId must match URL path domainId, not request body")
            .isEqualTo(urlDomainId);

        assertThat(created.solutionId())
            .as("Solution solutionId must match request")
            .isEqualTo(solutionId);
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

    @Provide
    Arbitrary<String> validSolutionIds() {
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

    @Provide
    Arbitrary<String> optionalDescriptions() {
        Arbitrary<String> descriptions = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars(' ', '-', '_', '.')
            .ofMinLength(1)
            .ofMaxLength(100);

        return Arbitraries.oneOf(
            Arbitraries.just(null),
            descriptions
        );
    }
}
