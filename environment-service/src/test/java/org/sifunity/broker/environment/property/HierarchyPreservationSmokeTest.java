// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.DomainDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.SolutionDTO;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;

/**
 * Preservation smoke test: verifies the "happy path" that already works
 * (Domain → Solution → Environment) continues working after bugfix changes.
 *
 * <p><strong>Property 2: Preservation</strong> — Already-Passing Tests Remain Unaffected</p>
 *
 * <p>Creates a Domain, then a Solution (proper hierarchy), then registers
 * an Environment, and verifies the environment is retrievable with the
 * correct solutionId.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HierarchyPreservationSmokeTest {

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private EnvironmentService environmentService;

    /**
     * Verifies that creating a Domain → Solution → Environment hierarchy
     * works end-to-end via the service layer, and the environment is
     * retrievable with the correct solutionId.
     */
    @Test
    @Tag("preservation-smoke")
    void domainSolutionEnvironmentHierarchy_happyPath() {
        // 1. Create a Domain
        String domainId = "preserve-domain-" + System.nanoTime();
        DomainDTO domain = domainService.createDomain(
                new DomainCreateRequest(domainId, "Preservation test domain"));
        assertThat(domain.id()).isNotNull();
        assertThat(domain.domainId()).isEqualTo(domainId);

        // 2. Create a Solution under that Domain
        String solutionId = "preserve-solution-" + System.nanoTime();
        SolutionDTO solution = solutionService.createSolution(
                new SolutionCreateRequest(domainId, solutionId, "Preservation test solution"));
        assertThat(solution.id()).isNotNull();
        assertThat(solution.solutionId()).isEqualTo(solutionId);
        assertThat(solution.domainId()).isEqualTo(domainId);

        // 3. Register an Environment referencing that Solution
        String appName = "preserve-app-" + System.nanoTime();
        SifEnvironmentRegistrationResponse registration =
                environmentService.registerEnvironment(domainId, new EnvironmentRegistrationRequest(
                        new EnvironmentRegistrationBody(appName, EnvironmentType.DIRECT, null, solutionId, null)));

        assertThat(registration).isNotNull();
        assertThat(registration.environment()).isNotNull();
        assertThat(registration.environment().solutionId()).isEqualTo(solutionId);
        assertThat(registration.environment().id()).isNotNull();
        assertThat(registration.sharedSecret()).isNotBlank();

        // 4. Retrieve the environment and verify solutionId
        SifEnvironmentResponse retrieved =
                environmentService.getEnvironment(registration.environment().id());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.solutionId()).isEqualTo(solutionId);
        assertThat(retrieved.type()).isEqualTo(EnvironmentType.DIRECT);

        // 5. List environments by solution and verify our environment is present
        List<SifEnvironmentResponse> envsBySolution =
                environmentService.listEnvironmentsBySolution(domainId, solutionId);
        assertThat(envsBySolution)
                .extracting(SifEnvironmentResponse::id)
                .contains(registration.environment().id());
    }
}
