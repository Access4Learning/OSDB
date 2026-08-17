// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.springframework.cache.CacheManager;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;

/**
 * Unit tests for EnvironmentService.
 *
 * <p>Tests the core business logic for environment management including
 * registration, validation, and credential generation. Updated for
 * SIF 3.7 response DTOs and the current EnvironmentService constructor.</p>
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentServiceTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private AclEntryRepository aclEntryRepository;

    @Mock
    private SecretsBackend secretsBackend;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private EnvironmentResponseMapper mapper;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private QueueServiceClient queueServiceClient;

    @InjectMocks
    private EnvironmentService environmentService;

    private static final String SOLUTION_ID = "test-solution";
    private static final UUID SOLUTION_UUID = UUID.randomUUID();
    private static final String DOMAIN_ID = "test-domain";
    private static final UUID DOMAIN_UUID = UUID.randomUUID();

    private EnvironmentRegistrationRequest validRequest;
    private Solution solution;
    private Domain domain;

    @BeforeEach
    void setUp() {
        validRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestApplication", EnvironmentType.BROKERED, new DefaultZoneDTO("default"), SOLUTION_ID, null));

        solution = new Solution();
        solution.setId(SOLUTION_UUID);
        solution.setSolutionId(SOLUTION_ID);
        solution.setDomainUuid(DOMAIN_UUID);

        domain = new Domain();
        domain.setId(DOMAIN_UUID);
        domain.setDomainId(DOMAIN_ID);

        // Mock zone validation to accept any defaultZoneId for these unit tests
        lenient().when(zoneRepository.existsBySolutionUuidAndZoneId(any(UUID.class), any(String.class))).thenReturn(true);
    }

    @Test
    void registerEnvironment_WithValidRequest_ShouldCreateEnvironment() {
        // Given
        Environment savedEnvironment = buildSavedEnvironment("TestApplication");

        when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(environmentRepository.save(any(Environment.class))).thenReturn(savedEnvironment);

        SifEnvironmentRegistrationResponse expectedResponse = buildRegistrationResponse(savedEnvironment, "c2hhcmVkLXNlY3JldA==");
        when(mapper.toRegistrationResponse(eq(savedEnvironment), any(String.class))).thenReturn(expectedResponse);

        // When
        SifEnvironmentRegistrationResponse response = environmentService.registerEnvironment(DOMAIN_ID, validRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.environment().id()).isEqualTo(savedEnvironment.getId());
        assertThat(response.environment().consumerName()).isEqualTo("TestApplication");
        assertThat(response.environment().sessionToken()).isNotNull();
        assertThat(response.sharedSecret()).isNotNull();
        assertThat(DefaultZoneDTO.idOf(response.environment().defaultZone())).isEqualTo("default");
        assertThat(response.environment().type()).isEqualTo(EnvironmentType.BROKERED);

        verify(solutionRepository).findByDomainUuidAndSolutionId(DOMAIN_UUID, SOLUTION_ID);
        verify(environmentRepository).save(any(Environment.class));
        verify(secretsBackend).store(eq(savedEnvironment.getId()), any(String.class));
        verify(mapper).toRegistrationResponse(eq(savedEnvironment), any(String.class));
    }

    @Test
    void registerEnvironment_WithValidRequest_ShouldGenerateValidCredentials() {
        // Given
        Environment savedEnvironment = buildSavedEnvironment("TestApplication");

        when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(environmentRepository.save(any(Environment.class))).thenAnswer(invocation -> {
            Environment env = invocation.getArgument(0);
            savedEnvironment.setSessionToken(env.getSessionToken());
            return savedEnvironment;
        });

        // Capture the sharedSecret passed to the mapper
        when(mapper.toRegistrationResponse(any(Environment.class), any(String.class))).thenAnswer(invocation -> {
            Environment env = invocation.getArgument(0);
            String secret = invocation.getArgument(1);
            return buildRegistrationResponse(env, secret);
        });

        // When
        SifEnvironmentRegistrationResponse response = environmentService.registerEnvironment(DOMAIN_ID, validRequest);

        // Then — sessionToken is valid UUID format
        assertThat(response.environment().sessionToken()).matches(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        );

        // sharedSecret is Base64 encoded and at least 256 bits (32 bytes)
        assertThat(response.sharedSecret()).isNotNull();
        byte[] secretBytes = Base64.getDecoder().decode(response.sharedSecret());
        assertThat(secretBytes).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void registerEnvironment_WithNullContextId_ShouldCreateEnvironment() {
        // Given
        EnvironmentRegistrationRequest requestWithNullContext = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestApplication", EnvironmentType.BROKERED, new DefaultZoneDTO("default"), SOLUTION_ID, null));

        Environment savedEnvironment = buildSavedEnvironment("TestApplication");
        when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(environmentRepository.save(any(Environment.class))).thenReturn(savedEnvironment);

        SifEnvironmentRegistrationResponse expectedResponse = buildRegistrationResponse(savedEnvironment, "c2hhcmVkLXNlY3JldA==");
        when(mapper.toRegistrationResponse(eq(savedEnvironment), any(String.class))).thenReturn(expectedResponse);

        // When
        SifEnvironmentRegistrationResponse response = environmentService.registerEnvironment(DOMAIN_ID, requestWithNullContext);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.environment().type()).isEqualTo(EnvironmentType.BROKERED);

        verify(environmentRepository).save(any(Environment.class));
    }

    @Test
    void registerEnvironment_ShouldSetCreatedAtTimestamp() {
        // Given
        when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(environmentRepository.save(any(Environment.class))).thenAnswer(invocation -> {
            Environment env = invocation.getArgument(0);
            // Return the entity as-is so we can verify createdAt was set
            env.setId(UUID.randomUUID());
            return env;
        });
        when(mapper.toRegistrationResponse(any(Environment.class), any(String.class))).thenAnswer(invocation -> {
            Environment env = invocation.getArgument(0);
            String secret = invocation.getArgument(1);
            return buildRegistrationResponse(env, secret);
        });

        Instant beforeRegistration = Instant.now();

        // When
        SifEnvironmentRegistrationResponse response = environmentService.registerEnvironment(DOMAIN_ID, validRequest);

        Instant afterRegistration = Instant.now();

        // Then — verify the saved entity had createdAt set
        verify(environmentRepository).save(argThat(env ->
                env.getCreatedAt() != null
                && !env.getCreatedAt().isBefore(beforeRegistration)
                && !env.getCreatedAt().isAfter(afterRegistration)
        ));
    }

    @Test
    void registerEnvironment_ShouldGenerateUniqueCredentials() {
        // Given
        when(domainRepository.findByDomainId(DOMAIN_ID)).thenReturn(Optional.of(domain));
        when(solutionRepository.findByDomainUuidAndSolutionId(DOMAIN_UUID, SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(environmentRepository.save(any(Environment.class))).thenAnswer(invocation -> {
            Environment env = invocation.getArgument(0);
            env.setId(UUID.randomUUID());
            return env;
        });
        when(mapper.toRegistrationResponse(any(Environment.class), any(String.class))).thenAnswer(invocation -> {
            Environment env = invocation.getArgument(0);
            String secret = invocation.getArgument(1);
            return buildRegistrationResponse(env, secret);
        });

        // When — register two environments
        SifEnvironmentRegistrationResponse response1 = environmentService.registerEnvironment(DOMAIN_ID,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("App1", EnvironmentType.BROKERED, new DefaultZoneDTO("zone1"), SOLUTION_ID, null))
        );
        SifEnvironmentRegistrationResponse response2 = environmentService.registerEnvironment(DOMAIN_ID,
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("App2", EnvironmentType.BROKERED, new DefaultZoneDTO("zone2"), SOLUTION_ID, null))
        );

        // Then — credentials should be unique
        assertThat(response1.environment().sessionToken()).isNotEqualTo(response2.environment().sessionToken());
        assertThat(response1.sharedSecret()).isNotEqualTo(response2.sharedSecret());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Environment buildSavedEnvironment(String applicationName) {
        Environment env = new Environment();
        env.setId(UUID.randomUUID());
        env.setApplicationName(applicationName);
        env.setSessionToken(UUID.randomUUID().toString());
        env.setSharedSecret("PLACEHOLDER");
        env.setCreatedAt(Instant.now());
        env.setDefaultZoneId("default");
        env.setType(EnvironmentType.BROKERED);
        env.setSolutionUuid(SOLUTION_UUID);
        env.setFingerprint(UUID.randomUUID().toString());
        env.setAuthenticationMethod("SIF_HMACSHA256");
        env.setConsumerName(applicationName);
        return env;
    }

    private SifEnvironmentRegistrationResponse buildRegistrationResponse(Environment env, String sharedSecret) {
        SifEnvironmentResponse envResponse = new SifEnvironmentResponse(
                env.getId(),
                env.getType(),
                env.getFingerprint(),
                env.getSessionToken(),
                SOLUTION_ID,
                new DefaultZoneDTO(env.getDefaultZoneId()),
                env.getAuthenticationMethod(),
                null, // instanceId
                null, // userToken
                env.getConsumerName(),
                null, // applicationInfo
                null, // infrastructureServices
                null  // provisionedZones
        );
        return SifEnvironmentRegistrationResponse.of(envResponse, sharedSecret);
    }
}
