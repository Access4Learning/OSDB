// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Queue Service.
 * 
 * <p>The Queue Service is a foundational core service in the SIF Unity Broker
 * system that manages message queues and provides asynchronous message delivery
 * with at-least-once semantics.</p>
 * 
 * <p>Key responsibilities:
 * <ul>
 *   <li>Queue management (create, read, delete queues)</li>
 *   <li>Message enqueuing with pluggable storage</li>
 *   <li>Message delivery with visibility timeout</li>
 *   <li>Message acknowledgment and cleanup</li>
 *   <li>Dead-letter queue management for failed messages</li>
 *   <li>At-least-once delivery guarantees</li>
 * </ul>
 * 
 * <p>Architecture:
 * <ul>
 *   <li>PostgreSQL: Queue metadata storage</li>
 *   <li>Redis: Message metadata and delivery tracking</li>
 *   <li>Pluggable Storage: Message payload storage (S3 or filesystem)</li>
 * </ul>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
@SpringBootApplication
@EnableJpaRepositories
@EnableScheduling
@ComponentScan(basePackages = {
    "org.sifunity.broker.queueservice",
    "org.sifunity.broker.common"
})
public class QueueServiceApplication {

    /**
     * Main entry point for the Queue Service application.
     * 
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(QueueServiceApplication.class, args);
    }
}
