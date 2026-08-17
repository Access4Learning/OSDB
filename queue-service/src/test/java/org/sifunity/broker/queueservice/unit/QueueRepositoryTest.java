// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for QueueRepository using H2 in-memory database.
 * Tests database operations for queue metadata.
 */
@DataJpaTest(excludeAutoConfiguration = RedisAutoConfiguration.class)
@ContextConfiguration(classes = QueueRepositoryTest.TestConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:15-alpine:///queuerepotest",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class QueueRepositoryTest {
    
    @Configuration
    @EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)
    @EntityScan(basePackageClasses = Queue.class)
    @EnableJpaRepositories(basePackageClasses = QueueRepository.class)
    static class TestConfig {
    }
    
    
    @Autowired
    private QueueRepository queueRepository;
    
    @Test
    void testSaveQueue_ShouldPersistQueue() {
        // Given
        UUID ownerId = UUID.randomUUID();
        Queue queue = Queue.builder()
                .name("test-queue")
                .ownerId(ownerId)
                .maxRetries(3)
                .retryDelayMs(5000L)
                .build();
        
        // When
        Queue saved = queueRepository.save(queue);
        
        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("test-queue");
        assertThat(saved.getOwnerId()).isEqualTo(ownerId);
        assertThat(saved.getCreated()).isNotNull();
        assertThat(saved.getMaxRetries()).isEqualTo(3);
        assertThat(saved.getRetryDelayMs()).isEqualTo(5000L);
    }
    
    @Test
    void testPrePersist_ShouldSetDefaults() {
        // Given
        UUID ownerId = UUID.randomUUID();
        Queue queue = Queue.builder()
                .name("test-queue")
                .ownerId(ownerId)
                .build();
        
        // When
        Queue saved = queueRepository.save(queue);
        
        // Then
        assertThat(saved.getCreated()).isNotNull();
        assertThat(saved.getMaxRetries()).isEqualTo(3);
        assertThat(saved.getRetryDelayMs()).isEqualTo(5000L);
    }
    
    @Test
    void testFindByOwnerId_ShouldReturnQueuesForOwner() {
        // Given
        UUID ownerId1 = UUID.randomUUID();
        UUID ownerId2 = UUID.randomUUID();
        
        Queue queue1 = createQueue("queue1", ownerId1);
        Queue queue2 = createQueue("queue2", ownerId1);
        Queue queue3 = createQueue("queue3", ownerId2);
        
        queueRepository.saveAll(List.of(queue1, queue2, queue3));
        
        // When
        List<Queue> owner1Queues = queueRepository.findByOwnerId(ownerId1);
        List<Queue> owner2Queues = queueRepository.findByOwnerId(ownerId2);
        
        // Then
        assertThat(owner1Queues).hasSize(2);
        assertThat(owner1Queues).extracting(Queue::getName)
                .containsExactlyInAnyOrder("queue1", "queue2");
        
        assertThat(owner2Queues).hasSize(1);
        assertThat(owner2Queues).extracting(Queue::getName)
                .containsExactly("queue3");
    }
    
    @Test
    void testFindByOwnerId_ShouldReturnEmptyListWhenNoQueues() {
        // Given
        UUID ownerId = UUID.randomUUID();
        
        // When
        List<Queue> queues = queueRepository.findByOwnerId(ownerId);
        
        // Then
        assertThat(queues).isEmpty();
    }
    
    @Test
    void testFindByOwnerIdAndName_ShouldReturnQueue() {
        // Given
        UUID ownerId = UUID.randomUUID();
        Queue queue = createQueue("test-queue", ownerId);
        queueRepository.save(queue);
        
        // When
        Optional<Queue> found = queueRepository.findByOwnerIdAndName(ownerId, "test-queue");
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("test-queue");
        assertThat(found.get().getOwnerId()).isEqualTo(ownerId);
    }
    
    @Test
    void testFindByOwnerIdAndName_ShouldReturnEmptyWhenNotFound() {
        // Given
        UUID ownerId = UUID.randomUUID();
        
        // When
        Optional<Queue> found = queueRepository.findByOwnerIdAndName(ownerId, "nonexistent");
        
        // Then
        assertThat(found).isEmpty();
    }
    
    @Test
    void testFindByOwnerIdAndName_ShouldNotReturnQueueFromDifferentOwner() {
        // Given
        UUID ownerId1 = UUID.randomUUID();
        UUID ownerId2 = UUID.randomUUID();
        Queue queue = createQueue("test-queue", ownerId1);
        queueRepository.save(queue);
        
        // When
        Optional<Queue> found = queueRepository.findByOwnerIdAndName(ownerId2, "test-queue");
        
        // Then
        assertThat(found).isEmpty();
    }
    
    @Test
    void testDeleteQueue_ShouldRemoveQueue() {
        // Given
        UUID ownerId = UUID.randomUUID();
        Queue queue = createQueue("test-queue", ownerId);
        Queue saved = queueRepository.save(queue);
        
        // When
        queueRepository.delete(saved);
        
        // Then
        Optional<Queue> found = queueRepository.findById(saved.getId());
        assertThat(found).isEmpty();
    }
    
    private Queue createQueue(String name, UUID ownerId) {
        return Queue.builder()
                .name(name)
                .ownerId(ownerId)
                .maxRetries(3)
                .retryDelayMs(5000L)
                .build();
    }
}
