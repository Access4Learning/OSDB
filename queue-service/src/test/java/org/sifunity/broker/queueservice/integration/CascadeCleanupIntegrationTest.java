// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.queueservice.config.EmbeddedRedisTestConfiguration;
import org.sifunity.broker.queueservice.dto.CascadeCleanupRequest;
import org.sifunity.broker.queueservice.redis.RedisKeyManager;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for cascade cleanup end-to-end.
 *
 * <p>Uses Testcontainers PostgreSQL (via application-test.yml JDBC URL) and
 * Testcontainers Redis (via {@link EmbeddedRedisTestConfiguration}) for real
 * infrastructure testing.</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.2, 2.3, 3.1, 3.2, 4.1, 4.2, 4.3, 4.5, 5.1, 5.2, 6.1, 6.2</strong></p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(EmbeddedRedisTestConfiguration.class)
@TestPropertySource(properties = {
    "message.storage.type=filesystem",
    "message.storage.filesystem.path=./target/test-messages-cascade-integration",
    "cascade.cleanup.scan-batch-size=100",
    "cascade.cleanup.inter-batch-delay-ms=5",
    "cascade.cleanup.thread-pool-size=2"
})
class CascadeCleanupIntegrationTest {

    private static final String DOMAIN_ID = "cascade-test-domain";
    private static final String SOLUTION_A = "solution-alpha";
    private static final String SOLUTION_B = "solution-beta";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisKeyManager keyManager;

    @Autowired
    private StorageProvider storageProvider;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        cleanupTestKeys();
    }

    @AfterEach
    void tearDown() {
        cleanupTestKeys();
    }

    /**
     * Test 1: Domain-level cascade cleanup removes all Redis keys and storage payloads
     * for a domain with 2 solutions.
     *
     * <p>Seeds stream keys, DLQ keys, ref count keys, and storage payloads for
     * a domain with 2 solutions, then triggers domain cleanup and verifies
     * everything is removed.</p>
     *
     * <p><strong>Validates: Requirements 2.2, 4.1, 4.3, 4.5, 5.1</strong></p>
     */
    @Test
    void domainCleanup_removesAllKeysAndStorage() throws Exception {
        // Seed Redis keys for solution A
        UUID envA = UUID.randomUUID();
        UUID queueA1 = UUID.randomUUID();
        UUID queueA2 = UUID.randomUUID();
        seedStreamKeys(DOMAIN_ID, SOLUTION_A, envA, queueA1);
        seedStreamKeys(DOMAIN_ID, SOLUTION_A, envA, queueA2);
        seedRefCountKeys(DOMAIN_ID, SOLUTION_A, "payload-a1.json", "payload-a2.json");
        seedRateLimitKey(DOMAIN_ID, SOLUTION_A, envA.toString());

        // Seed Redis keys for solution B
        UUID envB = UUID.randomUUID();
        UUID queueB1 = UUID.randomUUID();
        seedStreamKeys(DOMAIN_ID, SOLUTION_B, envB, queueB1);
        seedRefCountKeys(DOMAIN_ID, SOLUTION_B, "payload-b1.json");
        seedRateLimitKey(DOMAIN_ID, SOLUTION_B, envB.toString());

        // Seed storage payloads
        storageProvider.store(DOMAIN_ID + "/" + SOLUTION_A + "/msg1.json", "payload-content-a1");
        storageProvider.store(DOMAIN_ID + "/" + SOLUTION_A + "/msg2.json", "payload-content-a2");
        storageProvider.store(DOMAIN_ID + "/" + SOLUTION_B + "/msg1.json", "payload-content-b1");

        // Verify keys exist before cleanup
        assertThat(scanKeys("domain:" + DOMAIN_ID + ":*")).isNotEmpty();
        assertThat(storageProvider.exists(DOMAIN_ID + "/" + SOLUTION_A + "/msg1.json")).isTrue();
        assertThat(storageProvider.exists(DOMAIN_ID + "/" + SOLUTION_B + "/msg1.json")).isTrue();

        // Act: call POST /internal/cascade-cleanup with domainId
        CascadeCleanupRequest request = new CascadeCleanupRequest(DOMAIN_ID, null);
        ResponseEntity<Void> response = postCascadeCleanup(request);

        // Verify 202 Accepted
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Wait for async cleanup to complete
        waitForCleanup(DOMAIN_ID, null);

        // Assert: SCAN domain:{d}:* returns 0 keys
        Set<String> remainingKeys = scanKeys("domain:" + DOMAIN_ID + ":*");
        assertThat(remainingKeys)
                .as("All Redis keys for domain should be removed")
                .isEmpty();

        // Assert: storage prefix is empty
        assertThat(storageProvider.exists(DOMAIN_ID + "/" + SOLUTION_A + "/msg1.json")).isFalse();
        assertThat(storageProvider.exists(DOMAIN_ID + "/" + SOLUTION_A + "/msg2.json")).isFalse();
        assertThat(storageProvider.exists(DOMAIN_ID + "/" + SOLUTION_B + "/msg1.json")).isFalse();
    }

    /**
     * Test 2: Solution-level cascade cleanup removes only keys for that solution,
     * leaving other solutions in the same domain untouched.
     *
     * <p><strong>Validates: Requirements 3.2, 4.2, 5.2</strong></p>
     */
    @Test
    void solutionCleanup_removesOnlySolutionKeys() throws Exception {
        // Seed Redis keys for solution A (to be deleted)
        UUID envA = UUID.randomUUID();
        UUID queueA = UUID.randomUUID();
        seedStreamKeys(DOMAIN_ID, SOLUTION_A, envA, queueA);
        seedRefCountKeys(DOMAIN_ID, SOLUTION_A, "payload-a.json");
        seedRateLimitKey(DOMAIN_ID, SOLUTION_A, envA.toString());

        // Seed Redis keys for solution B (should remain)
        UUID envB = UUID.randomUUID();
        UUID queueB = UUID.randomUUID();
        seedStreamKeys(DOMAIN_ID, SOLUTION_B, envB, queueB);
        seedRefCountKeys(DOMAIN_ID, SOLUTION_B, "payload-b.json");
        seedRateLimitKey(DOMAIN_ID, SOLUTION_B, envB.toString());

        // Seed storage payloads
        storageProvider.store(DOMAIN_ID + "/" + SOLUTION_A + "/msg.json", "content-a");
        storageProvider.store(DOMAIN_ID + "/" + SOLUTION_B + "/msg.json", "content-b");

        // Verify both solutions have keys
        assertThat(scanKeys("domain:" + DOMAIN_ID + ":solution:" + SOLUTION_A + ":*")).isNotEmpty();
        assertThat(scanKeys("domain:" + DOMAIN_ID + ":solution:" + SOLUTION_B + ":*")).isNotEmpty();

        // Act: cleanup solution A only
        CascadeCleanupRequest request = new CascadeCleanupRequest(DOMAIN_ID, SOLUTION_A);
        ResponseEntity<Void> response = postCascadeCleanup(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Wait for async cleanup
        waitForCleanup(DOMAIN_ID, SOLUTION_A);

        // Assert: solution A keys are gone
        Set<String> solutionAKeys = scanKeys("domain:" + DOMAIN_ID + ":solution:" + SOLUTION_A + ":*");
        assertThat(solutionAKeys)
                .as("All Redis keys for solution A should be removed")
                .isEmpty();

        // Assert: solution B keys remain
        Set<String> solutionBKeys = scanKeys("domain:" + DOMAIN_ID + ":solution:" + SOLUTION_B + ":*");
        assertThat(solutionBKeys)
                .as("Redis keys for solution B should remain untouched")
                .isNotEmpty();

        // Assert: storage for solution A is gone, solution B remains
        assertThat(storageProvider.exists(DOMAIN_ID + "/" + SOLUTION_A + "/msg.json")).isFalse();
        assertThat(storageProvider.exists(DOMAIN_ID + "/" + SOLUTION_B + "/msg.json")).isTrue();
    }

    /**
     * Test 3: Legacy key cleanup — seeds legacy-format keys and verifies they're removed
     * during domain cascade cleanup.
     *
     * <p><strong>Validates: Requirements 6.1, 6.2</strong></p>
     */
    @Test
    void domainCleanup_removesLegacyFormatKeys() throws Exception {
        UUID envId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        // Seed legacy-format keys
        String legacyStreamKey = keyManager.legacyStreamKey(DOMAIN_ID, SOLUTION_A, envId, queueId);
        String legacyRefKey = keyManager.legacyRefCountKey(DOMAIN_ID, SOLUTION_A, "legacy-payload.json");

        redisTemplate.opsForValue().set(legacyStreamKey, "legacy-stream-data");
        redisTemplate.opsForValue().set(legacyRefKey, "1");

        // Also seed a new-format key to verify both are cleaned
        String newStreamKey = keyManager.streamKey(DOMAIN_ID, SOLUTION_A, envId, queueId);
        redisTemplate.opsForValue().set(newStreamKey, "new-stream-data");

        // Verify legacy keys exist
        assertThat(redisTemplate.hasKey(legacyStreamKey)).isTrue();
        assertThat(redisTemplate.hasKey(legacyRefKey)).isTrue();
        assertThat(redisTemplate.hasKey(newStreamKey)).isTrue();

        // Act: domain cleanup
        CascadeCleanupRequest request = new CascadeCleanupRequest(DOMAIN_ID, null);
        ResponseEntity<Void> response = postCascadeCleanup(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Wait for async cleanup
        waitForCleanup(DOMAIN_ID, null);

        // Assert: legacy keys are removed
        assertThat(redisTemplate.hasKey(legacyStreamKey))
                .as("Legacy stream key should be removed")
                .isFalse();
        assertThat(redisTemplate.hasKey(legacyRefKey))
                .as("Legacy ref count key should be removed")
                .isFalse();

        // Assert: new-format key is also removed
        assertThat(redisTemplate.hasKey(newStreamKey))
                .as("New-format stream key should be removed")
                .isFalse();
    }

    /**
     * Test 4: Legacy key cleanup at solution level — seeds legacy-format keys for
     * a specific solution and verifies they're removed while other solutions' legacy
     * keys remain.
     *
     * <p><strong>Validates: Requirements 6.1, 6.2</strong></p>
     */
    @Test
    void solutionCleanup_removesLegacyFormatKeysForSolution() throws Exception {
        UUID envId = UUID.randomUUID();
        UUID queueA = UUID.randomUUID();
        UUID queueB = UUID.randomUUID();

        // Seed legacy keys for solution A (to be deleted)
        String legacyKeyA = keyManager.legacyStreamKey(DOMAIN_ID, SOLUTION_A, envId, queueA);
        redisTemplate.opsForValue().set(legacyKeyA, "legacy-a");

        // Seed legacy keys for solution B (should remain)
        String legacyKeyB = keyManager.legacyStreamKey(DOMAIN_ID, SOLUTION_B, envId, queueB);
        redisTemplate.opsForValue().set(legacyKeyB, "legacy-b");

        // Act: cleanup solution A only
        CascadeCleanupRequest request = new CascadeCleanupRequest(DOMAIN_ID, SOLUTION_A);
        ResponseEntity<Void> response = postCascadeCleanup(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Wait for async cleanup
        waitForCleanup(DOMAIN_ID, SOLUTION_A);

        // Assert: solution A legacy key removed
        assertThat(redisTemplate.hasKey(legacyKeyA))
                .as("Legacy key for solution A should be removed")
                .isFalse();

        // Assert: solution B legacy key remains
        assertThat(redisTemplate.hasKey(legacyKeyB))
                .as("Legacy key for solution B should remain")
                .isTrue();
    }

    /**
     * Test 5: Endpoint returns 400 when domainId is missing.
     *
     * <p><strong>Validates: Requirements 2.2 (validation)</strong></p>
     */
    @Test
    void cleanup_returnsBadRequest_whenDomainIdMissing() {
        CascadeCleanupRequest request = new CascadeCleanupRequest(null, null);
        ResponseEntity<Void> response = postCascadeCleanup(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private void seedStreamKeys(String domainId, String solutionId, UUID envId, UUID queueId) {
        String streamKey = keyManager.streamKey(domainId, solutionId, envId, queueId);
        String dlqKey = keyManager.dlqStreamKey(domainId, solutionId, envId, queueId);
        redisTemplate.opsForValue().set(streamKey, "stream-data");
        redisTemplate.opsForValue().set(dlqKey, "dlq-data");
    }

    private void seedRefCountKeys(String domainId, String solutionId, String... storageKeys) {
        for (String storageKey : storageKeys) {
            String refKey = keyManager.refCountKey(domainId, solutionId, storageKey);
            redisTemplate.opsForValue().set(refKey, "1");
        }
    }

    private void seedRateLimitKey(String domainId, String solutionId, String envId) {
        String rateKey = keyManager.rateLimitKey(domainId, solutionId, envId);
        redisTemplate.opsForValue().set(rateKey, "10");
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        var scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match(pattern).count(1000).build();
        try (var cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }

    private void waitForCleanup(String domainId, String solutionId) throws InterruptedException {
        // Async cleanup runs on a separate thread pool. Poll until keys are gone.
        String pattern = solutionId != null
                ? "domain:" + domainId + ":solution:" + solutionId + ":*"
                : "domain:" + domainId + ":*";

        String legacyPattern = solutionId != null
                ? "function:msg_queue:domain:" + domainId + ":solution:" + solutionId + ":*"
                : "function:msg_queue:domain:" + domainId + ":*";

        long deadline = System.currentTimeMillis() + 10_000; // 10 second timeout
        while (System.currentTimeMillis() < deadline) {
            Set<String> remaining = scanKeys(pattern);
            Set<String> legacyRemaining = scanKeys(legacyPattern);
            if (remaining.isEmpty() && legacyRemaining.isEmpty()) {
                // Give storage a moment to complete
                Thread.sleep(100);
                return;
            }
            Thread.sleep(50);
        }
        // If we get here, cleanup didn't complete in time — tests will fail with descriptive assertions
    }

    private void cleanupTestKeys() {
        // Clean up any leftover keys from this test class
        Set<String> keys = scanKeys("domain:" + DOMAIN_ID + ":*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        Set<String> legacyKeys = scanKeys("function:msg_queue:domain:" + DOMAIN_ID + ":*");
        if (!legacyKeys.isEmpty()) {
            redisTemplate.delete(legacyKeys);
        }
    }

    private ResponseEntity<Void> postCascadeCleanup(CascadeCleanupRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service-Token", "test-internal-token");
        headers.set("Content-Type", "application/json");
        HttpEntity<CascadeCleanupRequest> entity = new HttpEntity<>(request, headers);
        return restTemplate.postForEntity(
                baseUrl + "/internal/cascade-cleanup", entity, Void.class);
    }
}
