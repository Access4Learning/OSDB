// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SIF 3.7 collection wrapper for list responses.
 *
 * <p>Wraps a collection of objects in the SIF-standard double-nested structure:
 * <pre>{@code
 * {
 *   "<name>s": {
 *     "<name>": [ ... ]
 *   }
 * }
 * }</pre>
 *
 * <p>The plural form is naive — it always appends "s" to the singular name,
 * even if the name already ends in "s". This matches the SIF convention.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * List<SifEnvironmentResponse> envs = service.listEnvironments();
 * return ResponseEntity.ok(SifCollection.of("environment", envs));
 * }</pre>
 *
 * <p>Produces:
 * <pre>{@code
 * {
 *   "environments": {
 *     "environment": [ { ... }, { ... } ]
 *   }
 * }
 * }</pre>
 */
public class SifCollection {

    private final Map<String, Object> root;

    private SifCollection(Map<String, Object> root) {
        this.root = root;
    }

    /**
     * Creates a SIF collection wrapper.
     *
     * @param singularName the singular object name (e.g., "environment", "queue")
     * @param items        the collection of items to wrap
     * @param <T>          the item type
     * @return a wrapper that serializes to the SIF double-nested format
     */
    public static <T> SifCollection of(String singularName, Collection<T> items) {
        String pluralName = singularName + "s";

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put(singularName, items);

        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put(pluralName, inner);

        return new SifCollection(outer);
    }

    /**
     * Jackson uses this to serialize the root map as top-level JSON properties.
     */
    @JsonAnyGetter
    public Map<String, Object> getRoot() {
        return root;
    }
}
