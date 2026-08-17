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
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1: Registration-Retrieval Round-Trip.
 *
 * <p>For any valid registration request, register then retrieve should produce
 * matching fields (excluding sharedSecret).</p>
 *
 * <p>Tag: Feature: sif-environment-response, Property 1: Registration-Retrieval Round-Trip</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.3, 1.4, 2.1, 2.3, 2.4, 3.1, 3.2, 3.3,
 * 4.1, 4.2, 4.3, 4.5, 4.6, 5.1, 5.2, 5.3, 9.2, 9.3, 12.1</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnvironmentResponseRoundTripPropertyTest {

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    private String testDomainId;
    private String testSolutionId;

    @BeforeEach
    void setUp() {
        testDomainId = "rrt-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(testDomainId, "Round-trip test domain"));
        testSolutionId = "rrt-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(testDomainId, testSolutionId, "Round-trip test solution"));
    }

    /**
     * Property 1: For any valid registration request, register then retrieve
     * should produce matching fields (excluding sharedSecret).
     */
    @RepeatedTest(10)
    @Tag("Feature: sif-environment-response, Property 1: Registration-Retrieval Round-Trip")
    void registerThenRetrieveProducesMatchingFields() {
        Arbitrary<EnvironmentRegistrationRequest> requests =
                SifEnvironmentResponseArbitraries.validRegistrationRequests(testSolutionId);
        EnvironmentRegistrationRequest request = requests.sample();

        // Register
        SifEnvironmentRegistrationResponse regResponse = environmentService.registerEnvironment(testDomainId, request);
        var regEnv = regResponse.environment();

        // Retrieve
        SifEnvironmentResponse retrieved = environmentService.getEnvironment(regEnv.id());

        // Core fields must match
        assertThat(retrieved.id()).isEqualTo(regEnv.id());
        assertThat(retrieved.type()).isEqualTo(regEnv.type());
        assertThat(retrieved.fingerprint()).isEqualTo(regEnv.fingerprint());
        assertThat(retrieved.sessionToken()).isEqualTo(regEnv.sessionToken());
        assertThat(retrieved.solutionId()).isEqualTo(regEnv.solutionId());
        assertThat(retrieved.authenticationMethod()).isEqualTo(regEnv.authenticationMethod());
        assertThat(retrieved.consumerName()).isEqualTo(regEnv.consumerName());

        // defaultZone — D3: the key is OMITTED when the environment has no default zone. The schema
        // types defaultZone.id as a string and does not declare it nullable, so the previous
        // {"defaultZone": {"id": null}} was non-conformant. Asserted conditionally on the input so
        // this covers both directions rather than hardcoding the no-zone case.
        if (request.environment().defaultZoneId() == null) {
            assertThat(retrieved.defaultZone())
                    .as("with no default zone the key must be absent entirely, not present-and-null")
                    .isNull();
        } else {
            assertThat(retrieved.defaultZone()).isNotNull();
            assertThat(DefaultZoneDTO.idOf(retrieved.defaultZone())).isEqualTo(request.environment().defaultZoneId());
        }

        // applicationInfo round-trip
        if (request.environment().applicationInfo() != null) {
            ApplicationInfoDTO ai = request.environment().applicationInfo();
            // Mapper returns null when ALL appinfo fields are null
            boolean allFieldsNull = ai.applicationKey() == null
                    && ai.supportedInfrastructureVersion() == null
                    && ai.dataModelNamespace() == null
                    && ai.transport() == null
                    && ai.applicationProduct() == null
                    && ai.adapterProduct() == null;
            if (allFieldsNull) {
                // Mapper correctly returns null for all-null applicationInfo
                return;
            }
            assertThat(retrieved.applicationInfo()).isNotNull();
            ApplicationInfoDTO retAi = retrieved.applicationInfo();
            assertThat(retAi.applicationKey()).isEqualTo(ai.applicationKey());
            assertThat(retAi.supportedInfrastructureVersion()).isEqualTo(ai.supportedInfrastructureVersion());
            assertThat(retAi.dataModelNamespace()).isEqualTo(ai.dataModelNamespace());
            assertThat(retAi.transport()).isEqualTo(ai.transport());

            assertProductIdentityMatches(ai.applicationProduct(), retAi.applicationProduct());
            assertProductIdentityMatches(ai.adapterProduct(), retAi.adapterProduct());
        }

        // infrastructureServices present on both
        assertThat(retrieved.infrastructureServices()).isNotNull();
        assertThat(retrieved.infrastructureServices().infrastructureService()).hasSize(8);
    }

    private void assertProductIdentityMatches(ProductIdentityDTO expected, ProductIdentityDTO actual) {
        if (expected == null) {
            return;
        }
        if (actual == null) {
            // Mapper returns null when all fields are null
            assertThat(expected.vendorName()).isNull();
            assertThat(expected.productName()).isNull();
            assertThat(expected.productVersion()).isNull();
            assertThat(expected.iconURI()).isNull();
            return;
        }
        assertThat(actual.vendorName()).isEqualTo(expected.vendorName());
        assertThat(actual.productName()).isEqualTo(expected.productName());
        assertThat(actual.productVersion()).isEqualTo(expected.productVersion());
        assertThat(actual.iconURI()).isEqualTo(expected.iconURI());
    }
}
