// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.queueservice.storage.FileSystemStorageProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation Property Test for Valid Storage Key Operations.
 *
 * <p><strong>IMPORTANT: These tests encode current CORRECT behavior that must survive the fix.</strong></p>
 *
 * <p>Property: For any storageKey that is a simple relative path (no {@code ..} components)
 * where resolved path starts with basePath, store/retrieve/delete/exists work normally.</p>
 *
 * <p>These tests MUST PASS on both unfixed and fixed code, confirming that the path traversal
 * fix does not break legitimate storage operations.</p>
 *
 * <p><strong>Validates: Requirements 3.3</strong></p>
 */
class ValidStorageKeyPreservationTest {

    private FileSystemStorageProvider createProvider(Path basePath) {
        return new FileSystemStorageProvider(basePath.toString(), new ObjectMapper());
    }

    /**
     * Property: For any valid storageKey (simple relative path with no ".." components),
     * store succeeds, retrieve returns stored data, delete removes it, exists returns correct boolean.
     */
    @Property(tries = 20)
    @Label("Valid storage keys SHALL store/retrieve/delete/exists normally")
    void validStorageKeysShallWorkNormally(
            @ForAll("validStorageKeys") String storageKey) throws IOException {

        Path basePath = Files.createTempDirectory("storage-preservation-");
        try {
            FileSystemStorageProvider provider = createProvider(basePath);
            Map<String, String> payload = Map.of("message", "test-data", "id", "msg-001");

            // store succeeds
            String result = provider.store(storageKey, payload);
            assertThat(result)
                    .as("store('%s') should return the storage key", storageKey)
                    .isEqualTo(storageKey);

            // exists returns true after store
            boolean existsAfterStore = provider.exists(storageKey);
            assertThat(existsAfterStore)
                    .as("exists('%s') should return true after store", storageKey)
                    .isTrue();

            // retrieve returns stored data
            Optional<Object> retrieved = provider.retrieve(storageKey);
            assertThat(retrieved)
                    .as("retrieve('%s') should return non-empty Optional", storageKey)
                    .isPresent();

            // delete removes it
            provider.delete(storageKey);
            boolean existsAfterDelete = provider.exists(storageKey);
            assertThat(existsAfterDelete)
                    .as("exists('%s') should return false after delete", storageKey)
                    .isFalse();

        } finally {
            deleteRecursively(basePath);
        }
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> validStorageKeys() {
        // Generate random path segments matching [a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)* + .json suffix
        Arbitrary<String> segment = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_', '-')
                .ofMinLength(1)
                .ofMaxLength(12);

        return Combinators.combine(
                segment,
                Arbitraries.integers().between(0, 2)
        ).flatAs((firstSegment, extraSegments) -> {
            if (extraSegments == 0) {
                return Arbitraries.just(firstSegment + ".json");
            }
            // Build multi-segment path
            Arbitrary<String> extraParts = segment.list().ofSize(extraSegments)
                    .map(parts -> String.join("/", parts));
            return extraParts.map(extra -> firstSegment + "/" + extra + ".json");
        });
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
