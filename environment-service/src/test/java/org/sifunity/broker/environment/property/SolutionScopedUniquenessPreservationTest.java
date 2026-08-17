// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
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
import org.sifunity.broker.environment.exception.DuplicateProviderException;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.ProviderService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Preservation property tests for uniqueness fix.
 *
 * <p>These tests lock down existing correct behavior that must be preserved
 * after the fix is applied.</p>
 *
 * <p>Properties tested:</p>
 * <ul>
 *   <li>Same-environment duplicate provider rejection (Req 3.4)</li>
 *   <li>Session token global uniqueness (Req 3.2)</li>
 *   <li>Provider lookup correctness (Req 3.5)</li>
 * </ul>
 *
 * <p>Note: applicationName uniqueness is intentionally NOT tested here.
 * Per the SIF spec, consumerName is a descriptive label with no uniqueness requirement.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SolutionScopedUniquenessPreservationTest {

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

    private final Random random = new Random();

    // ========== Helper Methods ==========

    private String createDomain(String suffix) {
        String domainId = "pres-domain-" + suffix;
        domainService.createDomain(new DomainCreateRequest(domainId, null));
        return domainId;
    }

    private String createSolution(String domainId, String suffix) {
        String solutionId = "pres-sol-" + suffix;
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
        return solutionId;
    }

    private SifEnvironmentRegistrationResponse registerEnvironment(
            String domainId, String solutionId, String applicationName, EnvironmentType type) {
        return environmentService.registerEnvironment(
                domainId,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, null, solutionId, null)));
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

    private String createZoneWithContext(String domainId, String solutionId) {
        String zoneId = "zone-" + System.nanoTime() + "-" + random.nextInt(10000);
        zoneService.createZone(domainId, new org.sifunity.broker.environment.dto.ZoneCreateRequest(solutionId, zoneId, "Test zone"));
        return zoneId;
    }

    private String generateUniqueApplicationName() {
        String[] prefixes = {"PresApp", "PresSvc", "PresSystem", "PresTool"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        return prefix + "-" + System.nanoTime() + "-" + random.nextInt(10000);
    }

    private String generateValidServiceName() {
        String[] services = {"StudentSvc", "StaffSvc", "CourseSvc", "GradeSvc", "AttendanceSvc"};
        String base = services[random.nextInt(services.length)];
        return base + "-" + System.nanoTime();
    }

    private String generateValidZoneId() {
        String[] zones = {"production", "staging", "development", "test", "demo"};
        String base = zones[random.nextInt(zones.length)];
        return base + "-" + random.nextInt(100);
    }

    private String generateValidContextId() {
        String[] contexts = {"default", "primary", "secondary", "backup", "main"};
        String base = contexts[random.nextInt(contexts.length)];
        return base + "-" + random.nextInt(50);
    }

    private String generateValidEndpoint() {
        String[] hosts = {"api.example.com", "service.test.org", "provider.demo.net"};
        String host = hosts[random.nextInt(hosts.length)];
        int port = 8000 + random.nextInt(1000);
        return "https://" + host + ":" + port + "/api";
    }

    private List<SupportedRequestType> generateRandomSupportedRequestTypes() {
        List<SupportedRequestType> result = new java.util.ArrayList<>();
        result.add(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(generateRandomOperations())));
        if (random.nextBoolean()) {
            result.add(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(generateRandomOperations())));
        }
        return result;
    }

    private Set<Operation> generateRandomOperations() {
        Operation[] allOps = Operation.values();
        Set<Operation> ops = EnumSet.noneOf(Operation.class);
        ops.add(allOps[random.nextInt(allOps.length)]);
        for (Operation op : allOps) {
            if (random.nextBoolean()) {
                ops.add(op);
            }
        }
        return ops;
    }

    // ========== Preservation Property Tests ==========

    /**
     * Property 4 (Preservation): Same-Environment Duplicate Provider Rejection.
     *
     * <p>For any BROKERED environment and any valid (serviceName, zoneId, contextId)
     * tuple, registering a provider with that tuple and then attempting to register
     * the same tuple again in the same environment SHALL fail with
     * DuplicateProviderException.</p>
     *
     * <p>This behavior must be preserved before and after the fix.</p>
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     */
    @RepeatedTest(10)
    @Tag("preservation")
    void sameEnvironmentDuplicateProvider_isRejected() {
        // Setup: create a BROKERED environment
        String domainId = createDomain("dupprov-" + System.nanoTime());
        String solutionId = createSolution(domainId, "dupprov-" + System.nanoTime());
        SifEnvironmentRegistrationResponse env = registerEnvironment(
                domainId, solutionId, generateUniqueApplicationName(), EnvironmentType.BROKERED);

        // Create a zone with DEFAULT context for provider registration
        String zoneId = createZoneWithContext(domainId, solutionId);
        String contextId = "DEFAULT";

        // Generate random provider tuple
        String serviceName = generateValidServiceName();

        // First provider registration — should succeed
        ProviderRegistrationDTO first = registerProvider(domainId, env, serviceName, zoneId, contextId, solutionId);
        assertThat(first.id()).isNotNull();
        assertThat(first.serviceName()).isEqualTo(serviceName);

        // Second registration with SAME tuple in SAME environment — must be rejected
        assertThatThrownBy(() -> registerProvider(domainId, env, serviceName, zoneId, contextId, solutionId))
                .isInstanceOf(DuplicateProviderException.class)
                .hasMessageContaining(serviceName)
                .hasMessageContaining(zoneId);
    }

    /**
     * Preservation: Session Token Global Uniqueness.
     *
     * <p>Each environment registration must receive a globally unique sessionToken.
     * This property must hold regardless of the uniqueness fix — sessionTokens
     * are intentionally globally unique to avoid auth lookup confusion.</p>
     *
     * <p><strong>Validates: Requirements 3.2</strong></p>
     */
    @RepeatedTest(10)
    @Tag("preservation")
    void sessionTokens_areGloballyUnique() {
        // Create a solution and register multiple environments
        String domainId = createDomain("token-" + System.nanoTime());
        String solutionId = createSolution(domainId, "token-" + System.nanoTime());

        int envCount = 2 + random.nextInt(4); // 2-5 environments
        Set<String> sessionTokens = new HashSet<>();

        for (int i = 0; i < envCount; i++) {
            SifEnvironmentRegistrationResponse resp = registerEnvironment(
                    domainId, solutionId, generateUniqueApplicationName(), EnvironmentType.BROKERED);

            String token = resp.environment().sessionToken();
            assertThat(token)
                    .as("sessionToken for environment %d must not be null or blank", i)
                    .isNotNull()
                    .isNotBlank();

            boolean unique = sessionTokens.add(token);
            assertThat(unique)
                    .as("sessionToken '%s' for environment %d must be unique", token, i)
                    .isTrue();
        }

        assertThat(sessionTokens).hasSize(envCount);
    }

    /**
     * Preservation: Provider Lookup Correctness.
     *
     * <p>After registering a provider with a given (serviceName, zoneId, contextId)
     * tuple, looking up by that exact tuple must return the correct provider.
     * Looking up by a different tuple must return empty.</p>
     *
     * <p><strong>Validates: Requirements 3.5</strong></p>
     */
    @RepeatedTest(10)
    @Tag("preservation")
    void providerLookup_returnsCorrectResult() {
        // Setup: create a BROKERED environment and register a provider
        String domainId = createDomain("lookup-" + System.nanoTime());
        String solutionId = createSolution(domainId, "lookup-" + System.nanoTime());
        SifEnvironmentRegistrationResponse env = registerEnvironment(
                domainId, solutionId, generateUniqueApplicationName(), EnvironmentType.BROKERED);

        // Create a zone with DEFAULT context for provider registration
        String zoneId = createZoneWithContext(domainId, solutionId);
        String contextId = "DEFAULT";

        String serviceName = generateValidServiceName();

        ProviderRegistrationDTO registered = registerProvider(domainId, env, serviceName, zoneId, contextId, solutionId);

        // Lookup by exact tuple — must find the provider
        UUID lookupSolutionUuid = solutionService.getSolution(domainId, solutionId).id();
        Optional<ProviderRegistrationDTO> found = providerService.lookupProvider(
                lookupSolutionUuid, serviceName, zoneId, contextId);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(registered.id());
        assertThat(found.get().serviceName()).isEqualTo(serviceName);
        assertThat(found.get().zoneId()).isEqualTo(zoneId);
        assertThat(found.get().contextId()).isEqualTo(contextId);
        assertThat(ProtocolDTO.locationOf(found.get().endPoint())).isEqualTo(ProtocolDTO.locationOf(registered.endPoint()));

        // Lookup by non-existent service — must return empty
        Optional<ProviderRegistrationDTO> notFound = providerService.lookupProvider(
                lookupSolutionUuid, "NonExistent-" + System.nanoTime(), zoneId, contextId);
        assertThat(notFound).isEmpty();

        // Lookup by non-existent zone — must return empty
        Optional<ProviderRegistrationDTO> notFoundZone = providerService.lookupProvider(
                lookupSolutionUuid, serviceName, "nonexistent-zone-" + System.nanoTime(), contextId);
        assertThat(notFoundZone).isEmpty();

        // Lookup with mismatched contextId — must return empty
        Optional<ProviderRegistrationDTO> notFoundCtx = providerService.lookupProvider(
                lookupSolutionUuid, serviceName, zoneId, "some-context-" + System.nanoTime());
        assertThat(notFoundCtx).isEmpty();
    }
}
