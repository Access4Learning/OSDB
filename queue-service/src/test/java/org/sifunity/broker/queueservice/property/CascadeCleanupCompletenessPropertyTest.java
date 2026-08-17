// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.sifunity.broker.queueservice.redis.RedisKeyManager;
import org.sifunity.broker.queueservice.service.CascadeCleanupService;
import org.sifunity.broker.queueservice.storage.FileSystemStorageProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for cascade cleanup completeness.
 *
 * <p>Generates random domain/solution/queue structures, performs cleanup,
 * and verifies that zero keys remain for the deleted scope while keys
 * for other domains/solutions are untouched.</p>
 *
 * <p><strong>Validates: Requirements 4.1, 4.2, 4.3, 5.1, 5.2, 6.1, 6.2</strong></p>
 */
class CascadeCleanupCompletenessPropertyTest {

    private static final int REDIS_PORT = 6379;

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(REDIS_PORT);

    static {
        REDIS_CONTAINER.start();
    }

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisKeyManager keyManager;
    private CascadeCleanupService cleanupService;
    private FileSystemStorageProvider storageProvider;
    private Path storagePath;

    @BeforeProperty
    void setUp() throws IOException {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                REDIS_CONTAINER.getHost(),
                REDIS_CONTAINER.getMappedPort(REDIS_PORT));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        keyManager = new RedisKeyManager();

        storagePath = Files.createTempDirectory("cascade-pbt-storage");
        storageProvider = new FileSystemStorageProvider(
                storagePath.toString(), new ObjectMapper());

        cleanupService = new CascadeCleanupService(
                redisTemplate, storageProvider, keyManager,
                new SimpleMeterRegistry(), 2);

        // Set @Value fields via reflection (not injected outside Spring context)
        setField(cleanupService, "scanBatchSize", 100);
        setField(cleanupService, "interBatchDelayMs", 5L);

        // Flush Redis to start clean
        connectionFactory.getConnection().serverCommands().flushAll();
    }

    @AfterProperty
    void tearDown() throws IOException {
        // Flush Redis
        try {
            connectionFactory.getConnection().serverCommands().flushAll();
        } catch (Exception ignored) {}

        // Clean up storage directory
        if (storagePath != null && Files.exists(storagePath)) {
            try (var walk = Files.walk(storagePath)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        }

        // Destroy connection factory
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    // ── Arbitraries ──

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(12)
                .map(String::toLowerCase);
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(String::toLowerCase);
    }

    @Provide
    Arbitrary<DomainStructure> domainStructures() {
        return Combinators.combine(
                domainIds(),
                Arbitraries.integers().between(1, 3), // number of solutions
                Arbitraries.integers().between(1, 3)  // queues per solution
        ).as((domainId, numSolutions, queuesPerSolution) -> {
            List<SolutionStructure> solutions = new ArrayList<>();
            for (int i = 0; i < numSolutions; i++) {
                String solutionId = "sol" + i + domainId.substring(0, Math.min(3, domainId.length()));
                List<UUID> queueIds = new ArrayList<>();
                for (int q = 0; q < queuesPerSolution; q++) {
                    queueIds.add(UUID.randomUUID());
                }
                solutions.add(new SolutionStructure(solutionId, UUID.randomUUID(), queueIds));
            }
            return new DomainStructure(domainId, solutions);
        });
    }

    /**
     * Property 1: After domain cleanup, zero keys remain for the deleted domain.
     *
     * <p>Seeds random domain/solution/queue structures into Redis, triggers domain
     * cleanup, and verifies that SCAN returns zero keys for that domain.</p>
     *
     * <p><strong>Validates: Requirements 4.1, 4.3, 6.1</strong></p>
     */
    @Property(tries = 15)
    @Label("After domain cleanup, zero keys remain for the deleted domain")
    void afterDomainCleanup_zeroKeysRemain(
            @ForAll("domainStructures") DomainStructure target) throws Exception {

        // Seed keys for target domain
        seedDomainKeys(target);

        // Verify keys exist
        Set<String> before = scanKeys(keyManager.domainPattern(target.domainId()));
        assertThat(before).isNotEmpty();

        // Act: domain cleanup
        cleanupService.submitDomainCleanup(target.domainId());

        // Wait for async completion
        waitForKeysGone(keyManager.domainPattern(target.domainId()), 5000);
        waitForKeysGone(keyManager.legacyDomainPattern(target.domainId()), 5000);

        // Assert: zero keys remain for new-format
        Set<String> remainingNew = scanKeys(keyManager.domainPattern(target.domainId()));
        assertThat(remainingNew)
                .as("No new-format keys should remain for domain " + target.domainId())
                .isEmpty();

        // Assert: zero keys remain for legacy-format
        Set<String> remainingLegacy = scanKeys(keyManager.legacyDomainPattern(target.domainId()));
        assertThat(remainingLegacy)
                .as("No legacy-format keys should remain for domain " + target.domainId())
                .isEmpty();
    }

    /**
     * Property 2: After solution cleanup, zero keys remain for the deleted solution.
     *
     * <p><strong>Validates: Requirements 4.2, 6.2</strong></p>
     */
    @Property(tries = 15)
    @Label("After solution cleanup, zero keys remain for the deleted solution")
    void afterSolutionCleanup_zeroKeysRemain(
            @ForAll("domainStructures") DomainStructure target) throws Exception {

        // Need at least one solution
        if (target.solutions().isEmpty()) return;

        // Seed keys for target domain
        seedDomainKeys(target);

        // Pick first solution to delete
        SolutionStructure targetSolution = target.solutions().get(0);

        // Verify keys exist for target solution
        Set<String> before = scanKeys(
                keyManager.solutionPattern(target.domainId(), targetSolution.solutionId()));
        assertThat(before).isNotEmpty();

        // Act: solution cleanup
        cleanupService.submitSolutionCleanup(target.domainId(), targetSolution.solutionId());

        // Wait for async completion
        waitForKeysGone(
                keyManager.solutionPattern(target.domainId(), targetSolution.solutionId()), 5000);
        waitForKeysGone(
                keyManager.legacySolutionPattern(target.domainId(), targetSolution.solutionId()), 5000);

        // Assert: zero keys remain for deleted solution
        Set<String> remainingNew = scanKeys(
                keyManager.solutionPattern(target.domainId(), targetSolution.solutionId()));
        assertThat(remainingNew)
                .as("No new-format keys should remain for solution " + targetSolution.solutionId())
                .isEmpty();

        Set<String> remainingLegacy = scanKeys(
                keyManager.legacySolutionPattern(target.domainId(), targetSolution.solutionId()));
        assertThat(remainingLegacy)
                .as("No legacy-format keys should remain for solution " + targetSolution.solutionId())
                .isEmpty();
    }

    /**
     * Property 3: Keys for OTHER domains/solutions are untouched after cleanup.
     *
     * <p>Seeds keys for two different domains, cleans up one, and verifies the
     * other domain's keys are completely intact.</p>
     *
     * <p><strong>Validates: Requirements 4.1, 4.2 (isolation)</strong></p>
     */
    @Property(tries = 15)
    @Label("Keys for OTHER domains are untouched after cleanup")
    void otherDomainKeysUntouched(
            @ForAll("domainStructures") DomainStructure target,
            @ForAll("domainStructures") DomainStructure bystander) throws Exception {

        // Ensure domains are different
        if (target.domainId().equals(bystander.domainId())) return;

        // Seed keys for both domains
        seedDomainKeys(target);
        seedDomainKeys(bystander);

        // Record bystander keys before cleanup
        Set<String> bystanderKeysBefore = scanKeys(keyManager.domainPattern(bystander.domainId()));
        Set<String> bystanderLegacyBefore = scanKeys(keyManager.legacyDomainPattern(bystander.domainId()));

        // Act: cleanup target domain
        cleanupService.submitDomainCleanup(target.domainId());

        // Wait for target cleanup to complete
        waitForKeysGone(keyManager.domainPattern(target.domainId()), 5000);
        waitForKeysGone(keyManager.legacyDomainPattern(target.domainId()), 5000);

        // Assert: bystander keys are untouched
        Set<String> bystanderKeysAfter = scanKeys(keyManager.domainPattern(bystander.domainId()));
        Set<String> bystanderLegacyAfter = scanKeys(keyManager.legacyDomainPattern(bystander.domainId()));

        assertThat(bystanderKeysAfter)
                .as("Bystander domain keys should be untouched")
                .containsExactlyInAnyOrderElementsOf(bystanderKeysBefore);

        assertThat(bystanderLegacyAfter)
                .as("Bystander domain legacy keys should be untouched")
                .containsExactlyInAnyOrderElementsOf(bystanderLegacyBefore);
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private void seedDomainKeys(DomainStructure domain) {
        for (SolutionStructure solution : domain.solutions()) {
            for (UUID queueId : solution.queueIds()) {
                // New-format keys
                String streamKey = keyManager.streamKey(
                        domain.domainId(), solution.solutionId(), solution.envId(), queueId);
                String dlqKey = keyManager.dlqStreamKey(
                        domain.domainId(), solution.solutionId(), solution.envId(), queueId);
                redisTemplate.opsForValue().set(streamKey, "data");
                redisTemplate.opsForValue().set(dlqKey, "dlq-data");

                // Legacy-format keys
                String legacyStream = keyManager.legacyStreamKey(
                        domain.domainId(), solution.solutionId(), solution.envId(), queueId);
                redisTemplate.opsForValue().set(legacyStream, "legacy-data");
            }

            // Ref count key (new format)
            String refKey = keyManager.refCountKey(
                    domain.domainId(), solution.solutionId(), "payload-" + solution.solutionId() + ".json");
            redisTemplate.opsForValue().set(refKey, "1");

            // Legacy ref count key
            String legacyRef = keyManager.legacyRefCountKey(
                    domain.domainId(), solution.solutionId(), "payload-" + solution.solutionId() + ".json");
            redisTemplate.opsForValue().set(legacyRef, "1");

            // Rate limit key
            String rateKey = keyManager.rateLimitKey(
                    domain.domainId(), solution.solutionId(), solution.envId().toString());
            redisTemplate.opsForValue().set(rateKey, "100");
        }
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        var scanOptions = ScanOptions.scanOptions().match(pattern).count(1000).build();
        try (var cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }

    private void waitForKeysGone(String pattern, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (scanKeys(pattern).isEmpty()) {
                return;
            }
            Thread.sleep(30);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    // ── Data structures ──

    record DomainStructure(String domainId, List<SolutionStructure> solutions) {}

    record SolutionStructure(String solutionId, UUID envId, List<UUID> queueIds) {}
}
