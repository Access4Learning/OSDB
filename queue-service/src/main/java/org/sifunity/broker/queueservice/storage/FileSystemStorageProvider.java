// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Filesystem-based implementation of StorageProvider.
 * Stores message payloads in the local filesystem.
 */
@Component
@ConditionalOnProperty(name = "message.storage.type", havingValue = "filesystem", matchIfMissing = true)
public class FileSystemStorageProvider implements StorageProvider {
    
    private static final Logger log = LoggerFactory.getLogger(FileSystemStorageProvider.class);
    
    private final Path basePath;
    private final ObjectMapper objectMapper;
    
    public FileSystemStorageProvider(@Value("${message.storage.filesystem.path}") String basePath,
                                    ObjectMapper objectMapper) {
        this.basePath = Paths.get(basePath);
        this.objectMapper = objectMapper;
        createBaseDirectory();
        log.info("FileSystemStorageProvider initialized with path: {}", this.basePath.toAbsolutePath());
    }
    
    private Path validateAndResolve(String storageKey) {
        Path resolved = basePath.resolve(storageKey).normalize();
        if (!resolved.startsWith(basePath.normalize())) {
            throw new StorageException("Invalid storage key: path traversal detected");
        }
        return resolved;
    }

    private void createBaseDirectory() {
        try {
            Files.createDirectories(basePath);
            log.debug("Created base storage directory: {}", basePath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create base storage directory: {}", basePath.toAbsolutePath(), e);
            throw new RuntimeException("Failed to create base storage directory: " + basePath.toAbsolutePath(), e);
        }
    }
    
    @Override
    public String store(String storageKey, Object payload) throws StorageException {
        try {
            Path filePath = validateAndResolve(storageKey);
            
            // Create parent directories if they don't exist
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            String json = objectMapper.writeValueAsString(payload);
            Files.writeString(filePath, json);
            log.debug("Stored message payload in filesystem: {}", storageKey);
            return storageKey;
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to store message to filesystem: {}", storageKey, e);
            throw new StorageException("Failed to store message to filesystem: " + storageKey, e);
        }
    }
    
    @Override
    public Optional<Object> retrieve(String storageKey) throws StorageException {
        try {
            Path filePath = validateAndResolve(storageKey);
            if (!Files.exists(filePath)) {
                log.debug("Message payload not found in filesystem: {}", storageKey);
                return Optional.empty();
            }
            
            String json = Files.readString(filePath);
            Object payload = objectMapper.readValue(json, Object.class);
            log.debug("Retrieved message payload from filesystem: {}", storageKey);
            return Optional.ofNullable(payload);
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve message from filesystem: {}", storageKey, e);
            throw new StorageException("Failed to retrieve message from filesystem: " + storageKey, e);
        }
    }
    
    @Override
    public void delete(String storageKey) throws StorageException {
        try {
            Path filePath = validateAndResolve(storageKey);
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.debug("Deleted message payload from filesystem: {}", storageKey);
            } else {
                log.debug("Message payload not found for deletion in filesystem: {}", storageKey);
            }
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete message from filesystem: {}", storageKey, e);
            throw new StorageException("Failed to delete message from filesystem: " + storageKey, e);
        }
    }
    
    @Override
    public boolean exists(String storageKey) throws StorageException {
        try {
            Path filePath = validateAndResolve(storageKey);
            boolean exists = Files.exists(filePath);
            log.debug("Message payload {} in filesystem: {}", exists ? "exists" : "does not exist", storageKey);
            return exists;
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to check message existence in filesystem: {}", storageKey, e);
            throw new StorageException("Failed to check message existence in filesystem: " + storageKey, e);
        }
    }

    @Override
    public void deleteBatch(List<String> storageKeys) {
        if (storageKeys == null || storageKeys.isEmpty()) {
            return;
        }

        for (String key : storageKeys) {
            try {
                Path filePath = validateAndResolve(key);
                boolean deleted = Files.deleteIfExists(filePath);
                if (deleted) {
                    log.debug("Batch delete: deleted file for key: {}", key);
                } else {
                    log.warn("Batch delete: file not found for key: {}", key);
                }
            } catch (StorageException e) {
                log.warn("Batch delete: path traversal detected for key: {}", key);
            } catch (IOException e) {
                log.warn("Batch delete: failed to delete file for key: {}", key, e);
            }
        }
        log.debug("Filesystem batch delete completed for {} keys", storageKeys.size());
    }

    @Override
    public long deleteByPrefix(String prefix) {
        Path targetDir = basePath.resolve(prefix).normalize();
        if (!targetDir.startsWith(basePath.normalize())) {
            throw new StorageException("Invalid storage key: path traversal detected");
        }
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            return 0L;
        }
        try (Stream<Path> walk = Files.walk(targetDir)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            long count = 0;
            for (Path p : paths) {
                try {
                    Files.delete(p);
                    count++;
                } catch (IOException e) {
                    log.warn("Failed to delete path during prefix cleanup: {}", p, e);
                }
            }
            log.info("Filesystem deleteByPrefix completed: prefix={}, deleted={}", prefix, count);
            return count;
        } catch (IOException e) {
            log.error("Failed to walk directory tree for prefix cleanup: {}", targetDir, e);
            return 0L;
        }
    }
}
