// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SifCollectionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesToDoubleNestedStructure() throws Exception {
        SifCollection collection = SifCollection.of("environment",
                List.of(Map.of("id", "1"), Map.of("id", "2")));

        String json = objectMapper.writeValueAsString(collection);
        var tree = objectMapper.readTree(json);

        assertThat(tree.has("environments")).isTrue();
        assertThat(tree.get("environments").has("environment")).isTrue();
        assertThat(tree.get("environments").get("environment").isArray()).isTrue();
        assertThat(tree.get("environments").get("environment").size()).isEqualTo(2);
    }

    @Test
    void emptyCollectionProducesEmptyArray() throws Exception {
        SifCollection collection = SifCollection.of("queue", List.of());

        String json = objectMapper.writeValueAsString(collection);
        var tree = objectMapper.readTree(json);

        assertThat(tree.get("queues").get("queue").isArray()).isTrue();
        assertThat(tree.get("queues").get("queue").size()).isEqualTo(0);
    }

    @Test
    void naivePluralAppendsSEvenIfAlreadyEndsInS() throws Exception {
        SifCollection collection = SifCollection.of("address", List.of(Map.of("city", "NYC")));

        String json = objectMapper.writeValueAsString(collection);
        var tree = objectMapper.readTree(json);

        // "address" + "s" = "addresss" — naive, as specified
        assertThat(tree.has("addresss")).isTrue();
        assertThat(tree.get("addresss").has("address")).isTrue();
    }

    @Test
    void worksWithStringItems() throws Exception {
        SifCollection collection = SifCollection.of("zone", List.of("zone-1", "zone-2"));

        String json = objectMapper.writeValueAsString(collection);
        var tree = objectMapper.readTree(json);

        assertThat(tree.get("zones").get("zone").get(0).asText()).isEqualTo("zone-1");
        assertThat(tree.get("zones").get("zone").get(1).asText()).isEqualTo("zone-2");
    }
}
