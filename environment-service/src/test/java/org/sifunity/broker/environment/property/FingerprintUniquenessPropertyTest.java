// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.Arbitrary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 2: Fingerprint Uniqueness Invariant.
 *
 * <p>For any registered environment, the fingerprint value shall differ from
 * the environment's id (as string), sessionToken, userToken, and applicationKey.</p>
 *
 * <p>Tag: Feature: sif-environment-response, Property 2: Fingerprint Uniqueness Invariant</p>
 *
 * <p><strong>Validates: Requirements 2.2</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FingerprintUniquenessPropertyTest {

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    private String testSolutionId;

    private String testDomainId;

    @BeforeEach
    void setUp() {
        testDomainId = "fp-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(testDomainId, "Fingerprint test domain"));
        testSolutionId = "fp-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(testDomainId, testSolutionId, "Fingerprint test solution"));
    }

    @RepeatedTest(10)
    @Tag("Feature: sif-environment-response, Property 2: Fingerprint Uniqueness Invariant")
    void fingerprintDiffersFromIdSessionTokenUserTokenAndApplicationKey() {
        Arbitrary<EnvironmentRegistrationRequest> requests =
                SifEnvironmentResponseArbitraries.validRegistrationRequests(testSolutionId);
        EnvironmentRegistrationRequest request = requests.sample();

        SifEnvironmentRegistrationResponse regResponse = environmentService.registerEnvironment(testDomainId, request);
        var env = regResponse.environment();

        String fingerprint = env.fingerprint();
        assertThat(fingerprint).isNotNull().isNotBlank();

        // fingerprint != id
        assertThat(fingerprint).isNotEqualTo(env.id().toString());

        // fingerprint != sessionToken
        assertThat(fingerprint).isNotEqualTo(env.sessionToken());

        // fingerprint != userToken (if present)
        if (env.userToken() != null) {
            assertThat(fingerprint).isNotEqualTo(env.userToken());
        }

        // fingerprint != applicationKey (if present)
        if (env.applicationInfo() != null && env.applicationInfo().applicationKey() != null) {
            assertThat(fingerprint).isNotEqualTo(env.applicationInfo().applicationKey());
        }
    }
}
