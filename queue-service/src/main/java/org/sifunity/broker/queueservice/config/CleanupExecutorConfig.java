// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for the dedicated thread pool used by queue cleanup operations
 * (purge and delete).
 *
 * <p>The executor is sized conservatively (2–4 threads) because cleanup is I/O-bound
 * (Redis + S3) and we want to avoid starving the main request-handling threads.
 * The {@link ThreadPoolExecutor.CallerRunsPolicy} ensures that if the queue fills up,
 * the submitting thread executes the task itself — providing natural back-pressure
 * rather than rejecting work.</p>
 *
 * <p>All pool parameters are externalized under {@code queue.cleanup.executor.*} so
 * operators can tune them without a redeploy.</p>
 *
 * @see org.sifunity.broker.queueservice.service.QueueCleanupService
 */
@Configuration
public class CleanupExecutorConfig {

    @Value("${queue.cleanup.executor.core-pool-size:2}")
    private int corePoolSize;

    @Value("${queue.cleanup.executor.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${queue.cleanup.executor.queue-capacity:50}")
    private int queueCapacity;

    /**
     * Creates the cleanup executor bean used by {@code QueueCleanupService} to run
     * purge and delete operations asynchronously.
     *
     * @return a configured {@link ThreadPoolTaskExecutor}
     */
    @Bean("cleanupExecutor")
    public ThreadPoolTaskExecutor cleanupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("queue-cleanup-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
