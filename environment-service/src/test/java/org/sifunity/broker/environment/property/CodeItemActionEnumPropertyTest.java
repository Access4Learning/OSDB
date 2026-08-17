// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.sifunity.broker.common.constant.CodeItemAction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Property 12: CodeItemAction enum round-trip.
 *
 * <p><strong>Property:</strong> For all {@link CodeItemAction} values (ADD, CHANGE,
 * DEPRECATED, DELETE), serializing the value to its JSON string representation and
 * then deserializing it back SHALL produce the original enum value.</p>
 *
 * <p>This is a simple unit-style test — no Spring context needed.</p>
 *
 * <p><strong>Validates: Requirements 8.11</strong></p>
 */
class CodeItemActionEnumPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 12: CodeItemAction enum round-trip via JSON serialization.
     *
     * <p><strong>Validates: Requirements 8.11</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: codeset-registry, Property 12: CodeItemAction enum round-trip")
    void codeItemActionEnumRoundTrip() throws Exception {
        for (CodeItemAction action : CodeItemAction.values()) {
            // Serialize to JSON string
            String json = objectMapper.writeValueAsString(action);

            // Deserialize back
            CodeItemAction deserialized = objectMapper.readValue(json, CodeItemAction.class);

            assertThat(deserialized)
                    .as("CodeItemAction.%s should survive JSON round-trip", action.name())
                    .isEqualTo(action);
        }
    }
}
