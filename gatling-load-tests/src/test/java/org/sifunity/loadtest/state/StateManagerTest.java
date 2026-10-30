// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.state;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test verifying JSON round-trip serialization of {@link SetupState}
 * via {@link StateManager}: write then read produces an equal object.
 */
class StateManagerTest {

    @AfterEach
    void cleanup() throws IOException {
        Files.deleteIfExists(StateManager.STATE_FILE);
    }

    @Test
    void roundTripSerializationProducesEqualObject() {
        ZoneState zone1 = new ZoneState(
            "zone-1", "ctx-uuid-1",
            "prov-env-1", "prov-token-1", "prov-secret-1",
            "cons-env-1", "cons-token-1", "cons-secret-1",
            "queue-1", "acl-1", "sub-1"
        );
        ZoneState zone2 = new ZoneState(
            "zone-2", "ctx-uuid-2",
            "prov-env-2", "prov-token-2", "prov-secret-2",
            "cons-env-2", "cons-token-2", "cons-secret-2",
            "queue-2", "acl-2", "sub-2"
        );

        SetupState original = new SetupState("domain-1", "solution-1", List.of(zone1, zone2));

        StateManager.save(original);
        SetupState loaded = StateManager.load();

        assertEquals(original, loaded, "Loaded state must equal original state");
    }

    @Test
    void loadThrowsWhenFileDoesNotExist() throws IOException {
        // Ensure file is absent
        Files.deleteIfExists(StateManager.STATE_FILE);

        RuntimeException ex = assertThrows(RuntimeException.class, StateManager::load);
        assertTrue(ex.getMessage().contains("not found"),
                "Exception message must mention file not found");
    }

    @Test
    void saveCreatesParentDirectories() throws IOException {
        // Remove target dir contents to verify mkdir behavior
        Path targetDir = StateManager.STATE_FILE.getParent();
        Files.deleteIfExists(StateManager.STATE_FILE);

        SetupState state = new SetupState("d", "s", List.of());
        StateManager.save(state);

        assertTrue(Files.exists(StateManager.STATE_FILE),
                "State file must exist after save");
    }
}
