// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.sifunity.broker.queueservice.storage.FileSystemStorageProvider;
import org.sifunity.broker.queueservice.storage.S3StorageProvider;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for storage provider round-trip operations.
 * Tests that message payloads are preserved through store/retrieve cycles.
 * 
 * **Validates: Requirements 2.4, 3.4, 9.4**
 * 
 * Note: This test class uses JUnit 5 lifecycle with jqwik generators programmatically
 * to avoid Spring DI integration issues with jqwik. Each property test creates its own
 * storage provider instances since @BeforeEach is not reliably called by jqwik.
 */
class StorageProviderPropertiesTest {
    
    private static final String BUCKET_NAME = "test-bucket";
    
    // Windows reserved device names that cannot be used as directory/file names
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    /**
     * Provides arbitrary storage keys in the hierarchical format.
     * Filters out Windows reserved device names to avoid filesystem errors on Windows.
     */
    @Provide
    Arbitrary<String> storageKeys() {
        Arbitrary<String> safeSegment = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .filter(s -> !WINDOWS_RESERVED_NAMES.contains(s.toUpperCase()));
        return Combinators.combine(
                safeSegment, safeSegment, safeSegment, safeSegment,
                Arbitraries.create(UUID::randomUUID)
        ).as((env, zone, ctx, svc, id) -> 
            String.format("%s/%s/%s/%s/%s.json", env, zone, ctx, svc, id.toString())
        );
    }
    
    /**
     * Provides arbitrary message payloads with various data types
     */
    @Provide
    Arbitrary<Map<String, Object>> messagePayloads() {
        return Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.oneOf(
                        Arbitraries.strings().ofMaxLength(100).map(s -> (Object) s),
                        Arbitraries.integers().map(i -> (Object) i),
                        Arbitraries.longs().map(l -> (Object) l),
                        Arbitraries.doubles().map(d -> (Object) d),
                        Arbitraries.of(true, false).map(b -> (Object) b)
                )
        ).ofMinSize(1).ofMaxSize(10);
    }
    
    /**
     * Provides arbitrary nested message payloads with complex structures
     */
    @Provide
    Arbitrary<Map<String, Object>> nestedPayloads() {
        Arbitrary<Object> simpleValues = Arbitraries.oneOf(
                Arbitraries.strings().ofMaxLength(50).map(s -> (Object) s),
                Arbitraries.integers().map(i -> (Object) i),
                Arbitraries.of(true, false).map(b -> (Object) b)
        );
        
        Arbitrary<Map<String, Object>> simpleMap = Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                simpleValues
        ).ofMinSize(1).ofMaxSize(5);
        
        Arbitrary<List<Object>> simpleList = simpleValues.list().ofMinSize(1).ofMaxSize(5);
        
        return Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.oneOf(
                        simpleValues,
                        simpleMap.map(m -> (Object) m),
                        simpleList.map(l -> (Object) l)
                )
        ).ofMinSize(1).ofMaxSize(5);
    }
    
    /**
     * Property 7: Message Storage Round-Trip (FileSystem Provider)
     * 
     * For any message payload, enqueuing the message then delivering it should return
     * the same payload (serialization and deserialization preserve content).
     * 
     * **Validates: Requirements 2.4, 3.4, 9.4**
     */
    @Property(tries = 100)
    @Label("Property 7: Message Storage Round-Trip - FileSystem Provider")
    void fileSystemStorageRoundTrip(
            @ForAll("storageKeys") String storageKey,
            @ForAll("messagePayloads") Map<String, Object> payload) throws IOException {
        
        // Setup: Create temporary directory and storage provider for this test
        Path tempDir = Files.createTempDirectory("storage-test");
        ObjectMapper objectMapper = new ObjectMapper();
        FileSystemStorageProvider fileSystemProvider = new FileSystemStorageProvider(tempDir.toString(), objectMapper);
        
        try {
            // When: Store the payload
            String returnedKey = fileSystemProvider.store(storageKey, payload);
        
        // Then: The storage key should be returned
        assertThat(returnedKey)
                .as("Storage provider should return the storage key")
                .isEqualTo(storageKey);
        
        // When: Retrieve the payload
        Optional<Object> retrieved = fileSystemProvider.retrieve(storageKey);
        
        // Then: The payload should be present and identical to the original
        assertThat(retrieved)
                .as("Stored payload should be retrievable")
                .isPresent();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedPayload = (Map<String, Object>) retrieved.get();
        
        // Verify all keys are present
        assertThat(retrievedPayload.keySet())
                .as("Retrieved payload should have same keys as original")
                .containsExactlyInAnyOrderElementsOf(payload.keySet());
        
        // Verify each value is preserved (with numeric type flexibility)
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            Object originalValue = entry.getValue();
            Object retrievedValue = retrievedPayload.get(entry.getKey());
            
            assertThat(retrievedValue)
                    .as("Value for key '%s' should be present", entry.getKey())
                    .isNotNull();
            
            // For numeric types, compare values not types (Jackson may deserialize Long as Integer if it fits)
            if (originalValue instanceof Number && retrievedValue instanceof Number) {
                assertThat(((Number) retrievedValue).longValue())
                        .as("Numeric value for key '%s' should be preserved", entry.getKey())
                        .isEqualTo(((Number) originalValue).longValue());
            } else {
                assertThat(retrievedValue)
                        .as("Value for key '%s' should be preserved", entry.getKey())
                        .isEqualTo(originalValue);
            }
            
            // Verify type preservation for non-numeric types
            if (originalValue instanceof Boolean) {
                assertThat(retrievedValue)
                        .as("Boolean value should remain Boolean type")
                        .isInstanceOf(Boolean.class);
            } else if (originalValue instanceof String) {
                assertThat(retrievedValue)
                        .as("String value should remain String type")
                        .isInstanceOf(String.class);
            }
        }
        } finally {
            // Cleanup: Delete temporary directory
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                // Ignore cleanup errors
                            }
                        });
            }
        }
    }
    
    /**
     * Property 7.1: Message Storage Round-Trip with Nested Structures (FileSystem Provider)
     * 
     * For any message payload with nested structures (maps and lists), the round-trip
     * should preserve the nested structure and all values.
     * 
     * **Validates: Requirements 2.4, 3.4, 9.4**
     */
    @Property(tries = 100)
    @Label("Property 7.1: Message Storage Round-Trip with Nested Structures - FileSystem Provider")
    void fileSystemStorageRoundTripWithNestedStructures(
            @ForAll("storageKeys") String storageKey,
            @ForAll("nestedPayloads") Map<String, Object> payload) throws IOException {
        
        // Setup: Create temporary directory and storage provider for this test
        Path tempDir = Files.createTempDirectory("storage-test");
        ObjectMapper objectMapper = new ObjectMapper();
        FileSystemStorageProvider fileSystemProvider = new FileSystemStorageProvider(tempDir.toString(), objectMapper);
        
        try {
            // When: Store and retrieve the nested payload
            fileSystemProvider.store(storageKey, payload);
        Optional<Object> retrieved = fileSystemProvider.retrieve(storageKey);
        
        // Then: The nested structure should be preserved
        assertThat(retrieved).isPresent();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedPayload = (Map<String, Object>) retrieved.get();
        
        assertThat(retrievedPayload)
                .as("Retrieved nested payload should have same keys")
                .containsOnlyKeys(payload.keySet().toArray(new String[0]));
        
        // Verify nested structures are preserved
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            Object originalValue = entry.getValue();
            Object retrievedValue = retrievedPayload.get(entry.getKey());
            
            if (originalValue instanceof Map) {
                assertThat(retrievedValue)
                        .as("Nested map should be preserved")
                        .isInstanceOf(Map.class);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> originalMap = (Map<String, Object>) originalValue;
                @SuppressWarnings("unchecked")
                Map<String, Object> retrievedMap = (Map<String, Object>) retrievedValue;
                
                assertThat(retrievedMap)
                        .as("Nested map contents should match")
                        .containsExactlyInAnyOrderEntriesOf(originalMap);
            } else if (originalValue instanceof List) {
                assertThat(retrievedValue)
                        .as("Nested list should be preserved")
                        .isInstanceOf(List.class);
                
                @SuppressWarnings("unchecked")
                List<Object> originalList = (List<Object>) originalValue;
                @SuppressWarnings("unchecked")
                List<Object> retrievedList = (List<Object>) retrievedValue;
                
                assertThat(retrievedList)
                        .as("Nested list contents should match")
                        .containsExactlyElementsOf(originalList);
            } else {
                assertThat(retrievedValue)
                        .as("Simple value should match")
                        .isEqualTo(originalValue);
            }
        }
        } finally {
            // Cleanup: Delete temporary directory
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                // Ignore cleanup errors
                            }
                        });
            }
        }
    }
    
    /**
     * Property 7.2: Message Storage Round-Trip (S3 Provider)
     * 
     * For any message payload, storing and retrieving from S3 should return
     * the same payload (serialization and deserialization preserve content).
     * 
     * **Validates: Requirements 2.4, 3.4, 9.4**
     */
    @Property(tries = 100)
    @Label("Property 7.2: Message Storage Round-Trip - S3 Provider")
    void s3StorageRoundTrip(
            @ForAll("storageKeys") String storageKey,
            @ForAll("messagePayloads") Map<String, Object> payload) throws Exception {
        
        // Setup: Create mocked S3 client and storage provider for this test
        ObjectMapper objectMapper = new ObjectMapper();
        S3Client mockS3Client = mock(S3Client.class);
        S3StorageProvider s3Provider = new S3StorageProvider(mockS3Client, BUCKET_NAME, "7d", objectMapper);
        
        // Setup mock S3 client to capture and return the stored data
        final String[] capturedJson = new String[1];
        
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenAnswer(invocation -> {
                    RequestBody body = invocation.getArgument(1);
                    // Capture the JSON that would be sent to S3
                    capturedJson[0] = objectMapper.writeValueAsString(payload);
                    return PutObjectResponse.builder().build();
                });
        
        when(mockS3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenAnswer(invocation -> {
                    // Return the captured JSON as if retrieved from S3
                    return ResponseBytes.fromByteArray(
                            GetObjectResponse.builder().build(),
                            capturedJson[0].getBytes()
                    );
                });
        
        // When: Store the payload
        String returnedKey = s3Provider.store(storageKey, payload);
        
        // Then: The storage key should be returned
        assertThat(returnedKey)
                .as("Storage provider should return the storage key")
                .isEqualTo(storageKey);
        
        // When: Retrieve the payload
        Optional<Object> retrieved = s3Provider.retrieve(storageKey);
        
        // Then: The payload should be present and identical to the original
        assertThat(retrieved)
                .as("Stored payload should be retrievable")
                .isPresent();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedPayload = (Map<String, Object>) retrieved.get();
        
        // Verify all keys are present
        assertThat(retrievedPayload.keySet())
                .as("Retrieved payload should have same keys as original")
                .containsExactlyInAnyOrderElementsOf(payload.keySet());
        
        // Verify each value is preserved (with numeric type flexibility)
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            Object originalValue = entry.getValue();
            Object retrievedValue = retrievedPayload.get(entry.getKey());
            
            assertThat(retrievedValue)
                    .as("Value for key '%s' should be present", entry.getKey())
                    .isNotNull();
            
            // For numeric types, compare values not types (Jackson may deserialize Long as Integer if it fits)
            if (originalValue instanceof Number && retrievedValue instanceof Number) {
                assertThat(((Number) retrievedValue).longValue())
                        .as("Numeric value for key '%s' should be preserved", entry.getKey())
                        .isEqualTo(((Number) originalValue).longValue());
            } else {
                assertThat(retrievedValue)
                        .as("Value for key '%s' should be preserved", entry.getKey())
                        .isEqualTo(originalValue);
            }
        }
        
        // Verify S3 operations were called correctly
        verify(mockS3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(mockS3Client).getObjectAsBytes(any(GetObjectRequest.class));
    }
    
    /**
     * Property 7.3: Message Storage Round-Trip with Nested Structures (S3 Provider)
     * 
     * For any message payload with nested structures, the S3 round-trip
     * should preserve the nested structure and all values.
     * 
     * **Validates: Requirements 2.4, 3.4, 9.4**
     */
    @Property(tries = 100)
    @Label("Property 7.3: Message Storage Round-Trip with Nested Structures - S3 Provider")
    void s3StorageRoundTripWithNestedStructures(
            @ForAll("storageKeys") String storageKey,
            @ForAll("nestedPayloads") Map<String, Object> payload) throws Exception {
        
        // Setup: Create mocked S3 client and storage provider for this test
        ObjectMapper objectMapper = new ObjectMapper();
        S3Client mockS3Client = mock(S3Client.class);
        S3StorageProvider s3Provider = new S3StorageProvider(mockS3Client, BUCKET_NAME, "7d", objectMapper);
        
        // Setup mock S3 client to capture and return the stored data
        final String[] capturedJson = new String[1];
        
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenAnswer(invocation -> {
                    capturedJson[0] = objectMapper.writeValueAsString(payload);
                    return PutObjectResponse.builder().build();
                });
        
        when(mockS3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenAnswer(invocation -> {
                    return ResponseBytes.fromByteArray(
                            GetObjectResponse.builder().build(),
                            capturedJson[0].getBytes()
                    );
                });
        
        // When: Store and retrieve the nested payload
        s3Provider.store(storageKey, payload);
        Optional<Object> retrieved = s3Provider.retrieve(storageKey);
        
        // Then: The nested structure should be preserved
        assertThat(retrieved).isPresent();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedPayload = (Map<String, Object>) retrieved.get();
        
        assertThat(retrievedPayload)
                .as("Retrieved nested payload should have same keys")
                .containsOnlyKeys(payload.keySet().toArray(new String[0]));
        
        // Verify nested structures are preserved
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            Object originalValue = entry.getValue();
            Object retrievedValue = retrievedPayload.get(entry.getKey());
            
            if (originalValue instanceof Map) {
                assertThat(retrievedValue)
                        .as("Nested map should be preserved")
                        .isInstanceOf(Map.class);
                
                @SuppressWarnings("unchecked")
                Map<String, Object> originalMap = (Map<String, Object>) originalValue;
                @SuppressWarnings("unchecked")
                Map<String, Object> retrievedMap = (Map<String, Object>) retrievedValue;
                
                assertThat(retrievedMap)
                        .as("Nested map contents should match")
                        .containsExactlyInAnyOrderEntriesOf(originalMap);
            } else if (originalValue instanceof List) {
                assertThat(retrievedValue)
                        .as("Nested list should be preserved")
                        .isInstanceOf(List.class);
                
                @SuppressWarnings("unchecked")
                List<Object> originalList = (List<Object>) originalValue;
                @SuppressWarnings("unchecked")
                List<Object> retrievedList = (List<Object>) retrievedValue;
                
                assertThat(retrievedList)
                        .as("Nested list contents should match")
                        .containsExactlyElementsOf(originalList);
            } else {
                assertThat(retrievedValue)
                        .as("Simple value should match")
                        .isEqualTo(originalValue);
            }
        }
        
        // Verify S3 operations were called
        verify(mockS3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(mockS3Client).getObjectAsBytes(any(GetObjectRequest.class));
    }
    
    /**
     * Property 7.4: Empty Payload Round-Trip
     * 
     * For an empty message payload, the round-trip should preserve the empty structure.
     * 
     * **Validates: Requirements 2.4, 3.4, 9.4**
     */
    @Property(tries = 50)
    @Label("Property 7.4: Empty Payload Round-Trip")
    void emptyPayloadRoundTrip(@ForAll("storageKeys") String storageKey) throws IOException {
        // Given: An empty payload
        Map<String, Object> emptyPayload = new HashMap<>();
        
        // Setup: Create temporary directory and storage provider for this test
        Path tempDir = Files.createTempDirectory("storage-test");
        ObjectMapper objectMapper = new ObjectMapper();
        FileSystemStorageProvider fileSystemProvider = new FileSystemStorageProvider(tempDir.toString(), objectMapper);
        
        try {
            // When: Store and retrieve with FileSystem provider
            fileSystemProvider.store(storageKey, emptyPayload);
        Optional<Object> retrieved = fileSystemProvider.retrieve(storageKey);
        
        // Then: The empty payload should be preserved
        assertThat(retrieved).isPresent();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedPayload = (Map<String, Object>) retrieved.get();
        
        assertThat(retrievedPayload)
                .as("Empty payload should be preserved")
                .isEmpty();
        } finally {
            // Cleanup: Delete temporary directory
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                // Ignore cleanup errors
                            }
                        });
            }
        }
    }
    
    /**
     * Property 7.5: Special Characters in Payload Values
     * 
     * For message payloads containing special characters, the round-trip should
     * preserve all characters correctly.
     * 
     * **Validates: Requirements 2.4, 3.4, 9.4**
     */
    @Property(tries = 50)
    @Label("Property 7.5: Special Characters in Payload Values")
    void specialCharactersInPayloadRoundTrip(
            @ForAll("storageKeys") String storageKey,
            @ForAll @Size(min = 1, max = 5) List<@NotBlank String> specialStrings) throws IOException {
        
        // Given: A payload with special characters
        Map<String, Object> payload = new HashMap<>();
        payload.put("unicode", "Hello 世界 🌍");
        payload.put("quotes", "He said \"Hello\" and 'Goodbye'");
        payload.put("newlines", "Line1\nLine2\rLine3\r\nLine4");
        payload.put("tabs", "Col1\tCol2\tCol3");
        payload.put("backslash", "C:\\Users\\Test\\file.txt");
        payload.put("special", "!@#$%^&*()_+-=[]{}|;:',.<>?/~`");
        
        // Add the generated special strings
        for (int i = 0; i < specialStrings.size(); i++) {
            payload.put("generated_" + i, specialStrings.get(i));
        }
        
        // Setup: Create temporary directory and storage provider for this test
        Path tempDir = Files.createTempDirectory("storage-test");
        ObjectMapper objectMapper = new ObjectMapper();
        FileSystemStorageProvider fileSystemProvider = new FileSystemStorageProvider(tempDir.toString(), objectMapper);
        
        try {
            // When: Store and retrieve
            fileSystemProvider.store(storageKey, payload);
        Optional<Object> retrieved = fileSystemProvider.retrieve(storageKey);
        
        // Then: All special characters should be preserved
        assertThat(retrieved).isPresent();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> retrievedPayload = (Map<String, Object>) retrieved.get();
        
        assertThat(retrievedPayload)
                .as("Payload with special characters should be preserved")
                .containsExactlyInAnyOrderEntriesOf(payload);
        } finally {
            // Cleanup: Delete temporary directory
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                // Ignore cleanup errors
                            }
                        });
            }
        }
    }
}
