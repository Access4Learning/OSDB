// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * S3-based implementation of StorageProvider.
 * Stores message payloads in AWS S3.
 */
@Component
@ConditionalOnProperty(name = "message.storage.type", havingValue = "s3")
public class S3StorageProvider implements StorageProvider {
    
    private static final Logger log = LoggerFactory.getLogger(S3StorageProvider.class);
    
    private final S3Client s3Client;
    private final String bucketName;
    private final ObjectMapper objectMapper;
    private final Duration payloadTtl;
    
    public S3StorageProvider(S3Client s3Client,
                            @Value("${message.storage.s3.bucket}") String bucketName,
                            @Value("${message.storage.payload-ttl:7d}") String payloadTtlStr,
                            ObjectMapper objectMapper) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.objectMapper = objectMapper;
        this.payloadTtl = parseDuration(payloadTtlStr);
        log.info("S3StorageProvider initialized with bucket: {}, payload-ttl: {}", bucketName, payloadTtl);
    }
    
    /**
     * Parse a duration string like "7d", "24h", "30m" into a {@link Duration}.
     * Supports 'd' (days), 'h' (hours), 'm' (minutes), 's' (seconds).
     * Falls back to ISO-8601 parsing if no suffix matches.
     */
    static public Duration parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ofDays(7);
        }
        String trimmed = value.trim();
        if (trimmed.endsWith("d")) {
            return Duration.ofDays(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
        } else if (trimmed.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
        } else if (trimmed.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
        } else if (trimmed.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
        }
        // Fallback to ISO-8601 (e.g., "PT168H")
        return Duration.parse(trimmed);
    }
    
    @Override
    public String store(String storageKey, Object payload) throws StorageException {
        try {
            String json = objectMapper.writeValueAsString(payload);
            Instant expires = Instant.now().plus(payloadTtl);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .contentType("application/json")
                    .expires(expires)
                    .build();
            
            s3Client.putObject(request, RequestBody.fromString(json));
            log.debug("Stored message payload in S3: {} (expires: {})", storageKey, expires);
            return storageKey;
        } catch (Exception e) {
            log.error("Failed to store message in S3: {}", storageKey, e);
            throw new StorageException("Failed to store message in S3: " + storageKey, e);
        }
    }
    
    @Override
    public Optional<Object> retrieve(String storageKey) throws StorageException {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();
            
            String json = s3Client.getObjectAsBytes(request).asUtf8String();
            Object payload = objectMapper.readValue(json, Object.class);
            log.debug("Retrieved message payload from S3: {}", storageKey);
            return Optional.of(payload);
        } catch (NoSuchKeyException e) {
            log.debug("Message payload not found in S3: {}", storageKey);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to retrieve message from S3: {}", storageKey, e);
            throw new StorageException("Failed to retrieve message from S3: " + storageKey, e);
        }
    }
    
    @Override
    public void delete(String storageKey) throws StorageException {
        CompletableFuture.runAsync(() -> {
            try {
                DeleteObjectRequest request = DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(storageKey)
                        .build();
                
                s3Client.deleteObject(request);
                log.debug("Deleted message payload from S3: {}", storageKey);
            } catch (Exception e) {
                log.warn("Failed to delete message from S3 (async, best-effort): {}", storageKey, e);
            }
        });
    }
    
    @Override
    public boolean exists(String storageKey) throws StorageException {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();
            
            s3Client.headObject(request);
            log.debug("Message payload exists in S3: {}", storageKey);
            return true;
        } catch (NoSuchKeyException e) {
            log.debug("Message payload does not exist in S3: {}", storageKey);
            return false;
        } catch (Exception e) {
            log.error("Failed to check message existence in S3: {}", storageKey, e);
            throw new StorageException("Failed to check message existence in S3: " + storageKey, e);
        }
    }

    @Override
    public void deleteBatch(List<String> storageKeys) {
        if (storageKeys == null || storageKeys.isEmpty()) {
            return;
        }

        List<ObjectIdentifier> objects = storageKeys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();

        DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete(Delete.builder().objects(objects).quiet(true).build())
                .build();

        try {
            DeleteObjectsResponse response = s3Client.deleteObjects(request);
            if (response.hasErrors() && !response.errors().isEmpty()) {
                for (S3Error error : response.errors()) {
                    log.warn("S3 batch delete error for key {}: {} - {}",
                            error.key(), error.code(), error.message());
                }
            }
            log.debug("S3 batch delete completed for {} keys", storageKeys.size());
        } catch (Exception e) {
            log.error("Failed to execute S3 batch delete for {} keys", storageKeys.size(), e);
            throw new StorageException("Failed to execute S3 batch delete", e);
        }
    }

    @Override
    public long deleteByPrefix(String prefix) {
        long totalDeleted = 0;
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder listBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .maxKeys(1000);
            if (continuationToken != null) {
                listBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listBuilder.build());
            List<ObjectIdentifier> objects = listResponse.contents().stream()
                    .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                    .toList();

            if (!objects.isEmpty()) {
                DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(Delete.builder().objects(objects).quiet(true).build())
                        .build();
                s3Client.deleteObjects(deleteRequest);
                totalDeleted += objects.size();
            }

            continuationToken = listResponse.isTruncated() ? listResponse.nextContinuationToken() : null;
        } while (continuationToken != null);

        log.info("S3 deleteByPrefix completed: prefix={}, deleted={}", prefix, totalDeleted);
        return totalDeleted;
    }
}
