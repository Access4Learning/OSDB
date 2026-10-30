// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.state;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists and loads {@link SetupState} to/from
 * {@code target/setup-state.json} using Jackson. This bridges the gap between
 * the setup, publish, and consume simulation phases.
 */
public final class StateManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final Path STATE_FILE = Path.of("target", "setup-state.json");

    private StateManager() { }

    /**
     * Serialize {@code state} to {@code target/setup-state.json}, creating
     * parent directories if they do not exist.
     */
    public static void save(SetupState state) {
        try {
            Files.createDirectories(STATE_FILE.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(STATE_FILE.toFile(), state);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save setup state to " + STATE_FILE, e);
        }
    }

    /**
     * Deserialize {@link SetupState} from {@code target/setup-state.json}.
     *
     * @throws RuntimeException if the file does not exist or cannot be read
     */
    public static SetupState load() {
        if (!Files.exists(STATE_FILE)) {
            throw new RuntimeException(
                "Setup state file not found at " + STATE_FILE.toAbsolutePath()
                    + ". Run SetupSimulation first to create it.");
        }
        try {
            return MAPPER.readValue(STATE_FILE.toFile(), SetupState.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load setup state from " + STATE_FILE, e);
        }
    }
}
