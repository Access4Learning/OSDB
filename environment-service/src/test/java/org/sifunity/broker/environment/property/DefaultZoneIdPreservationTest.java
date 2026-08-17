// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.service.EnvironmentResponseMapper;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.QueueServiceClient;
import org.springframework.cache.CacheManager;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Preservation Property Tests for DefaultZoneId validation fix.
 *
 * <p><strong>CRITICAL: These tests MUST PASS on unfixed code.</strong>
 * They capture baseline behavior that must remain unchanged after
 * the defaultZoneId validation fix is applied.</p>
 *
 * <p>Preservation scope: all requests where {@code NOT isBugCondition(input)} —
 * i.e., zoneId is null, empty, or exists in solution.</p>
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>registerEnvironment() with null defaultZoneId succeeds</li>
 *   <li>registerEnvironment() with empty/blank defaultZoneId succeeds</li>
 *   <li>registerEnvironment() with a valid defaultZoneId matching a zone in the solution succeeds</li>
 *   <li>updateEnvironment() changing only consumerName (no defaultZone field) succeeds without zone validation</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3</strong></p>
 */
class DefaultZoneIdPreservationTest {

    private static final String DOMAIN_ID = "test-domain";
    private static final UUID DOMAIN_UUID = UUID.randomUUID();

    private EnvironmentService createService(SolutionRepository solutionRepository,
                                             EnvironmentRepository environmentRepository) {
        return createService(solutionRepository, environmentRepository, mock(ZoneRepository.class));
    }

    private EnvironmentService createService(SolutionRepository solutionRepository,
                                             EnvironmentRepository environmentRepository,
                                             ZoneRepository zoneRepository) {
        DomainRepository domainRepository = mock(DomainRepository.class);
        Domain domain = new Domain();
        domain.setId(DOMAIN_UUID);
        domain.setDomainId(DOMAIN_ID);
        when(domainRepository.findByDomainId(anyString())).thenReturn(Optional.of(domain));
        AclEntryRepository aclEntryRepository = mock(AclEntryRepository.class);
        SecretsBackend secretsBackend = mock(SecretsBackend.class);
        EnvironmentResponseMapper mapper = mock(EnvironmentResponseMapper.class);
        CacheManager cacheManager = mock(CacheManager.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);

        // Mock mapper to return a valid response
        when(mapper.toRegistrationResponse(any(Environment.class), anyString()))
                .thenReturn(SifEnvironmentRegistrationResponse.of(
                        mock(SifEnvironmentResponse.class), "mock-secret"));
        when(mapper.toEnvironmentResponse(any(Environment.class)))
                .thenReturn(mock(SifEnvironmentResponse.class));

        // Mock environment save to return the entity with an ID
        when(environmentRepository.save(any(Environment.class))).thenAnswer(invocation -> {
            Environment env = invocation.getArgument(0);
            if (env.getId() == null) {
                // Simulate JPA UUID generation for new entities
                env.setId(UUID.randomUUID());
            }
            return env;
        });

        return new EnvironmentService(
                environmentRepository,
                solutionRepository,
                domainRepository,
                aclEntryRepository,
                secretsBackend,
                mapper,
                cacheManager,
                queueServiceClient,
                zoneRepository);
    }

    private SolutionRepository mockSolutionRepository() {
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        Solution mockSolution = mock(Solution.class);
        when(mockSolution.getId()).thenReturn(UUID.randomUUID());
        when(solutionRepository.findByDomainUuidAndSolutionId(any(UUID.class), anyString()))
                .thenReturn(Optional.of(mockSolution));
        return solutionRepository;
    }

    // ========== Property 1: Null defaultZoneId preserved ==========

    /**
     * Property: Preservation — registerEnvironment() with null defaultZoneId succeeds.
     *
     * <p>For any registration request where defaultZoneId is null, the service
     * must not throw an exception. Zone is optional.</p>
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: registerEnvironment with null defaultZoneId succeeds")
    void registerWithNullDefaultZoneIdSucceeds(
            @ForAll("applicationNames") String appName,
            @ForAll("environmentTypes") EnvironmentType type) {

        SolutionRepository solutionRepository = mockSolutionRepository();
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        EnvironmentService service = createService(solutionRepository, environmentRepository);

        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, type, null, "test-solution", null));

        assertThatCode(() -> service.registerEnvironment(DOMAIN_ID, request))
                .doesNotThrowAnyException();
    }

    // ========== Property 2: Empty/blank defaultZoneId preserved ==========

    /**
     * Property: Preservation — registerEnvironment() with empty/blank defaultZoneId succeeds.
     *
     * <p>For any registration request where defaultZoneId is empty or blank,
     * the service must not throw an exception. Zone is optional.</p>
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: registerEnvironment with empty/blank defaultZoneId succeeds")
    void registerWithEmptyDefaultZoneIdSucceeds(
            @ForAll("applicationNames") String appName,
            @ForAll("environmentTypes") EnvironmentType type,
            @ForAll("emptyOrBlankStrings") String emptyZoneId) {

        SolutionRepository solutionRepository = mockSolutionRepository();
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        EnvironmentService service = createService(solutionRepository, environmentRepository);

        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, type, DefaultZoneDTO.of(emptyZoneId), "test-solution", null));

        assertThatCode(() -> service.registerEnvironment(DOMAIN_ID, request))
                .doesNotThrowAnyException();
    }

    // ========== Property 3: Valid defaultZoneId preserved ==========

    /**
     * Property: Preservation — registerEnvironment() with valid defaultZoneId succeeds.
     *
     * <p>For any registration request where defaultZoneId matches a zone in the solution,
     * the service must succeed (store value and return success).</p>
     *
     * <p><strong>Validates: Requirements 3.2</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: registerEnvironment with valid defaultZoneId succeeds")
    void registerWithValidDefaultZoneIdSucceeds(
            @ForAll("applicationNames") String appName,
            @ForAll("environmentTypes") EnvironmentType type,
            @ForAll("validZoneIds") String validZoneId) {

        SolutionRepository solutionRepository = mockSolutionRepository();
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);

        // Mock ZoneRepository to return true — simulates a valid zone existing in the solution
        ZoneRepository zoneRepository = mock(ZoneRepository.class);
        when(zoneRepository.existsBySolutionUuidAndZoneId(any(UUID.class), anyString())).thenReturn(true);

        EnvironmentService service = createService(solutionRepository, environmentRepository, zoneRepository);

        // After the fix, ZoneRepository.existsBySolutionUuidAndZoneId is called
        // and must return true for valid zone IDs to pass validation.
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appName, type, DefaultZoneDTO.of(validZoneId), "test-solution", null));

        assertThatCode(() -> service.registerEnvironment(DOMAIN_ID, request))
                .doesNotThrowAnyException();
    }

    // ========== Property 4: Update without defaultZone field preserved ==========

    /**
     * Property: Preservation — updateEnvironment() without defaultZone field succeeds.
     *
     * <p>For any update request that changes only consumerName (no defaultZone field),
     * no zone validation is triggered and the update succeeds.</p>
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: updateEnvironment without defaultZone field succeeds")
    void updateWithoutDefaultZoneFieldSucceeds(
            @ForAll("applicationNames") String consumerName) {

        SolutionRepository solutionRepository = mockSolutionRepository();
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);

        // Mock an existing environment for update
        Environment existingEnv = new Environment();
        existingEnv.setId(UUID.randomUUID());
        existingEnv.setSolutionUuid(UUID.randomUUID());
        existingEnv.setApplicationName("existing-app");
        existingEnv.setConsumerName("existing-consumer");
        existingEnv.setDefaultZoneId("existing-zone");
        existingEnv.setSessionToken(UUID.randomUUID().toString());

        when(environmentRepository.findByIdWithAclEntries(any(UUID.class)))
                .thenReturn(Optional.of(existingEnv));

        EnvironmentService service = createService(solutionRepository, environmentRepository);

        // Update with no defaultZone field (null defaultZone in request)
        EnvironmentUpdateRequest request = new EnvironmentUpdateRequest(
                consumerName, null, null, null, null, null);

        assertThatCode(() -> service.updateEnvironment(existingEnv.getId(), request))
                .doesNotThrowAnyException();
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> applicationNames() {
        return Arbitraries.of(
                "StudentInfoSystem", "LMS-Platform", "Assessment-Tool",
                "Gradebook-Pro", "Attendance-Tracker");
    }

    @Provide
    Arbitrary<EnvironmentType> environmentTypes() {
        return Arbitraries.of(EnvironmentType.values());
    }

    @Provide
    Arbitrary<String> emptyOrBlankStrings() {
        return Arbitraries.of("", " ", "  ", "\t", "\n", "   ");
    }

    @Provide
    Arbitrary<String> validZoneIds() {
        return Arbitraries.of(
                "production-zone", "staging-zone", "dev-zone",
                "test-zone", "sandbox-zone");
    }
}
