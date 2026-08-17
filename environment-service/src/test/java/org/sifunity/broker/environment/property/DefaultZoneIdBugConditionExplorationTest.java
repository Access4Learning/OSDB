// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.EnvironmentUpdateRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;

/**
 * Bug Condition Exploration Test for DefaultZoneId Validation.
 *
 * <p><strong>CRITICAL: These tests MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>Bug: {@code EnvironmentService.registerEnvironment()} and {@code updateEnvironment()} store
 * arbitrary {@code defaultZoneId} values without checking that they correspond to an actual zone
 * in the solution. No call to {@code ZoneRepository.existsBySolutionUuidAndZoneId()} is made.</p>
 *
 * <p>On UNFIXED code: operations succeed, storing invalid zone IDs without error.</p>
 * <p>On FIXED code: operations throw {@code IllegalArgumentException} for invalid zone IDs.</p>
 *
 * <p>Bug Condition: {@code isBugCondition(input) = zoneId IS NOT NULL AND zoneId IS NOT EMPTY
 * AND NOT existsInSolution(solutionUuid, zoneId)}</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 2.1, 2.2</strong></p>
 */
class DefaultZoneIdBugConditionExplorationTest {

    private static final String DOMAIN_ID = "test-domain";

    // ========== Property 1: registerEnvironment with invalid defaultZoneId ==========

    /**
     * Property 1: Bug Condition — registerEnvironment with invalid defaultZoneId throws IllegalArgumentException.
     *
     * <p>For any non-null, non-empty defaultZoneId that does not match any zone in the solution,
     * registerEnvironment() SHALL throw IllegalArgumentException.</p>
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — operation succeeds, stores invalid value.</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — throws IllegalArgumentException.</p>
     *
     * <p><strong>Validates: Requirements 1.1, 2.1</strong></p>
     */
    @Property(tries = 20)
    @Label("registerEnvironment with invalid defaultZoneId should throw IllegalArgumentException (currently stores without error)")
    void registerEnvironmentWithInvalidDefaultZoneIdShouldReject(
            @ForAll("invalidZoneIds") String invalidZoneId) {

        // Arrange: mock all dependencies
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);
        AclEntryRepository aclEntryRepository = mock(AclEntryRepository.class);
        SecretsBackend secretsBackend = mock(SecretsBackend.class);
        EnvironmentResponseMapper mapper = mock(EnvironmentResponseMapper.class);
        CacheManager cacheManager = mock(CacheManager.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);

        // Mock solution lookup — solutionId resolves to a valid UUID, scoped to its owning domain
        UUID solutionUuid = UUID.randomUUID();
        Solution mockSolution = mock(Solution.class);
        when(mockSolution.getId()).thenReturn(solutionUuid);

        UUID domainUuid = UUID.randomUUID();
        Domain domain = new Domain();
        domain.setId(domainUuid);
        domain.setDomainId(DOMAIN_ID);
        when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, "test-solution"))
                .thenReturn(Optional.of(mockSolution));

        // Mock ZoneRepository: invalid zone does NOT exist in solution
        when(zoneRepository.existsBySolutionUuidAndZoneId(any(UUID.class), anyString())).thenReturn(false);

        // Mock environment save (returns entity with ID for unfixed code path)
        when(environmentRepository.save(any(Environment.class))).thenAnswer(invocation -> {
            Environment env = invocation.getArgument(0);
            env.setId(UUID.randomUUID());
            return env;
        });

        // Construct EnvironmentService (unfixed code — no ZoneRepository in constructor)
        EnvironmentService service = new EnvironmentService(
                environmentRepository, solutionRepository, domainRepository,
                aclEntryRepository, secretsBackend, mapper, cacheManager, queueServiceClient, zoneRepository);

        // Create request with invalid defaultZoneId
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestApp-" + System.nanoTime(), EnvironmentType.BROKERED, DefaultZoneDTO.of(invalidZoneId), "test-solution", null));

        // Act
        Throwable thrown = catchThrowable(() -> service.registerEnvironment(DOMAIN_ID, request));

        // Assert: Should throw IllegalArgumentException for invalid zone
        // On UNFIXED code: no exception thrown (operation succeeds — this assertion fails)
        assertThat(thrown)
                .as("registerEnvironment('%s') should throw IllegalArgumentException for invalid zone ID",
                        invalidZoneId)
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ========== Property 1b: updateEnvironment with invalid defaultZone.id ==========

    /**
     * Property 1b: Bug Condition — updateEnvironment with invalid defaultZone.id throws IllegalArgumentException.
     *
     * <p>For any non-null, non-empty defaultZone.id that does not match any zone in the solution,
     * updateEnvironment() SHALL throw IllegalArgumentException.</p>
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — operation succeeds, stores invalid value.</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — throws IllegalArgumentException.</p>
     *
     * <p><strong>Validates: Requirements 1.2, 2.2</strong></p>
     */
    @Property(tries = 20)
    @Label("updateEnvironment with invalid defaultZone.id should throw IllegalArgumentException (currently stores without error)")
    void updateEnvironmentWithInvalidDefaultZoneIdShouldReject(
            @ForAll("invalidZoneIds") String invalidZoneId) {

        // Arrange: mock all dependencies
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);
        AclEntryRepository aclEntryRepository = mock(AclEntryRepository.class);
        SecretsBackend secretsBackend = mock(SecretsBackend.class);
        EnvironmentResponseMapper mapper = mock(EnvironmentResponseMapper.class);
        CacheManager cacheManager = mock(CacheManager.class);
        QueueServiceClient queueServiceClient = mock(QueueServiceClient.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);

        // Mock existing environment with a valid solutionUuid
        UUID environmentId = UUID.randomUUID();
        UUID solutionUuid = UUID.randomUUID();
        Environment existingEnv = mock(Environment.class);
        when(existingEnv.getId()).thenReturn(environmentId);
        when(existingEnv.getSolutionUuid()).thenReturn(solutionUuid);
        when(environmentRepository.findByIdWithAclEntries(environmentId)).thenReturn(Optional.of(existingEnv));

        // Mock ZoneRepository: invalid zone does NOT exist in solution
        when(zoneRepository.existsBySolutionUuidAndZoneId(any(UUID.class), anyString())).thenReturn(false);

        // Mock save for unfixed code path
        when(environmentRepository.save(any(Environment.class))).thenReturn(existingEnv);
        when(mapper.toEnvironmentResponse(any(Environment.class))).thenReturn(null);

        // Construct EnvironmentService (unfixed code — no ZoneRepository in constructor)
        EnvironmentService service = new EnvironmentService(
                environmentRepository, solutionRepository, domainRepository,
                aclEntryRepository, secretsBackend, mapper, cacheManager, queueServiceClient, zoneRepository);

        // Create update request with invalid defaultZone.id
        EnvironmentUpdateRequest request = new EnvironmentUpdateRequest(
                null, DefaultZoneDTO.of(invalidZoneId), null, null, null, null);

        // Act
        Throwable thrown = catchThrowable(() -> service.updateEnvironment(environmentId, request));

        // Assert: Should throw IllegalArgumentException for invalid zone
        // On UNFIXED code: no exception thrown (operation succeeds — this assertion fails)
        assertThat(thrown)
                .as("updateEnvironment with defaultZone.id='%s' should throw IllegalArgumentException for invalid zone ID",
                        invalidZoneId)
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ========== Generators ==========

    /**
     * Generates arbitrary non-empty strings as invalid zone IDs.
     * These represent zone IDs that do NOT exist in the solution.
     */
    @Provide
    Arbitrary<String> invalidZoneIds() {
        return Arbitraries.of(
                "nonexistent-zone",
                "00000000-0000-0000-0000-000000000001",  // UUID instead of zoneId
                "!@#$%^&*()",                            // garbage characters
                "   spaced-zone   ",                     // whitespace-padded
                "zone/with/slashes",                     // special path chars
                "x"                                      // single char
        );
    }
}
