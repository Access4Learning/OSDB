// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.integration;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.secrets.config.MigrationProperties;
import org.sifunity.broker.environment.secrets.config.MultiKeyProperties;
import org.sifunity.broker.environment.secrets.impl.MultiKeyBackend;
import org.sifunity.broker.environment.secrets.impl.PlaintextBackend;
import org.sifunity.broker.environment.secrets.migration.MigrationController;
import org.sifunity.broker.environment.secrets.migration.MigrationJob;
import org.sifunity.broker.environment.secrets.migration.MigrationStatus;
import org.sifunity.broker.environment.service.EnvironmentResponseMapper;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;

/**
 * Integration test for backend migration from plaintext to multi-key.
 * 
 * <p><strong>Validates: Requirements 10.1, 10.2, 10.4, 10.5, 10.6</strong></p>
 */
@Slf4j
@DisplayName("Backend Migration Integration Test")
class BackendMigrationIntegrationTest {

    private static final String TEST_DOMAIN_ID = "test-domain";

    private EnvironmentRepository environmentRepository;
    private SolutionRepository solutionRepository;
    private DomainRepository domainRepository;
    private Map<UUID, Environment> savedEnvironments;

    @BeforeEach
    void setUp() {
        environmentRepository = mock(EnvironmentRepository.class);
        solutionRepository = mock(SolutionRepository.class);
        domainRepository = mock(DomainRepository.class);
        savedEnvironments = new HashMap<>();

        // Mock a test domain so registerEnvironment can resolve the domain-scoped solution
        UUID domainUuid = UUID.randomUUID();
        Domain testDomain = new Domain();
        testDomain.setId(domainUuid);
        testDomain.setDomainId(TEST_DOMAIN_ID);
        when(domainRepository.findByDomainId(TEST_DOMAIN_ID)).thenReturn(Optional.of(testDomain));

        // Mock a test solution so registerEnvironment can resolve solutionId (scoped to its domain)
        Solution testSolution = new Solution();
        testSolution.setId(UUID.randomUUID());
        testSolution.setSolutionId("test-solution");
        testSolution.setDomainUuid(domainUuid);
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, "test-solution"))
                .thenReturn(Optional.of(testSolution));
        
        when(environmentRepository.save(any(Environment.class))).thenAnswer(invocation -> {
            Environment env = invocation.getArgument(0);
            if (env.getId() == null) {
                env.setId(UUID.randomUUID());
            }
            savedEnvironments.put(env.getId(), env);
            return env;
        });
        
        when(environmentRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.ofNullable(savedEnvironments.get(id));
        });
        
        when(environmentRepository.existsByApplicationName(anyString())).thenReturn(false);
    }

    @Test
    @DisplayName("Should migrate from plaintext to multi-key backend")
    void shouldMigrateFromPlaintextToMultiKeyBackend() throws Exception {
        // Phase 1: Setup with PlaintextBackend
        SecretsBackend plaintextBackend = new PlaintextBackend(environmentRepository);
        EnvironmentService plaintextService = new EnvironmentService(environmentRepository, solutionRepository, domainRepository, mock(org.sifunity.broker.environment.repository.AclEntryRepository.class), plaintextBackend, createStubbedMapper(), mock(org.springframework.cache.CacheManager.class), mock(org.sifunity.broker.environment.service.QueueServiceClient.class), mock(org.sifunity.broker.environment.repository.ZoneRepository.class));
        
        EnvironmentRegistrationRequest request1 = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("PlaintextApp1", EnvironmentType.BROKERED, null, "test-solution", null));
        
        SifEnvironmentRegistrationResponse response1 = plaintextService.registerEnvironment(TEST_DOMAIN_ID, request1);
        String secret1 = response1.sharedSecret();
        UUID env1Id = response1.environment().id();
        
        Environment env1 = savedEnvironments.get(env1Id);
        assertThat(env1.getSharedSecret())
                .as("Secret should be stored in plaintext")
                .isEqualTo(secret1)
                .doesNotContain(":");

        
        // Phase 2: Switch to MultiKeyBackend
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240115", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=");
        properties.setKeys(keys);
        
        SecretsBackend multiKeyBackend = new MultiKeyBackend(environmentRepository, properties);
        EnvironmentService multiKeyService = new EnvironmentService(environmentRepository, solutionRepository, domainRepository, mock(org.sifunity.broker.environment.repository.AclEntryRepository.class), multiKeyBackend, createStubbedMapper(), mock(org.springframework.cache.CacheManager.class), mock(org.sifunity.broker.environment.service.QueueServiceClient.class), mock(org.sifunity.broker.environment.repository.ZoneRepository.class));
        
        // Phase 3: Verify plaintext secret can be read (backward compatibility)
        String retrievedSecret1 = multiKeyService.getSharedSecret(env1Id);
        assertThat(retrievedSecret1)
                .as("Plaintext secret should be readable after switching to MultiKeyBackend")
                .isEqualTo(secret1);
        
        // Phase 4: Setup and run migration
        MigrationProperties migrationProperties = new MigrationProperties();
        migrationProperties.setEnabled(true);
        migrationProperties.setBatchSize(10);
        
        when(environmentRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(invocation -> {
                    org.springframework.data.domain.Pageable pageable = invocation.getArgument(0);
                    java.util.List<Environment> allEnvs = new java.util.ArrayList<>(savedEnvironments.values());
                    int start = (int) pageable.getOffset();
                    int end = Math.min(start + pageable.getPageSize(), allEnvs.size());
                    if (start >= allEnvs.size()) {
                        return new org.springframework.data.domain.PageImpl<>(
                                java.util.Collections.emptyList(), pageable, allEnvs.size());
                    }
                    java.util.List<Environment> pageContent = allEnvs.subList(start, end);
                    return new org.springframework.data.domain.PageImpl<>(
                            pageContent, pageable, allEnvs.size());
                });
        
        MigrationJob migrationJob = new MigrationJob(multiKeyBackend, environmentRepository, migrationProperties);
        MigrationController migrationController = new MigrationController(migrationJob);
        
        ResponseEntity<MigrationStatus> migrationResponse = migrationController.triggerMigration();
        MigrationStatus migrationStatus = migrationResponse.getBody();
        
        assertThat(migrationStatus).isNotNull();
        assertThat(migrationStatus.getMigrated()).isEqualTo(1);
        assertThat(migrationStatus.getFailed()).isEqualTo(0);

        
        // Phase 5: Verify secret is now encrypted
        Environment migratedEnv1 = savedEnvironments.get(env1Id);
        assertThat(migratedEnv1.getSharedSecret())
                .as("Migrated secret should be encrypted with version prefix")
                .startsWith("20240115:");
        
        // Phase 6: Verify secret still decrypts to original value
        String finalSecret1 = multiKeyService.getSharedSecret(env1Id);
        assertThat(finalSecret1)
                .as("Migrated secret should decrypt to original value")
                .isEqualTo(secret1);
        
        // Phase 7: Verify API compatibility - new registrations work
        EnvironmentRegistrationRequest request2 = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("PostMigrationApp", EnvironmentType.BROKERED, null, "test-solution", null));
        
        SifEnvironmentRegistrationResponse response2 = multiKeyService.registerEnvironment(TEST_DOMAIN_ID, request2);
        assertThat(response2).isNotNull();
        assertThat(response2.sharedSecret()).isNotNull().doesNotContain(":");
        
        Environment env2 = savedEnvironments.get(response2.environment().id());
        assertThat(env2.getSharedSecret()).startsWith("20240115:");
    }

    @Test
    @DisplayName("Should maintain database schema backward compatibility")
    void shouldMaintainDatabaseSchemaBackwardCompatibility() {
        // Create environment with plaintext backend
        SecretsBackend plaintextBackend = new PlaintextBackend(environmentRepository);
        EnvironmentService plaintextService = new EnvironmentService(environmentRepository, solutionRepository, domainRepository, mock(org.sifunity.broker.environment.repository.AclEntryRepository.class), plaintextBackend, createStubbedMapper(), mock(org.springframework.cache.CacheManager.class), mock(org.sifunity.broker.environment.service.QueueServiceClient.class), mock(org.sifunity.broker.environment.repository.ZoneRepository.class));
        
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("SchemaTestApp", EnvironmentType.BROKERED, null, "test-solution", null));
        
        SifEnvironmentRegistrationResponse response = plaintextService.registerEnvironment(TEST_DOMAIN_ID, request);
        UUID envId = response.environment().id();
        String originalSecret = response.sharedSecret();
        
        Environment plaintextEnv = savedEnvironments.get(envId);
        assertThat(plaintextEnv.getSharedSecret())
                .as("Should store plaintext secret without prefix")
                .isEqualTo(originalSecret)
                .doesNotContain(":");

        
        // Switch to multi-key backend (no schema change)
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240115", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=");
        properties.setKeys(keys);
        
        SecretsBackend multiKeyBackend = new MultiKeyBackend(environmentRepository, properties);
        EnvironmentService multiKeyService = new EnvironmentService(environmentRepository, solutionRepository, domainRepository, mock(org.sifunity.broker.environment.repository.AclEntryRepository.class), multiKeyBackend, createStubbedMapper(), mock(org.springframework.cache.CacheManager.class), mock(org.sifunity.broker.environment.service.QueueServiceClient.class), mock(org.sifunity.broker.environment.repository.ZoneRepository.class));
        
        // Verify plaintext secret can be read (same column, different format)
        String retrievedPlaintext = multiKeyService.getSharedSecret(envId);
        assertThat(retrievedPlaintext)
                .as("Should read plaintext secret from same column")
                .isEqualTo(originalSecret);
        
        // Update secret to encrypted format (same column)
        multiKeyBackend.store(envId, originalSecret);
        
        Environment encryptedEnv = savedEnvironments.get(envId);
        assertThat(encryptedEnv.getSharedSecret())
                .as("Should store encrypted secret in same column with version prefix")
                .startsWith("20240115:")
                .isNotEqualTo(originalSecret);
        
        // Verify encrypted secret can be read (same column)
        String retrievedEncrypted = multiKeyService.getSharedSecret(envId);
        assertThat(retrievedEncrypted)
                .as("Should decrypt secret from same column")
                .isEqualTo(originalSecret);
    }

    /**
     * Creates an EnvironmentResponseMapper mock that is stubbed to return a minimal
     * SifEnvironmentRegistrationResponse from toRegistrationResponse(). This is needed
     * because the real mapper requires BrokerInfrastructureConfig, and these tests only
     * care about the sharedSecret and environment id.
     */
    private EnvironmentResponseMapper createStubbedMapper() {
        EnvironmentResponseMapper mapper = mock(EnvironmentResponseMapper.class);
        when(mapper.toRegistrationResponse(any(Environment.class), anyString()))
                .thenAnswer(invocation -> {
                    Environment env = invocation.getArgument(0);
                    String secret = invocation.getArgument(1);
                    SifEnvironmentResponse envResponse = new SifEnvironmentResponse(
                            env.getId(), env.getType(), env.getFingerprint(),
                            env.getSessionToken(), null, null,
                            env.getAuthenticationMethod(), null, null,
                            env.getConsumerName(), null, null, null);
                    return SifEnvironmentRegistrationResponse.of(envResponse, secret);
                });
        return mapper;
    }
}

