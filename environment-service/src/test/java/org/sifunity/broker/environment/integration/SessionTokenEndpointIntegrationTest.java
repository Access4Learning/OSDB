// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for GET /internal/environments/{id}/session-token endpoint.
 *
 * <p>Uses full Spring Boot context with Testcontainers PostgreSQL (configured via
 * application-test.yml JDBC URL). Creates entities directly via repositories and
 * verifies the endpoint returns the correct session token.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.5</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class SessionTokenEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DomainRepository domainRepository;

    @Autowired
    private SolutionRepository solutionRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    private Environment testEnvironment;

    @BeforeEach
    void setUp() {
        // Create domain → solution → environment hierarchy
        Domain domain = new Domain();
        domain.setDomainId("inttest-domain-" + System.currentTimeMillis());
        domain.setDescription("Integration test domain");
        domain = domainRepository.saveAndFlush(domain);

        Solution solution = new Solution();
        solution.setDomainUuid(domain.getId());
        solution.setSolutionId("inttest-solution-" + System.currentTimeMillis());
        solution.setDescription("Integration test solution");
        solution = solutionRepository.saveAndFlush(solution);

        testEnvironment = Environment.builder()
                .applicationName("session-token-test-app")
                .sessionToken("test-session-token-" + UUID.randomUUID())
                .sharedSecret("dGVzdC1zaGFyZWQtc2VjcmV0")
                .type(EnvironmentType.BROKERED)
                .solutionUuid(solution.getId())
                .build();
        testEnvironment = environmentRepository.saveAndFlush(testEnvironment);
    }

    /**
     * Valid UUID returns session token as plain text with 200 OK.
     * Validates: Requirement 1.1, 1.5
     */
    @Test
    void getSessionToken_existingEnvironment_returnsTokenAsPlainText() throws Exception {
        mockMvc.perform(get("/internal/environments/{id}/session-token", testEnvironment.getId())
                        .header("X-Internal-Service-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(testEnvironment.getSessionToken()));
    }

    /**
     * Non-existent UUID returns 404.
     * Validates: Requirement 1.2
     */
    @Test
    void getSessionToken_nonExistentUuid_returns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/internal/environments/{id}/session-token", nonExistentId)
                        .header("X-Internal-Service-Token", "test-internal-token"))
                .andExpect(status().isNotFound());
    }

    /**
     * Malformed UUID path returns 400.
     * Validates: Requirement 1.3
     */
    @Test
    void getSessionToken_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/internal/environments/{id}/session-token", "not-a-uuid")
                        .header("X-Internal-Service-Token", "test-internal-token"))
                .andExpect(status().isBadRequest());
    }
}
