// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sifunity.broker.queueservice.storage.FileSystemStorageProvider;
import org.sifunity.broker.queueservice.storage.StorageException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FileSystemStorageProvider using temporary directories.
 */
class FileSystemStorageProviderTest {
    
    @TempDir
    Path tempDir;
    
    private ObjectMapper objectMapper;
    private FileSystemStorageProvider storageProvider;
    
    private static final String STORAGE_KEY = "env/zone/context/service/message-id.json";
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        storageProvider = new FileSystemStorageProvider(tempDir.toString(), objectMapper);
    }
    
    @AfterEach
    void tearDown() throws IOException {
        // Clean up test files
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try {
                            if (!path.equals(tempDir)) {
                                Files.deleteIfExists(path);
                            }
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
        }
    }
    
    @Test
    void testStore_ShouldStorePayloadInFilesystem() throws Exception {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        payload.put("number", 42);
        
        // When
        String result = storageProvider.store(STORAGE_KEY, payload);
        
        // Then
        assertThat(result).isEqualTo(STORAGE_KEY);
        
        Path filePath = tempDir.resolve(STORAGE_KEY);
        assertThat(Files.exists(filePath)).isTrue();
        
        String json = Files.readString(filePath);
        @SuppressWarnings("unchecked")
        Map<String, Object> storedPayload = objectMapper.readValue(json, Map.class);
        assertThat(storedPayload).containsEntry("key", "value");
        assertThat(storedPayload).containsEntry("number", 42);
    }
    
    @Test
    void testStore_ShouldCreateParentDirectories() {
        // Given
        String nestedKey = "level1/level2/level3/message.json";
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        
        // When
        String result = storageProvider.store(nestedKey, payload);
        
        // Then
        assertThat(result).isEqualTo(nestedKey);
        
        Path filePath = tempDir.resolve(nestedKey);
        assertThat(Files.exists(filePath)).isTrue();
        assertThat(Files.exists(filePath.getParent())).isTrue();
    }
    
    @Test
    void testRetrieve_ShouldReturnPayloadWhenExists() throws Exception {
        // Given
        Map<String, Object> expectedPayload = new HashMap<>();
        expectedPayload.put("key", "value");
        expectedPayload.put("number", 42);
        
        // Store the payload first
        storageProvider.store(STORAGE_KEY, expectedPayload);
        
        // When
        Optional<Object> result = storageProvider.retrieve(STORAGE_KEY);
        
        // Then
        assertThat(result).isPresent();
        @SuppressWarnings("unchecked")
        Map<String, Object> actualPayload = (Map<String, Object>) result.get();
        assertThat(actualPayload).containsEntry("key", "value");
        assertThat(actualPayload).containsEntry("number", 42);
    }
    
    @Test
    void testRetrieve_ShouldReturnEmptyWhenNotFound() {
        // Given
        String nonExistentKey = "nonexistent/message.json";
        
        // When
        Optional<Object> result = storageProvider.retrieve(nonExistentKey);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void testRetrieve_ShouldThrowStorageExceptionOnInvalidJson() throws Exception {
        // Given
        Path filePath = tempDir.resolve(STORAGE_KEY);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "invalid json {{{");
        
        // When/Then
        assertThatThrownBy(() -> storageProvider.retrieve(STORAGE_KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to retrieve message from filesystem");
    }
    
    @Test
    void testDelete_ShouldDeletePayloadFromFilesystem() throws Exception {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        storageProvider.store(STORAGE_KEY, payload);
        
        Path filePath = tempDir.resolve(STORAGE_KEY);
        assertThat(Files.exists(filePath)).isTrue();
        
        // When
        storageProvider.delete(STORAGE_KEY);
        
        // Then
        assertThat(Files.exists(filePath)).isFalse();
    }
    
    @Test
    void testDelete_ShouldNotThrowWhenFileDoesNotExist() {
        // Given
        String nonExistentKey = "nonexistent/message.json";
        
        // When/Then - should not throw
        storageProvider.delete(nonExistentKey);
    }
    
    @Test
    void testExists_ShouldReturnTrueWhenPayloadExists() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        storageProvider.store(STORAGE_KEY, payload);
        
        // When
        boolean result = storageProvider.exists(STORAGE_KEY);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    void testExists_ShouldReturnFalseWhenPayloadNotFound() {
        // Given
        String nonExistentKey = "nonexistent/message.json";
        
        // When
        boolean result = storageProvider.exists(nonExistentKey);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void testStoreAndRetrieve_RoundTrip() {
        // Given
        Map<String, Object> originalPayload = new HashMap<>();
        originalPayload.put("string", "test");
        originalPayload.put("number", 123);
        originalPayload.put("boolean", true);
        
        Map<String, Object> nested = new HashMap<>();
        nested.put("nested_key", "nested_value");
        originalPayload.put("nested", nested);
        
        // When
        storageProvider.store(STORAGE_KEY, originalPayload);
        Optional<Object> retrieved = storageProvider.retrieve(STORAGE_KEY);
        
        // Then
        assertThat(retrieved).isPresent();
        @SuppressWarnings("unchecked")
        Map<String, Object> actualPayload = (Map<String, Object>) retrieved.get();
        assertThat(actualPayload).containsEntry("string", "test");
        assertThat(actualPayload).containsEntry("number", 123);
        assertThat(actualPayload).containsEntry("boolean", true);
        assertThat(actualPayload).containsKey("nested");
    }

    @Test
    void testDeleteByPrefix_ShouldDeleteAllFilesUnderPrefix() throws Exception {
        // Given - create files under a prefix directory
        Map<String, Object> payload = Map.of("key", "value");
        storageProvider.store("domainA/sol1/file1.json", payload);
        storageProvider.store("domainA/sol1/file2.json", payload);
        storageProvider.store("domainA/sol1/sub/file3.json", payload);
        storageProvider.store("domainA/sol2/file4.json", payload);

        // When - delete by prefix "domainA/sol1"
        long deleted = storageProvider.deleteByPrefix("domainA/sol1");

        // Then - all files under domainA/sol1 deleted (3 files + 1 subdir + 1 dir = 5 paths)
        assertThat(deleted).isGreaterThanOrEqualTo(3);
        assertThat(Files.exists(tempDir.resolve("domainA/sol1"))).isFalse();
        // domainA/sol2 should still exist
        assertThat(Files.exists(tempDir.resolve("domainA/sol2/file4.json"))).isTrue();
    }

    @Test
    void testDeleteByPrefix_ShouldReturnZeroForNonExistentPrefix() {
        // When
        long deleted = storageProvider.deleteByPrefix("nonexistent/prefix");

        // Then
        assertThat(deleted).isEqualTo(0L);
    }

    @Test
    void testDeleteByPrefix_ShouldReturnZeroForFileNotDirectory() throws Exception {
        // Given - create a file (not a directory) at the prefix path
        Map<String, Object> payload = Map.of("key", "value");
        storageProvider.store("somefile.json", payload);

        // When - try to deleteByPrefix on a file path
        long deleted = storageProvider.deleteByPrefix("somefile.json");

        // Then - returns 0 since it's not a directory
        assertThat(deleted).isEqualTo(0L);
    }

    @Test
    void testDeleteByPrefix_ShouldDeleteEntireDomain() throws Exception {
        // Given - create files across multiple solutions
        Map<String, Object> payload = Map.of("key", "value");
        storageProvider.store("domainX/sol1/msg1.json", payload);
        storageProvider.store("domainX/sol1/msg2.json", payload);
        storageProvider.store("domainX/sol2/msg3.json", payload);
        storageProvider.store("domainY/sol1/msg4.json", payload);

        // When - delete entire domainX
        long deleted = storageProvider.deleteByPrefix("domainX");

        // Then
        assertThat(deleted).isGreaterThanOrEqualTo(3); // at least 3 files
        assertThat(Files.exists(tempDir.resolve("domainX"))).isFalse();
        // domainY should be untouched
        assertThat(Files.exists(tempDir.resolve("domainY/sol1/msg4.json"))).isTrue();
    }
}
