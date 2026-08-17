// SPDX-License-Identifier: MIT
package org.sifunity.integration.util;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.integration.generator.*;
import org.springframework.data.redis.core.RedisTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for cleaning up test data after integration tests.
 * 
 * <p>This class provides methods to:
 * <ul>
 *   <li>Delete test data in reverse dependency order</li>
 *   <li>Clear Redis cache</li>
 *   <li>Clear S3 bucket contents</li>
 *   <li>Track created resources for cleanup</li>
 * </ul>
 * 
 * <p>Cleanup order (reverse of creation dependencies):
 * <ol>
 *   <li>Subscriptions (depend on queues)</li>
 *   <li>Queues (depend on environments)</li>
 *   <li>ACL entries (depend on environments)</li>
 *   <li>Provider registrations (depend on environments)</li>
 *   <li>Environments (foundational)</li>
 *   <li>Redis cache</li>
 *   <li>S3 bucket contents</li>
 * </ol>
 * 
 * <p>Example usage:
 * <pre>
 * TestDataCleanup cleanup = new TestDataCleanup(
 *     environmentGenerator,
 *     aclGenerator,
 *     providerGenerator,
 *     subscriptionGenerator,
 *     redisTemplate,
 *     s3Client,
 *     "test-bucket"
 * );
 * 
 * // Track created resources
 * cleanup.trackEnvironment(environmentId);
 * cleanup.trackQueue(queueId);
 * cleanup.trackSubscription(subscriptionId);
 * 
 * // Clean up all tracked resources
 * cleanup.cleanupAll();
 * 
 * // Or clean up specific resource types
 * cleanup.cleanupSubscriptions();
 * cleanup.cleanupEnvironments();
 * </pre>
 * 
 * @since 1.0.0
 */
@Slf4j
public class TestDataCleanup {
    
    private final EnvironmentGenerator environmentGenerator;
    private final AclGenerator aclGenerator;
    private final ProviderGenerator providerGenerator;
    private final SubscriptionGenerator subscriptionGenerator;
    private final RedisTemplate<String, Object> redisTemplate;
    private final S3Client s3Client;
    private final String s3BucketName;
    
    // Track created resources for cleanup
    private final List<UUID> createdEnvironments = new ArrayList<>();
    private final List<UUID> createdQueues = new ArrayList<>();
    private final List<UUID> createdSubscriptions = new ArrayList<>();
    private final List<UUID> createdAclEntries = new ArrayList<>();
    private final List<UUID> createdProviders = new ArrayList<>();
    
    /**
     * Creates a new test data cleanup utility.
     * 
     * @param environmentGenerator the environment generator
     * @param aclGenerator the ACL generator
     * @param providerGenerator the provider generator
     * @param subscriptionGenerator the subscription generator
     * @param redisTemplate the Redis template
     * @param s3Client the S3 client
     * @param s3BucketName the S3 bucket name
     */
    public TestDataCleanup(
        EnvironmentGenerator environmentGenerator,
        AclGenerator aclGenerator,
        ProviderGenerator providerGenerator,
        SubscriptionGenerator subscriptionGenerator,
        RedisTemplate<String, Object> redisTemplate,
        S3Client s3Client,
        String s3BucketName
    ) {
        this.environmentGenerator = environmentGenerator;
        this.aclGenerator = aclGenerator;
        this.providerGenerator = providerGenerator;
        this.subscriptionGenerator = subscriptionGenerator;
        this.redisTemplate = redisTemplate;
        this.s3Client = s3Client;
        this.s3BucketName = s3BucketName;
    }
    
    /**
     * Tracks an environment for cleanup.
     * 
     * @param environmentId the environment ID
     */
    public void trackEnvironment(UUID environmentId) {
        createdEnvironments.add(environmentId);
        log.debug("Tracking environment for cleanup: {}", environmentId);
    }
    
    /**
     * Tracks a queue for cleanup.
     * 
     * @param queueId the queue ID
     */
    public void trackQueue(UUID queueId) {
        createdQueues.add(queueId);
        log.debug("Tracking queue for cleanup: {}", queueId);
    }
    
    /**
     * Tracks a subscription for cleanup.
     * 
     * @param subscriptionId the subscription ID
     */
    public void trackSubscription(UUID subscriptionId) {
        createdSubscriptions.add(subscriptionId);
        log.debug("Tracking subscription for cleanup: {}", subscriptionId);
    }
    
    /**
     * Tracks an ACL entry for cleanup.
     * 
     * @param aclEntryId the ACL entry ID
     */
    public void trackAclEntry(UUID aclEntryId) {
        createdAclEntries.add(aclEntryId);
        log.debug("Tracking ACL entry for cleanup: {}", aclEntryId);
    }
    
    /**
     * Tracks a provider registration for cleanup.
     * 
     * @param providerId the provider registration ID
     */
    public void trackProvider(UUID providerId) {
        createdProviders.add(providerId);
        log.debug("Tracking provider for cleanup: {}", providerId);
    }
    
    /**
     * Cleans up all tracked resources in reverse dependency order.
     */
    public void cleanupAll() {
        log.info("Starting cleanup of all tracked resources");
        
        cleanupSubscriptions();
        cleanupQueues();
        cleanupAclEntries();
        cleanupProviders();
        cleanupEnvironments();
        clearRedisCache();
        clearS3Bucket();
        
        log.info("Cleanup completed");
    }
    
    /**
     * Cleans up all tracked subscriptions.
     */
    public void cleanupSubscriptions() {
        log.info("Cleaning up {} subscriptions", createdSubscriptions.size());
        
        for (UUID subscriptionId : createdSubscriptions) {
            try {
                subscriptionGenerator.deleteSubscription(subscriptionId);
            } catch (Exception e) {
                log.warn("Failed to delete subscription {}: {}", subscriptionId, e.getMessage());
            }
        }
        
        createdSubscriptions.clear();
    }
    
    /**
     * Cleans up all tracked queues.
     * Note: Queue deletion should be handled by the Queue Service API.
     * This is a placeholder for when that API is available.
     */
    public void cleanupQueues() {
        log.info("Cleaning up {} queues", createdQueues.size());
        
        // TODO: Implement queue deletion when Queue Service API is available
        // For now, queues will be cleaned up when their owner environments are deleted
        
        createdQueues.clear();
    }
    
    /**
     * Cleans up all tracked ACL entries.
     * Note: This method cannot be used because ACL deletion requires authentication.
     * Tests should delete ACL entries directly using AclGenerator with proper credentials.
     */
    @Deprecated
    public void cleanupAclEntries() {
        log.warn("cleanupAclEntries() is deprecated - ACL entries require authentication for deletion");
        log.warn("Tests should delete ACL entries directly using AclGenerator with sessionToken and sharedSecret");
        createdAclEntries.clear();
    }
    
    /**
     * Cleans up all tracked provider registrations.
     * Note: This method cannot be used because provider deletion requires authentication.
     * Tests should delete provider registrations directly using ProviderGenerator with proper credentials.
     */
    @Deprecated
    public void cleanupProviders() {
        log.warn("cleanupProviders() is deprecated - provider registrations require authentication for deletion");
        log.warn("Tests should delete provider registrations directly using ProviderGenerator with sessionToken and sharedSecret");
        createdProviders.clear();
    }
    
    /**
     * Cleans up all tracked environments.
     * This will cascade delete related ACL entries, providers, and queues.
     */
    public void cleanupEnvironments() {
        log.info("Cleaning up {} environments", createdEnvironments.size());
        
        for (UUID environmentId : createdEnvironments) {
            try {
                environmentGenerator.deleteEnvironment(environmentId);
            } catch (Exception e) {
                log.warn("Failed to delete environment {}: {}", environmentId, e.getMessage());
            }
        }
        
        createdEnvironments.clear();
    }
    
    /**
     * Clears all keys from the Redis cache.
     */
    public void clearRedisCache() {
        log.info("Clearing Redis cache");
        
        try {
            // Flush the current database
            redisTemplate.getConnectionFactory().getConnection().flushDb();
            log.info("Redis cache cleared");
        } catch (Exception e) {
            log.warn("Failed to clear Redis cache: {}", e.getMessage());
        }
    }
    
    /**
     * Clears all objects from the S3 test bucket.
     */
    public void clearS3Bucket() {
        log.info("Clearing S3 bucket: {}", s3BucketName);
        
        try {
            // List all objects in the bucket
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(s3BucketName)
                .build();
            
            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            
            // Delete each object
            int deletedCount = 0;
            for (S3Object s3Object : listResponse.contents()) {
                try {
                    DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                        .bucket(s3BucketName)
                        .key(s3Object.key())
                        .build();
                    
                    s3Client.deleteObject(deleteRequest);
                    deletedCount++;
                } catch (Exception e) {
                    log.warn("Failed to delete S3 object {}: {}", s3Object.key(), e.getMessage());
                }
            }
            
            log.info("S3 bucket cleared: {} objects deleted", deletedCount);
            
        } catch (Exception e) {
            log.warn("Failed to clear S3 bucket: {}", e.getMessage());
        }
    }
    
    /**
     * Clears all tracked resources without deleting them.
     * Useful when resources have already been deleted by other means.
     */
    public void clearTracking() {
        createdEnvironments.clear();
        createdQueues.clear();
        createdSubscriptions.clear();
        createdAclEntries.clear();
        createdProviders.clear();
        log.debug("Cleared all resource tracking");
    }
    
    /**
     * Gets the count of tracked resources.
     * 
     * @return total count of tracked resources
     */
    public int getTrackedResourceCount() {
        return createdEnvironments.size() +
               createdQueues.size() +
               createdSubscriptions.size() +
               createdAclEntries.size() +
               createdProviders.size();
    }
}
