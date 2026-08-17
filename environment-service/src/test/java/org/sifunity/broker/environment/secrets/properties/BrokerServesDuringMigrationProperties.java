// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.secrets.config.MigrationProperties;
import org.sifunity.broker.environment.secrets.migration.MigrationJob;
import org.sifunity.broker.environment.service.EnvironmentResponseMapper;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Property-based tests for broker serving requests during migration.
 * 
 * <p>These tests validate that the broker continues to serve API requests
 * (store, retrieve) while the migration job is running concurrently. The
 * migration process should not block or interfere with normal operations.</p>
 * 
 * <p><strong>Validates: Requirements 8.10</strong></p>
 * 
 * <p><strong>Property 14: Broker Serves Requests During Migration</strong></p>
 * <p>For any valid API request received while the migration job is running,
 * the request should be processed successfully without blocking or errors.</p>
 */
@Tag("pluggable-secrets-backend")
class BrokerServesDuringMigrationProperties {

    // ========== Property 14: Broker Serves Requests During Migration ==========

    /**
     * Property 14.1: Broker processes registration requests during migration
     * 
     * <p>For any number of concurrent registration requests made while migration
     * is running, all requests should complete successfully without blocking.</p>
     * 
     * <p>This property validates that the migration job does not lock resources
     * needed for environment registration.</p>
     * 
     * <p><strong>Validates: Requirements 8.10</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-14-Broker-Serves-Requests-During-Migration")
    void brokerProcessesRegistrationRequestsDuringMigration(
            @ForAll("concurrentRequestCounts") int requestCount,
            @ForAll("migrationEnvironmentCounts") int migrationEnvCount) {
        
        Assume.that(requestCount > 0);
        
        // Given: A broker with migration running and concurrent registration requests
        ConcurrentOperationsHelper helper = new ConcurrentOperationsHelper(migrationEnvCount);
        
        // When: Registration requests are made concurrently with migration
        List<RegistrationResult> results = helper.runConcurrentRegistrations(requestCount);
        
        // Then: All registration requests should succeed
        assertThat(results)
            .as("All registration requests should complete")
            .hasSize(requestCount);
        
        long successCount = results.stream()
            .filter(r -> r.success && r.response != null)
            .count();
        
        assertThat(successCount)
            .as("All registration requests should succeed during migration")
            .isEqualTo(requestCount);
    }

    /**
     * Property 14.2: Broker processes retrieval requests during migration
     * 
     * <p>For any number of concurrent retrieval requests made while migration
     * is running, all requests should complete successfully and return correct
     * secret values.</p>
     * 
     * <p><strong>Validates: Requirements 8.10</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-14-Broker-Serves-Requests-During-Migration")
    void brokerProcessesRetrievalRequestsDuringMigration(
            @ForAll("concurrentRequestCounts") int requestCount,
            @ForAll("migrationEnvironmentCounts") int migrationEnvCount) {
        
        Assume.that(requestCount > 0);
        
        // Given: A broker with existing environments and migration running
        ConcurrentOperationsHelper helper = new ConcurrentOperationsHelper(migrationEnvCount);
        List<UUID> existingEnvIds = helper.createExistingEnvironments(requestCount);
        
        // When: Retrieval requests are made concurrently with migration
        List<RetrievalResult> results = helper.runConcurrentRetrievals(existingEnvIds);
        
        // Then: All retrieval requests should succeed
        assertThat(results)
            .as("All retrieval requests should complete")
            .hasSize(requestCount);
        
        long successCount = results.stream()
            .filter(r -> r.success && r.secret != null)
            .count();
        
        assertThat(successCount)
            .as("All retrieval requests should succeed during migration")
            .isEqualTo(requestCount);
    }

    /**
     * Property 14.3: Broker processes mixed operations during migration
     * 
     * <p>For any mix of registration and retrieval requests made while migration
     * is running, all requests should complete successfully.</p>
     * 
     * <p>This property validates that different types of operations can run
     * concurrently with migration without interference.</p>
     * 
     * <p><strong>Validates: Requirements 8.10</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-14-Broker-Serves-Requests-During-Migration")
    void brokerProcessesMixedOperationsDuringMigration(
            @ForAll("concurrentRequestCounts") int registrationCount,
            @ForAll("concurrentRequestCounts") int retrievalCount,
            @ForAll("migrationEnvironmentCounts") int migrationEnvCount) {
        
        Assume.that(registrationCount > 0 || retrievalCount > 0);
        
        // Given: A broker with migration running and mixed operations
        ConcurrentOperationsHelper helper = new ConcurrentOperationsHelper(migrationEnvCount);
        List<UUID> existingEnvIds = helper.createExistingEnvironments(retrievalCount);
        
        // When: Mixed operations are performed concurrently with migration
        MixedOperationResults results = helper.runMixedOperations(registrationCount, existingEnvIds);
        
        // Then: All operations should succeed
        assertThat(results.registrationSuccessCount)
            .as("All registration requests should succeed")
            .isEqualTo(registrationCount);
        
        assertThat(results.retrievalSuccessCount)
            .as("All retrieval requests should succeed")
            .isEqualTo(retrievalCount);
        
        assertThat(results.totalFailures)
            .as("No operations should fail during migration")
            .isEqualTo(0);
    }

    /**
     * Property 14.4: Migration completes while serving requests
     * 
     * <p>For any migration job running concurrently with API requests, the
     * migration should complete successfully and process all environments.</p>
     * 
     * <p>This property validates that API requests don't prevent migration
     * from completing.</p>
     * 
     * <p><strong>Validates: Requirements 8.10</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-14-Broker-Serves-Requests-During-Migration")
    void migrationCompletesWhileServingRequests(
            @ForAll("concurrentRequestCounts") int requestCount,
            @ForAll("migrationEnvironmentCounts") int migrationEnvCount) {
        
        Assume.that(migrationEnvCount > 0);
        
        // Given: A broker with migration and concurrent requests
        ConcurrentOperationsHelper helper = new ConcurrentOperationsHelper(migrationEnvCount);
        
        // When: Requests are made while migration runs
        helper.runConcurrentRegistrations(requestCount);
        
        // Then: Migration should have processed all environments
        // Note: We verify retrieve calls for migration only (store calls include both migration and registrations)
        verify(helper.secretsBackend, times(migrationEnvCount)).retrieve(any(UUID.class));
        
        // Verify that at least the migration environments were stored (may be more due to registrations)
        verify(helper.secretsBackend, atLeast(migrationEnvCount)).store(any(UUID.class), anyString());
    }

    /**
     * Property 14.5: No request blocking during migration
     * 
     * <p>For any API request made during migration, the request should complete
     * within a reasonable time without being blocked by migration operations.</p>
     * 
     * <p>This property validates that migration doesn't cause request timeouts
     * or excessive delays.</p>
     * 
     * <p><strong>Validates: Requirements 8.10</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-14-Broker-Serves-Requests-During-Migration")
    void noRequestBlockingDuringMigration(
            @ForAll("concurrentRequestCounts") int requestCount,
            @ForAll("migrationEnvironmentCounts") int migrationEnvCount) {
        
        Assume.that(requestCount > 0);
        
        // Given: A broker with migration running
        ConcurrentOperationsHelper helper = new ConcurrentOperationsHelper(migrationEnvCount);
        
        // When: Requests are made with timeout monitoring
        long startTime = System.currentTimeMillis();
        List<RegistrationResult> results = helper.runConcurrentRegistrations(requestCount);
        long duration = System.currentTimeMillis() - startTime;
        
        // Then: All requests should complete
        long successCount = results.stream().filter(r -> r.success).count();
        assertThat(successCount).isEqualTo(requestCount);
        
        // And: Requests should complete in reasonable time (not blocked)
        // Allow 5 seconds per request as reasonable upper bound
        long maxReasonableTime = requestCount * 5000L;
        assertThat(duration)
            .as("Requests should not be blocked by migration")
            .isLessThan(maxReasonableTime);
    }

    /**
     * Property 14.6: Concurrent operations maintain data consistency
     * 
     * <p>For any operations performed during migration, the data should remain
     * consistent - secrets should decrypt to correct values and no corruption
     * should occur.</p>
     * 
     * <p><strong>Validates: Requirements 8.10</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-14-Broker-Serves-Requests-During-Migration")
    void concurrentOperationsMaintainDataConsistency(
            @ForAll("concurrentRequestCounts") int requestCount,
            @ForAll("migrationEnvironmentCounts") int migrationEnvCount) {
        
        Assume.that(requestCount > 0);
        
        // Given: A broker with migration and concurrent operations
        ConcurrentOperationsHelper helper = new ConcurrentOperationsHelper(migrationEnvCount);
        
        // When: Environments are registered during migration
        List<RegistrationResult> results = helper.runConcurrentRegistrations(requestCount);
        
        // Then: All registered secrets should be retrievable with correct values
        for (RegistrationResult result : results) {
            if (result.success && result.response != null) {
                String retrievedSecret = helper.service.getSharedSecret(result.response.environment().id());
                assertThat(retrievedSecret)
                    .as("Retrieved secret should match registered secret")
                    .isEqualTo(result.response.sharedSecret());
            }
        }
    }

    // ========== Arbitraries (Test Data Generators) ==========

    /**
     * Provides arbitrary concurrent request counts for testing.
     * 
     * <p>Generates request counts between 1 and 50 for concurrent operations.</p>
     */
    @Provide
    Arbitrary<Integer> concurrentRequestCounts() {
        return Arbitraries.integers()
            .between(1, 50);
    }

    /**
     * Provides arbitrary migration environment counts for testing.
     * 
     * <p>Generates environment counts between 10 and 200 for migration.</p>
     */
    @Provide
    Arbitrary<Integer> migrationEnvironmentCounts() {
        return Arbitraries.integers()
            .between(10, 200);
    }

    // ========== Helper Classes ==========

    /**
     * Result of a registration operation.
     */
    private static class RegistrationResult {
        final boolean success;
        final SifEnvironmentRegistrationResponse response;
        final Exception error;
        
        RegistrationResult(SifEnvironmentRegistrationResponse response) {
            this.success = true;
            this.response = response;
            this.error = null;
        }
        
        RegistrationResult(Exception error) {
            this.success = false;
            this.response = null;
            this.error = error;
        }
    }

    /**
     * Result of a retrieval operation.
     */
    private static class RetrievalResult {
        final boolean success;
        final String secret;
        final Exception error;
        
        RetrievalResult(String secret) {
            this.success = true;
            this.secret = secret;
            this.error = null;
        }
        
        RetrievalResult(Exception error) {
            this.success = false;
            this.secret = null;
            this.error = error;
        }
    }

    /**
     * Results of mixed operations.
     */
    private static class MixedOperationResults {
        final int registrationSuccessCount;
        final int retrievalSuccessCount;
        final int totalFailures;
        
        MixedOperationResults(int registrationSuccessCount, int retrievalSuccessCount, int totalFailures) {
            this.registrationSuccessCount = registrationSuccessCount;
            this.retrievalSuccessCount = retrievalSuccessCount;
            this.totalFailures = totalFailures;
        }
    }

    /**
     * Helper class for testing concurrent operations during migration.
     * 
     * <p>This class creates a migration job and environment service, then runs
     * them concurrently to verify that API requests are served during migration.</p>
     */
    private static class ConcurrentOperationsHelper {
        private static final String TEST_DOMAIN_ID = "test-domain";
        private final MigrationJob migrationJob;
        final SecretsBackend secretsBackend;
        private final EnvironmentRepository environmentRepository;
        final EnvironmentService service;
        private final int migrationEnvCount;
        private final List<Environment> migrationEnvironments;
        private final Map<UUID, Environment> allEnvironments;
        private final AtomicInteger environmentCounter;
        
        ConcurrentOperationsHelper(int migrationEnvCount) {
            this.migrationEnvCount = migrationEnvCount;
            this.migrationEnvironments = createMigrationEnvironments(migrationEnvCount);
            this.allEnvironments = new ConcurrentHashMap<>();
            this.environmentCounter = new AtomicInteger(0);
            
            // Populate initial environments
            for (Environment env : migrationEnvironments) {
                allEnvironments.put(env.getId(), env);
            }
            
            // Create mocks
            this.secretsBackend = mock(SecretsBackend.class);
            this.environmentRepository = mock(EnvironmentRepository.class);
            MigrationProperties properties = mock(MigrationProperties.class);
            
            // Configure mocks
            when(properties.getBatchSize()).thenReturn(10);
            
            // Configure repository
            configureRepository();
            
            // Configure secrets backend
            configureSecretsBackend();
            
            // Create service and migration job
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
            // Configure DomainRepository + SolutionRepository mocks so registerEnvironment can
            // resolve the domain-scoped solution to a UUID
            org.sifunity.broker.environment.repository.DomainRepository domainRepository =
                    mock(org.sifunity.broker.environment.repository.DomainRepository.class);
            UUID testDomainUuid = UUID.randomUUID();
            org.sifunity.broker.environment.domain.Domain testDomain = new org.sifunity.broker.environment.domain.Domain();
            testDomain.setId(testDomainUuid);
            testDomain.setDomainId(TEST_DOMAIN_ID);
            when(domainRepository.findByDomainId(anyString())).thenReturn(Optional.of(testDomain));
            SolutionRepository solutionRepository = mock(SolutionRepository.class);
            org.sifunity.broker.environment.domain.Solution testSolution = new org.sifunity.broker.environment.domain.Solution();
            testSolution.setId(UUID.randomUUID());
            testSolution.setSolutionId("test-solution");
            testSolution.setDomainUuid(testDomainUuid);
            when(solutionRepository.findByDomainUuidAndSolutionId(any(UUID.class), anyString()))
                    .thenReturn(Optional.of(testSolution));
            org.sifunity.broker.environment.repository.ZoneRepository zoneRepository = mock(org.sifunity.broker.environment.repository.ZoneRepository.class);
            when(zoneRepository.existsBySolutionUuidAndZoneId(any(UUID.class), anyString())).thenReturn(true);
            this.service = new EnvironmentService(environmentRepository, solutionRepository, domainRepository, mock(org.sifunity.broker.environment.repository.AclEntryRepository.class), secretsBackend, mapper, mock(org.springframework.cache.CacheManager.class), mock(org.sifunity.broker.environment.service.QueueServiceClient.class), zoneRepository);
            this.migrationJob = new MigrationJob(secretsBackend, environmentRepository, properties);
        }
        
        private List<Environment> createMigrationEnvironments(int count) {
            List<Environment> envs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Environment env = new Environment();
                env.setId(UUID.randomUUID());
                env.setApplicationName("MigrationApp" + i);
                env.setSessionToken("migration-token-" + i);
                env.setSharedSecret("20240101:old-encrypted-secret-" + i);
                envs.add(env);
            }
            return envs;
        }
        
        List<UUID> createExistingEnvironments(int count) {
            List<UUID> ids = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                UUID envId = UUID.randomUUID();
                Environment env = new Environment();
                env.setId(envId);
                env.setApplicationName("ExistingApp" + i);
                env.setSessionToken("existing-token-" + i);
                env.setSharedSecret("20240115:encrypted-secret-" + i);
                allEnvironments.put(envId, env);
                ids.add(envId);
            }
            return ids;
        }
        
        private void configureRepository() {
            // Configure findAll for pagination (used by migration)
            when(environmentRepository.findAll(any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(0);
                    int pageNumber = pageable.getPageNumber();
                    int pageSize = pageable.getPageSize();
                    
                    int start = pageNumber * pageSize;
                    int end = Math.min(start + pageSize, migrationEnvCount);
                    
                    if (start >= migrationEnvCount) {
                        return new PageImpl<>(List.of(), pageable, migrationEnvCount);
                    }
                    
                    // Add small delay to simulate database access
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    List<Environment> pageContent = migrationEnvironments.subList(start, end);
                    boolean hasNext = end < migrationEnvCount;
                    
                    return new PageImpl<>(pageContent, pageable, migrationEnvCount) {
                        @Override
                        public boolean hasNext() {
                            return hasNext;
                        }
                    };
                });
            
            // Configure save (used by registration and migration)
            when(environmentRepository.save(any(Environment.class)))
                .thenAnswer(invocation -> {
                    Environment env = invocation.getArgument(0);
                    if (env.getId() == null) {
                        env.setId(UUID.randomUUID());
                    }
                    allEnvironments.put(env.getId(), env);
                    return env;
                });
            
            // Configure findById (used by retrieval)
            when(environmentRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    return Optional.ofNullable(allEnvironments.get(id));
                });
            
            // Configure existsByApplicationName (used by registration)
            when(environmentRepository.existsByApplicationName(anyString()))
                .thenReturn(false);
        }
        
        private void configureSecretsBackend() {
            // Configure store (used by registration and migration)
            doAnswer(invocation -> {
                UUID envId = invocation.getArgument(0);
                String secret = invocation.getArgument(1);
                
                Environment env = allEnvironments.get(envId);
                if (env != null) {
                    // Store the secret directly (simulating encryption happens inside backend)
                    env.setSharedSecret("20240115:" + secret);
                }
                return null;
            }).when(secretsBackend).store(any(UUID.class), anyString());
            
            // Configure retrieve (used by migration and retrieval)
            when(secretsBackend.retrieve(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID envId = invocation.getArgument(0);
                    Environment env = allEnvironments.get(envId);
                    
                    if (env == null) {
                        throw new RuntimeException("Environment not found: " + envId);
                    }
                    
                    // Simulate decryption by extracting the secret part after the version prefix
                    String storedValue = env.getSharedSecret();
                    if (storedValue != null && storedValue.contains(":")) {
                        // Return the secret part (after the version prefix)
                        return storedValue.split(":", 2)[1];
                    }
                    return "secret-" + envId;
                });
        }
        
        List<RegistrationResult> runConcurrentRegistrations(int count) {
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(count + 1, 20));
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(count + 1);
            List<RegistrationResult> results = new CopyOnWriteArrayList<>();
            
            // Start migration in background
            executor.submit(() -> {
                try {
                    startLatch.await();
                    migrationJob.migrateSecrets();
                } catch (Exception e) {
                    // Migration errors are logged but don't fail the test
                } finally {
                    completionLatch.countDown();
                }
            });
            
            // Start registration requests
            for (int i = 0; i < count; i++) {
                final int requestNum = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("ConcurrentApp" + requestNum, EnvironmentType.BROKERED, new DefaultZoneDTO("zone1"), "test-solution", null));

                        SifEnvironmentRegistrationResponse response = service.registerEnvironment(TEST_DOMAIN_ID, request);
                        results.add(new RegistrationResult(response));
                    } catch (Exception e) {
                        results.add(new RegistrationResult(e));
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }
            
            // Start all operations
            startLatch.countDown();
            
            // Wait for completion
            try {
                boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
                if (!completed) {
                    throw new RuntimeException("Operations did not complete within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for operations", e);
            } finally {
                executor.shutdown();
            }
            
            return results;
        }
        
        List<RetrievalResult> runConcurrentRetrievals(List<UUID> envIds) {
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(envIds.size() + 1, 20));
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(envIds.size() + 1);
            List<RetrievalResult> results = new CopyOnWriteArrayList<>();
            
            // Start migration in background
            executor.submit(() -> {
                try {
                    startLatch.await();
                    migrationJob.migrateSecrets();
                } catch (Exception e) {
                    // Migration errors are logged but don't fail the test
                } finally {
                    completionLatch.countDown();
                }
            });
            
            // Start retrieval requests
            for (UUID envId : envIds) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        String secret = service.getSharedSecret(envId);
                        results.add(new RetrievalResult(secret));
                    } catch (Exception e) {
                        results.add(new RetrievalResult(e));
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }
            
            // Start all operations
            startLatch.countDown();
            
            // Wait for completion
            try {
                boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
                if (!completed) {
                    throw new RuntimeException("Operations did not complete within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for operations", e);
            } finally {
                executor.shutdown();
            }
            
            return results;
        }
        
        MixedOperationResults runMixedOperations(int registrationCount, List<UUID> retrievalEnvIds) {
            ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(registrationCount + retrievalEnvIds.size() + 1, 20));
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(
                registrationCount + retrievalEnvIds.size() + 1);
            
            AtomicInteger registrationSuccess = new AtomicInteger(0);
            AtomicInteger retrievalSuccess = new AtomicInteger(0);
            AtomicInteger failures = new AtomicInteger(0);
            
            // Start migration in background
            executor.submit(() -> {
                try {
                    startLatch.await();
                    migrationJob.migrateSecrets();
                } catch (Exception e) {
                    // Migration errors are logged but don't fail the test
                } finally {
                    completionLatch.countDown();
                }
            });
            
            // Start registration requests
            for (int i = 0; i < registrationCount; i++) {
                final int requestNum = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("MixedApp" + requestNum, EnvironmentType.BROKERED, new DefaultZoneDTO("zone1"), "test-solution", null));

                        SifEnvironmentRegistrationResponse response = service.registerEnvironment(TEST_DOMAIN_ID, request);
                        if (response != null) {
                            registrationSuccess.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }
            
            // Start retrieval requests
            for (UUID envId : retrievalEnvIds) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        String secret = service.getSharedSecret(envId);
                        if (secret != null) {
                            retrievalSuccess.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }
            
            // Start all operations
            startLatch.countDown();
            
            // Wait for completion
            try {
                boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
                if (!completed) {
                    throw new RuntimeException("Operations did not complete within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for operations", e);
            } finally {
                executor.shutdown();
            }
            
            return new MixedOperationResults(
                registrationSuccess.get(),
                retrievalSuccess.get(),
                failures.get()
            );
        }
    }
}

