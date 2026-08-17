package org.sifunity.broker.queueservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.sifunity.broker.queueservice.redis.RedisKeyManager;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async service that performs bulk Redis key deletion and storage prefix deletion
 * when a domain or solution is deleted (cascade cleanup).
 */
@Service
public class CascadeCleanupService {

    private static final Logger log = LoggerFactory.getLogger(CascadeCleanupService.class);

    private final StringRedisTemplate redisTemplate;
    private final StorageProvider storageProvider;
    private final RedisKeyManager keyManager;
    private final MeterRegistry meterRegistry;
    private final ExecutorService cleanupExecutor;

    @Value("${cascade.cleanup.scan-batch-size:1000}")
    private int scanBatchSize;

    @Value("${cascade.cleanup.inter-batch-delay-ms:20}")
    private long interBatchDelayMs;

    public CascadeCleanupService(
            StringRedisTemplate redisTemplate,
            StorageProvider storageProvider,
            RedisKeyManager keyManager,
            MeterRegistry meterRegistry,
            @Value("${cascade.cleanup.thread-pool-size:2}") int threadPoolSize) {
        this.redisTemplate = redisTemplate;
        this.storageProvider = storageProvider;
        this.keyManager = keyManager;
        this.meterRegistry = meterRegistry;
        this.cleanupExecutor = Executors.newFixedThreadPool(threadPoolSize, new DaemonThreadFactory());
    }

    /**
     * Submit async cleanup for a domain deletion.
     * Scans both new-format and legacy-format keys, then deletes storage objects.
     */
    public void submitDomainCleanup(String domainId) {
        cleanupExecutor.submit(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                String newPattern = keyManager.domainPattern(domainId);
                String legacyPattern = keyManager.legacyDomainPattern(domainId);

                log.info("Domain cascade cleanup starting: domain={}, patterns=[{}, {}]",
                        domainId, newPattern, legacyPattern);

                long redisDeleted = 0;
                redisDeleted += scanAndUnlink(newPattern);
                redisDeleted += scanAndUnlink(legacyPattern);

                long storageDeleted = storageProvider.deleteByPrefix(domainId + "/");

                log.info("Domain cascade cleanup complete: domain={}, redisKeys={}, storageObjects={}",
                        domainId, redisDeleted, storageDeleted);

                meterRegistry.counter("cascade.cleanup.keys.deleted", "scope", "domain")
                        .increment(redisDeleted);
                meterRegistry.counter("cascade.cleanup.storage.deleted", "scope", "domain")
                        .increment(storageDeleted);

                sample.stop(meterRegistry.timer("cascade.cleanup.duration", "scope", "domain"));
            } catch (Exception e) {
                log.error("Domain cascade cleanup failed: domain={}", domainId, e);
            }
        });
    }

    /**
     * Submit async cleanup for a solution deletion.
     * Scans both new-format and legacy-format keys, then deletes storage objects.
     */
    public void submitSolutionCleanup(String domainId, String solutionId) {
        cleanupExecutor.submit(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                String newPattern = keyManager.solutionPattern(domainId, solutionId);
                String legacyPattern = keyManager.legacySolutionPattern(domainId, solutionId);

                log.info("Solution cascade cleanup starting: domain={}, solution={}, patterns=[{}, {}]",
                        domainId, solutionId, newPattern, legacyPattern);

                long redisDeleted = 0;
                redisDeleted += scanAndUnlink(newPattern);
                redisDeleted += scanAndUnlink(legacyPattern);

                long storageDeleted = storageProvider.deleteByPrefix(domainId + "/" + solutionId + "/");

                log.info("Solution cascade cleanup complete: domain={}, solution={}, redisKeys={}, storageObjects={}",
                        domainId, solutionId, redisDeleted, storageDeleted);

                meterRegistry.counter("cascade.cleanup.keys.deleted", "scope", "solution")
                        .increment(redisDeleted);
                meterRegistry.counter("cascade.cleanup.storage.deleted", "scope", "solution")
                        .increment(storageDeleted);

                sample.stop(meterRegistry.timer("cascade.cleanup.duration", "scope", "solution"));
            } catch (Exception e) {
                log.error("Solution cascade cleanup failed: domain={}, solution={}", domainId, solutionId, e);
            }
        });
    }

    /**
     * SCAN with cursor iteration + UNLINK in batches.
     * Uses inter-batch delay to avoid Redis CPU saturation.
     */
    private long scanAndUnlink(String pattern) {
        long totalDeleted = 0;
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(scanBatchSize).build();

        log.debug("Starting SCAN+UNLINK: pattern={}", pattern);

        try {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                List<byte[]> batch = new ArrayList<>(scanBatchSize);
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= scanBatchSize) {
                        totalDeleted += unlinkBatch(connection, batch);
                        batch.clear();
                        if (interBatchDelayMs > 0) {
                            Thread.sleep(interBatchDelayMs);
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    totalDeleted += unlinkBatch(connection, batch);
                }
            } finally {
                connection.close();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Cascade cleanup interrupted during SCAN: pattern={}", pattern);
        } catch (Exception e) {
            log.error("Error during SCAN+UNLINK: pattern={}", pattern, e);
        }

        return totalDeleted;
    }

    private long unlinkBatch(RedisConnection connection, List<byte[]> keys) {
        try {
            Long deleted = connection.keyCommands().unlink(keys.toArray(new byte[0][]));
            return deleted != null ? deleted : 0L;
        } catch (Exception e) {
            log.error("UNLINK batch failed for {} keys", keys.size(), e);
            return 0L;
        }
    }

    @PreDestroy
    void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simple daemon thread factory for the cleanup executor.
     */
    private static class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "cascade-cleanup-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
