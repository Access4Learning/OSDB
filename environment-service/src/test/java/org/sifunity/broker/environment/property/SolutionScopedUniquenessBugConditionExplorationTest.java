// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.ProviderService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Bug condition exploration test for uniqueness constraints.
 *
 * <p>This test demonstrates bugs where global uniqueness constraints on
 * {@code environments.application_name} and {@code provider_registry.(service_name, zone_id, context_id)}
 * incorrectly reject registrations that should succeed.</p>
 *
 * <p>Per the SIF spec, {@code consumerName} (mapped from applicationName) is a descriptive label
 * with no uniqueness requirement. The SIF 409 rule applies to {@code applicationKey}, not
 * {@code consumerName}. The global UNIQUE constraint on application_name was incorrect.</p>
 *
 * <p>Provider registration uniqueness should be scoped per-environment, not global.</p>
 *
 * <p><strong>EXPECTED OUTCOME ON UNFIXED CODE:</strong> These tests FAIL, confirming the bugs exist.
 * The tests encode the expected (correct) behavior — they will PASS after the fix is applied.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SolutionScopedUniquenessBugConditionExplorationTest {

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private org.sifunity.broker.environment.service.ContextServiceService contextServiceService;

    // ========== Helper Methods ==========

    private String createDomain(String suffix) {
        String domainId = "bug-domain-" + suffix;
        domainService.createDomain(new DomainCreateRequest(domainId, null));
        return domainId;
    }

    private String createSolution(String domainId, String suffix) {
        String solutionId = "bug-sol-" + suffix;
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
        return solutionId;
    }

    private String createZoneForSolution(String domainId, String solutionId) {
        String zoneId = "bug-zone-" + System.nanoTime();
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone"));
        return zoneId;
    }

    private SifEnvironmentRegistrationResponse registerEnvironment(
            String domainId, String solutionId, String applicationName, EnvironmentType type, String zoneId) {
        return environmentService.registerEnvironment(
                domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, DefaultZoneDTO.of(zoneId), solutionId, null)));
    }

    private ProviderRegistrationDTO registerProvider(
            String domainId, SifEnvironmentRegistrationResponse env, String serviceName, String zoneId, String contextId, String solutionId) {
        // Resolve context_service UUID
        java.util.List<org.sifunity.broker.environment.dto.ContextDTO> contexts = zoneService.listContexts(domainId, solutionId, zoneId);
        org.sifunity.broker.environment.dto.ContextDTO ctx = contexts.stream()
                .filter(c -> contextId.equals(c.contextId()))
                .findFirst()
                .orElse(contexts.get(0));
        java.util.UUID contextServiceUuid = contextServiceService.resolveOrCreate(
                ctx.id(), serviceName, "OBJECT");
        
        return providerService.registerProvider(
                domainId,
                solutionId,
                new ProviderRegistrationRequest(new ProviderRegistrationBody(
                env.environment().id(),
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of("https://provider.example.com/api"),
                SupportedRequestsDTO.of(List.of(new SupportedRequestType( RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY))))))));
    }

    // ========== Bug Condition Tests ==========

    /**
     * Property 1 (Bug Condition): Cross-Solution Environment Registration Succeeds.
     *
     * <p>Two environments in different solutions register with the same applicationName.
     * Both should succeed because applicationName uniqueness should be scoped per-solution.</p>
     *
     * <p>On UNFIXED code, the second registration throws {@code DuplicateApplicationNameException}
     * because {@code existsByApplicationName()} checks globally instead of per-solution.</p>
     *
     * <p><strong>Validates: Requirements 2.1, 2.2</strong></p>
     */
    @Test
    @Tag("bug-condition-exploration")
    void crossSolutionEnvironmentRegistration_sameName_shouldBothSucceed() {
        // Setup: two distinct solutions under the same domain
        String domainId = createDomain("envtest-" + System.nanoTime());
        String solutionA = createSolution(domainId, "solA-" + System.nanoTime());
        String solutionB = createSolution(domainId, "solB-" + System.nanoTime());

        String zoneA = createZoneForSolution(domainId, solutionA);
        String zoneB = createZoneForSolution(domainId, solutionB);

        String sharedAppName = "TestApp-" + System.nanoTime();

        // First registration in solutionA — should succeed
        SifEnvironmentRegistrationResponse envA = registerEnvironment(
                domainId, solutionA, sharedAppName, EnvironmentType.BROKERED, zoneA);
        assertThat(envA.environment().id()).isNotNull();
        assertThat(envA.environment().consumerName()).isEqualTo(sharedAppName);

        // Second registration in solutionB with SAME applicationName — should also succeed
        // BUG: On unfixed code, this throws DuplicateApplicationNameException
        SifEnvironmentRegistrationResponse envB = registerEnvironment(
                domainId, solutionB, sharedAppName, EnvironmentType.BROKERED, zoneB);
        assertThat(envB.environment().id()).isNotNull();
        assertThat(envB.environment().consumerName()).isEqualTo(sharedAppName);

        // Verify both environments exist with different IDs
        assertThat(envA.environment().id()).isNotEqualTo(envB.environment().id());
    }

    /**
     * Property 1 (Bug Condition): Cross-Environment Provider Registration Succeeds.
     *
     * <p>Two provider registrations in different BROKERED environments register with the same
     * (serviceName, zoneId, contextId) tuple. Both should succeed because provider uniqueness
     * should be scoped per-environment.</p>
     *
     * <p>On UNFIXED code, the second registration throws {@code DuplicateProviderException}
     * because {@code existsByServiceNameAndZoneIdAndContextId()} checks globally.</p>
     *
     * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
     */
    @Test
    @Tag("bug-condition-exploration")
    void crossEnvironmentProviderRegistration_sameTuple_shouldBothSucceed() {
        // Setup: two distinct BROKERED environments in different solutions
        String domainId = createDomain("provtest-" + System.nanoTime());
        String solutionA = createSolution(domainId, "provSolA-" + System.nanoTime());
        String solutionB = createSolution(domainId, "provSolB-" + System.nanoTime());

        String zoneA = createZoneForSolution(domainId, solutionA);
        String zoneB = createZoneForSolution(domainId, solutionB);

        SifEnvironmentRegistrationResponse envA = registerEnvironment(
                domainId, solutionA, "ProviderAppA-" + System.nanoTime(), EnvironmentType.BROKERED, zoneA);
        SifEnvironmentRegistrationResponse envB = registerEnvironment(
                domainId, solutionB, "ProviderAppB-" + System.nanoTime(), EnvironmentType.BROKERED, zoneB);

        String sharedServiceName = "students";
        String sharedContextId = "DEFAULT";

        // First provider registration in envA — should succeed
        ProviderRegistrationDTO provA = registerProvider(domainId, envA, sharedServiceName, zoneA, sharedContextId, solutionA);
        assertThat(provA.id()).isNotNull();
        assertThat(provA.serviceName()).isEqualTo(sharedServiceName);

        // Second provider registration in envB with SAME service name — should also succeed
        // BUG: On unfixed code, this throws DuplicateProviderException
        ProviderRegistrationDTO provB = registerProvider(domainId, envB, sharedServiceName, zoneB, sharedContextId, solutionB);
        assertThat(provB.id()).isNotNull();
        assertThat(provB.serviceName()).isEqualTo(sharedServiceName);

        // Verify both registrations exist with different IDs
        assertThat(provA.id()).isNotEqualTo(provB.id());
    }
}
