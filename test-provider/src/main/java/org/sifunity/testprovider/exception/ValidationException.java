// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown when a runtime configure request fails validation (a required field is missing/blank, or an
 * optional field such as {@code gatewayUrl} is syntactically invalid).
 *
 * <p>Carries a map of field name to a human-readable problem description so the control endpoint can
 * return an HTTP 400 that identifies exactly which field was wrong. This exception is thrown
 * <strong>before</strong> any mutation of {@code ProviderConfig}, so the config is always left unchanged
 * when validation fails.</p>
 */
public class ValidationException extends RuntimeException {

    private final transient Map<String, String> errors;

    /**
     * Create a validation exception for a set of field errors.
     *
     * @param errors map of field name to problem description (preserves insertion order)
     */
    public ValidationException(Map<String, String> errors) {
        super(buildMessage(errors));
        this.errors = errors == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    /**
     * The per-field validation errors.
     *
     * @return an unmodifiable map of field name to problem description
     */
    public Map<String, String> getErrors() {
        return errors;
    }

    private static String buildMessage(Map<String, String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "Validation failed";
        }
        StringBuilder sb = new StringBuilder("Validation failed: ");
        boolean first = true;
        for (Map.Entry<String, String> e : errors.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append(' ').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }
}
