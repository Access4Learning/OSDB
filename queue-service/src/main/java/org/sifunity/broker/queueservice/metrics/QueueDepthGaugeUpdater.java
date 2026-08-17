// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.metrics;

import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.repository.QueueRepository.QueueKeyProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically updates queue depth and DLQ depth gauges for all known queues.
 *
 * <p>Uses Redis XLEN to query the current size of each queue's main stream
 * and DLQ stream, then updates the corresponding Micrometer gauges via
 * {@link QueueMetricsService#updateQueueDepth} and
 * {@link QueueMetricsService#updateDlqDepth}.</p>
 *
 * <p>The update interval is configurable via
 * {@code queue.metrics.depth-update-interval-ms} (default: 15000ms / 15s).</p>
 *
 * <p>Requirements: 2.4, 2.5</p>
 */
@Component
public class QueueDepthGaugeUpdater {

    private static final Logger log = LoggerFactory.getLogger(QueueDepthGaugeUpdater.class);

    private final QueueRepository queueRepository;
    private final StreamMessageRepository streamMessageRepository;
    private final QueueMetricsService queueMetricsService;

    public QueueDepthGaugeUpdater(QueueRepository queueRepository,
                                  StreamMessageRepository streamMessageRepository,
                                  QueueMetricsService queueMetricsService) {
        this.queueRepository = queueRepository;
        this.streamMessageRepository = streamMessageRepository;
        this.queueMetricsService = queueMetricsService;
    }

    /**
     * Scheduled task that updates queue depth and DLQ depth gauges for all queues.
     *
     * <p>For each queue in the database, reads the persisted domainId and solutionId
     * (populated at creation time) to build the correct Redis stream key, then queries
     * XLEN for both the main stream and the DLQ stream.</p>
     *
     * <p>Uses a lightweight projection ({@link QueueKeyProjection}) to avoid loading
     * full entity state every tick. Per-tick cost: one SELECT (5 columns) + 2×XLEN
     * per queue. With typical queue counts (&lt;1000), this completes well within the
     * 15-second interval.</p>
     */
    @Scheduled(fixedDelayString = "${queue.metrics.depth-update-interval-ms:15000}")
    public void updateAllQueueDepths() {
        try {
            List<QueueKeyProjection> queues = queueRepository.findAllKeyProjections();
            log.debug("Updating queue depth gauges for {} queues", queues.size());

            for (QueueKeyProjection queue : queues) {
                updateDepthForQueue(queue);
            }
        } catch (Exception e) {
            log.warn("Failed to update queue depth gauges: {}", e.getMessage(), e);
        }
    }

    /**
     * Updates the depth gauges for a single queue.
     *
     * <p>Uses the queue's persisted domainId and solutionId (set at creation time)
     * to construct the correct Redis stream key. Falls back to empty strings for
     * queues created before V46 migration (legacy backward-compat).</p>
     */
    void updateDepthForQueue(QueueKeyProjection queue) {
        try {
            String domainId = queue.getDomainId() != null ? queue.getDomainId() : "";
            String solutionId = queue.getSolutionId() != null ? queue.getSolutionId() : "";

            String streamKey = streamMessageRepository.resolveStreamKey(
                    domainId, solutionId, queue.getOwnerId(), queue.getId());
            String dlqKey = streamMessageRepository.resolveDlqStreamKey(
                    domainId, solutionId, queue.getOwnerId(), queue.getId());

            long queueDepth = streamMessageRepository.messageCount(streamKey);
            long dlqDepth = streamMessageRepository.messageCount(dlqKey);

            queueMetricsService.updateQueueDepth(queue.getName(), queueDepth);
            queueMetricsService.updateDlqDepth(queue.getName(), dlqDepth);

            log.trace("Updated gauges for queue '{}': depth={}, dlqDepth={}",
                    queue.getName(), queueDepth, dlqDepth);
        } catch (Exception e) {
            log.warn("Failed to update depth gauges for queue '{}': {}",
                    queue.getName(), e.getMessage());
        }
    }

    /**
     * Updates the depth gauges for a single queue (full entity overload for backward compat).
     */
    void updateDepthForQueue(Queue queue) {
        try {
            String domainId = queue.getDomainId() != null ? queue.getDomainId() : "";
            String solutionId = queue.getSolutionId() != null ? queue.getSolutionId() : "";

            String streamKey = streamMessageRepository.resolveStreamKey(
                    domainId, solutionId, queue.getOwnerId(), queue.getId());
            String dlqKey = streamMessageRepository.resolveDlqStreamKey(
                    domainId, solutionId, queue.getOwnerId(), queue.getId());

            long queueDepth = streamMessageRepository.messageCount(streamKey);
            long dlqDepth = streamMessageRepository.messageCount(dlqKey);

            queueMetricsService.updateQueueDepth(queue.getName(), queueDepth);
            queueMetricsService.updateDlqDepth(queue.getName(), dlqDepth);

            log.trace("Updated gauges for queue '{}': depth={}, dlqDepth={}",
                    queue.getName(), queueDepth, dlqDepth);
        } catch (Exception e) {
            log.warn("Failed to update depth gauges for queue '{}': {}",
                    queue.getName(), e.getMessage());
        }
    }
}
