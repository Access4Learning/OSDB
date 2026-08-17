// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.queueservice.storage.FileSystemStorageProvider;
import org.sifunity.broker.queueservice.storage.StorageException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bug Condition Exploration Test for Path Traversal in FileSystemStorageProvider.
 *
 * <p><strong>CRITICAL: These tests MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>Bug: {@code FileSystemStorageProvider} uses {@code basePath.resolve(storageKey)} without
 * validating that the resolved path stays within basePath. Storage keys containing "../"
 * sequences escape the configured storage directory.</p>
 *
 * <p>Bug Condition: For any storageKey where {@code basePath.resolve(key).normalize()} does NOT
 * startWith {@code basePath.normalize()}, store/retrieve/delete/exists SHALL throw StorageException.</p>
 *
 * <p>On UNFIXED code: test FAILS because resolve blindly concatenates without validation.</p>
 * <p>On FIXED code: test PASSES because path traversal is detected and StorageException thrown.</p>
 *
 * <p><strong>Validates: Requirements 1.3, 1.4, 1.5, 1.6</strong></p>
 */
class PathTraversalBugConditionExplorationTest {

    private FileSystemStorageProvider createProvider(Path basePath) {
        return new FileSystemStorageProvider(basePath.toString(), new ObjectMapper());
    }

    /**
     * Property: store() with path traversal keys SHALL throw StorageException.
     *
     * <p>On UNFIXED code: store succeeds and writes outside basePath — test FAILS.</p>
     */
    @Property(tries = 20)
    @Label("store() SHALL throw StorageException for path-traversal storage keys")
    void storeShallRejectPathTraversalKeys(
            @ForAll("pathTraversalKeys") String storageKey) throws IOException {

        Path basePath = Files.createTempDirectory("storage-test-");
        try {
            FileSystemStorageProvider provider = createProvider(basePath);

            assertThatThrownBy(() -> provider.store(storageKey, "malicious-payload"))
                    .as("store('%s') should throw StorageException for path traversal, " +
                        "but on UNFIXED code it writes outside basePath", storageKey)
                    .isInstanceOf(StorageException.class);
        } finally {
            deleteRecursively(basePath);
        }
    }

    /**
     * Property: retrieve() with path traversal keys SHALL throw StorageException.
     *
     * <p>On UNFIXED code: retrieve attempts to read outside basePath — test FAILS.</p>
     */
    @Property(tries = 20)
    @Label("retrieve() SHALL throw StorageException for path-traversal storage keys")
    void retrieveShallRejectPathTraversalKeys(
            @ForAll("pathTraversalKeys") String storageKey) throws IOException {

        Path basePath = Files.createTempDirectory("storage-test-");
        try {
            FileSystemStorageProvider provider = createProvider(basePath);

            assertThatThrownBy(() -> provider.retrieve(storageKey))
                    .as("retrieve('%s') should throw StorageException for path traversal, " +
                        "but on UNFIXED code it reads outside basePath", storageKey)
                    .isInstanceOf(StorageException.class);
        } finally {
            deleteRecursively(basePath);
        }
    }

    /**
     * Property: delete() with path traversal keys SHALL throw StorageException.
     *
     * <p>On UNFIXED code: delete attempts to remove files outside basePath — test FAILS.</p>
     */
    @Property(tries = 20)
    @Label("delete() SHALL throw StorageException for path-traversal storage keys")
    void deleteShallRejectPathTraversalKeys(
            @ForAll("pathTraversalKeys") String storageKey) throws IOException {

        Path basePath = Files.createTempDirectory("storage-test-");
        try {
            FileSystemStorageProvider provider = createProvider(basePath);

            assertThatThrownBy(() -> provider.delete(storageKey))
                    .as("delete('%s') should throw StorageException for path traversal, " +
                        "but on UNFIXED code it deletes outside basePath", storageKey)
                    .isInstanceOf(StorageException.class);
        } finally {
            deleteRecursively(basePath);
        }
    }

    /**
     * Property: exists() with path traversal keys SHALL throw StorageException.
     *
     * <p>On UNFIXED code: exists probes file existence outside basePath — test FAILS.</p>
     */
    @Property(tries = 20)
    @Label("exists() SHALL throw StorageException for path-traversal storage keys")
    void existsShallRejectPathTraversalKeys(
            @ForAll("pathTraversalKeys") String storageKey) throws IOException {

        Path basePath = Files.createTempDirectory("storage-test-");
        try {
            FileSystemStorageProvider provider = createProvider(basePath);

            assertThatThrownBy(() -> provider.exists(storageKey))
                    .as("exists('%s') should throw StorageException for path traversal, " +
                        "but on UNFIXED code it probes outside basePath", storageKey)
                    .isInstanceOf(StorageException.class);
        } finally {
            deleteRecursively(basePath);
        }
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> pathTraversalKeys() {
        return Arbitraries.of(
                "../../../etc/passwd",
                "queues/../../../tmp/evil",
                "../secret/data.json",
                "../../../../../../etc/shadow",
                "valid-prefix/../../escape.txt",
                "../../../windows/system32/config",
                "queues/../../admin/secrets.json",
                "./../outside.txt"
        );
    }

    // ========== Cleanup Helper ==========

    private void deleteRecursively(Path path) {
        try {
            if (Files.exists(path)) {
                Files.walk(path)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
        } catch (IOException ignored) {}
    }
}
