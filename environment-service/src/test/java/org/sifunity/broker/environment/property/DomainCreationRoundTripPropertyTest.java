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
import org.sifunity.broker.environment.dto.DomainDTO;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.service.DomainService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Property-based test for Domain creation round-trip using jqwik with
 * {@code @SpringBootTest} semantics via Testcontainers PostgreSQL.
 *
 * <p><strong>Property 2: Domain Creation Round-Trip</strong></p>
 *
 * <p>For any valid domain_id and description, after creating a domain and then
 * retrieving it, the returned domain should contain equivalent data (same domain_id,
 * description, and a valid created_at timestamp).</p>
 *
 * <p><strong>Validates: Requirements 1.5, 1.6, 1.7</strong></p>
 *
 * <p>This test uses jqwik arbitraries to generate random valid domain IDs and
 * descriptions, persists them to a real PostgreSQL database via Testcontainers,
 * and verifies the round-trip property holds across many inputs.</p>
 */
class DomainCreationRoundTripPropertyTest {

    static PostgreSQLContainer<?> postgres;
    static ConfigurableApplicationContext context;
    private DomainService domainService;
    private DomainRepository domainRepository;

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
        domainRepository = context.getBean(DomainRepository.class);
        // Clean up domains before each try to avoid uniqueness conflicts
        domainRepository.deleteAll();
    }

    /**
     * Property 2: Domain Creation Round-Trip
     *
     * <p><strong>Validates: Requirements 1.5, 1.6, 1.7</strong></p>
     *
     * <p>For any valid domain_id and optional description, creating a domain and
     * then retrieving it by domain_id returns equivalent data.</p>
     */
    @Property(tries = 10)
    @Tag("domain-solution-hierarchy-property-2")
    void domainCreationRoundTrip(
            @ForAll("validDomainIds") String domainId,
            @ForAll("optionalDescriptions") String description) {

        Instant beforeCreation = Instant.now();

        DomainCreateRequest request = new DomainCreateRequest(domainId, description);

        // Requirement 1.5: Store domain_id, description, and created_at
        DomainDTO created = domainService.createDomain(request);

        Instant afterCreation = Instant.now();

        // Verify created response
        assertThat(created.id()).as("UUID must be generated").isNotNull();
        assertThat(created.domainId()).as("domainId must match request").isEqualTo(domainId);
        assertThat(created.description()).as("description must match request").isEqualTo(description);

        // Requirement 1.6: created_at generated automatically
        assertThat(created.createdAt())
            .as("createdAt must be set automatically")
            .isNotNull()
            .isBetween(beforeCreation.minusSeconds(1), afterCreation.plusSeconds(1));

        // Requirement 1.7: Retrieve domain by domain_id
        DomainDTO retrieved = domainService.getDomain(domainId);

        // Round-trip: all fields must match
        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.domainId()).isEqualTo(created.domainId());
        assertThat(retrieved.description()).isEqualTo(created.description());
        // PostgreSQL stores timestamps at microsecond precision, so nanosecond
        // differences from Java's Instant are expected after a round-trip.
        assertThat(retrieved.createdAt())
            .as("createdAt must survive round-trip (microsecond precision)")
            .isCloseTo(created.createdAt(), within(1, java.time.temporal.ChronoUnit.MICROS));
    }

    // ========== Arbitraries ==========

    /**
     * Generates valid domain IDs matching the naming pattern:
     * starts with alphanumeric, contains alphanumeric, underscore, or hyphen,
     * ends with alphanumeric.
     */
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

        // Single char IDs are valid too
        Arbitrary<String> singleChar = alphaNum.map(String::valueOf);

        // Multi-char IDs: alphaNum + middle + alphaNum
        Arbitrary<String> multiChar = Combinators.combine(alphaNum, middle, alphaNum)
            .as((first, mid, last) -> first + mid + last);

        return Arbitraries.oneOf(singleChar, multiChar);
    }

    /**
     * Generates optional descriptions (null or non-empty string).
     */
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
