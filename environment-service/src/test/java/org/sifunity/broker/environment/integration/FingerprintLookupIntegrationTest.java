// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the internal fingerprint lookup endpoint.
 *
 * <p>Tests the GET /internal/environments/by-fingerprint endpoint
 * and GET /internal/environments/{id} endpoint using Testcontainers
 * with a real PostgreSQL database.</p>
 *
 * <p><strong>Validates: Requirements 7.1, 7.2</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FingerprintLookupIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    private TestEntityHierarchy hierarchy;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("secrets.backend.type", () -> "plaintext");
        registry.add("broker.internal-service-token", () -> "test-internal-token");
    }

    @BeforeAll
    static void beforeAll() {
        assertThat(postgres.isRunning()).isTrue();
    }

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.create(domainService, solutionService);
    }

    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Test
    void fingerprintLookup_existingFingerprintAndSolution_returns200() throws Exception {
        // The SolutionService auto-creates a BROKER_SERVICES environment with
        // fingerprint "broker-services" for each solution, so we can query it directly.
        mockMvc.perform(get("/internal/environments/by-fingerprint")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .param("fingerprint", "broker-services")
                        .param("domainId", hierarchy.getDomainId())
                        .param("solutionId", hierarchy.getSolutionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fingerprint").value("broker-services"))
                .andExpect(jsonPath("$.solutionUuid").value(hierarchy.getSolutionUuid().toString()))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.applicationName").isNotEmpty());
    }

    @Test
    void fingerprintLookup_nonExistentFingerprint_returns404() throws Exception {
        mockMvc.perform(get("/internal/environments/by-fingerprint")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .param("fingerprint", "non-existent-fingerprint")
                        .param("domainId", hierarchy.getDomainId())
                        .param("solutionId", hierarchy.getSolutionId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void fingerprintLookup_wrongSolution_returns404() throws Exception {
        // Query with a non-existent solution ID → should not find it.
        mockMvc.perform(get("/internal/environments/by-fingerprint")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .param("fingerprint", "broker-services")
                        .param("domainId", hierarchy.getDomainId())
                        .param("solutionId", "non-existent-solution-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void environmentByIdLookup_existingId_returns200() throws Exception {
        // Create an environment
        Environment env = Environment.builder()
                .applicationName("lookup-by-id-" + System.nanoTime())
                .sessionToken("session-id-" + UUID.randomUUID())
                .sharedSecret("test-secret")
                .fingerprint("test-fp")
                .solutionUuid(hierarchy.getSolutionUuid())
                .type(EnvironmentType.BROKERED)
                .createdAt(Instant.now())
                .build();
        env = environmentRepository.save(env);

        mockMvc.perform(get("/internal/environments/{id}", env.getId())
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(env.getId().toString()))
                .andExpect(jsonPath("$.applicationName").value(env.getApplicationName()))
                .andExpect(jsonPath("$.fingerprint").value("test-fp"))
                .andExpect(jsonPath("$.solutionUuid").value(hierarchy.getSolutionUuid().toString()));
    }

    @Test
    void environmentByIdLookup_nonExistentId_returns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(get("/internal/environments/{id}", nonExistentId)
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andExpect(status().isNotFound());
    }
}
