// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.conformance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates JSON payloads against the authoritative SIF 3.7 schemas.
 *
 * <p>This is the mechanical replacement for reading payloads by hand. A full certification run
 * produces several hundred exchanges; the defects this harness exists to catch survived a
 * manual audit precisely because that does not scale.</p>
 *
 * <h2>Envelope vs. definition</h2>
 * <p>{@link #validateEnvelope(String, JsonNode)} validates a complete wire body — including its
 * SIF envelope key — against the schema document root. {@link #validate(String, JsonNode)}
 * validates a bare object against a single definition. Prefer the envelope form for anything
 * read off the wire, because a missing or wrong envelope key is itself a conformance defect.</p>
 *
 * <h2>Explicit nulls</h2>
 * <p>The SIF schemas type their optional properties ({@code type: string}, {@code type: object})
 * without {@code nullable}, so an explicit JSON {@code null} for an absent optional value is
 * nonconformant — the property must be omitted. Draft-07 validators do not all report this
 * consistently, so {@link #findExplicitNulls(JsonNode)} checks it directly and is reported
 * alongside schema violations by {@link #findViolations}.</p>
 */
public final class SifSchemaValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The create-body schema document. */
    public static final String CREATE = "jsonSchema_OpenAPI3.1_Create_IN.yaml";

    /** The update-body schema document. */
    public static final String UPDATE = "jsonSchema_OpenAPI3.1_Update_IN.yaml";

    private SifSchemaValidator() {
    }

    /**
     * Validates a bare object against a single definition in the create schema.
     *
     * @param definitionName e.g. {@code provider}, {@code environment}, {@code subscription}
     * @param payload the object to validate, without its SIF envelope
     * @return violations in specification order; empty when conformant
     */
    public static List<String> validate(String definitionName, JsonNode payload) {
        return validate(CREATE, definitionName, payload);
    }

    /**
     * Validates a bare object against a single definition in the named schema document.
     *
     * @param documentName {@link #CREATE} or {@link #UPDATE}
     * @param definitionName the definition to validate against
     * @param payload the object to validate, without its SIF envelope
     * @return violations in specification order; empty when conformant
     */
    public static List<String> validate(String documentName, String definitionName, JsonNode payload) {
        Set<ValidationMessage> messages =
                SifSchemaRegistry.definitionSchema(documentName, definitionName).validate(payload);
        return format(messages);
    }

    /**
     * Validates a complete wire body, envelope key included, against the create schema root.
     *
     * <p>Validation runs against the synthesized envelope schema (see
     * {@link SifSchemaRegistry#envelopeSchema}), which maps each SIF envelope key to its payload
     * definition. Because JSON Schema {@code properties} only constrains keys that are present,
     * an unwrapped body would otherwise validate vacuously — so the expected key's presence is
     * asserted explicitly as well.</p>
     *
     * @param expectedEnvelopeKey the SIF envelope key the body must carry, e.g. {@code provider}
     * @param body the complete response or request body
     * @return violations in specification order; empty when conformant
     */
    public static List<String> validateEnvelope(String expectedEnvelopeKey, JsonNode body) {
        List<String> violations = new ArrayList<>();
        if (body == null || body.isNull()) {
            violations.add("#: body is absent, expected an object wrapped under '"
                    + expectedEnvelopeKey + "'");
            return violations;
        }
        if (!body.has(expectedEnvelopeKey)) {
            violations.add("#: required envelope key [" + expectedEnvelopeKey + "] not found"
                    + " (found: " + fieldNames(body) + ")");
        }
        violations.addAll(format(SifSchemaRegistry.envelopeSchema(CREATE).validate(body)));
        return violations;
    }

    /**
     * Full conformance check for a wire body: schema, envelope, and explicit nulls.
     *
     * @param expectedEnvelopeKey the SIF envelope key the body must carry
     * @param body the complete response or request body
     * @return every violation found; empty when the body is conformant
     */
    public static List<String> findViolations(String expectedEnvelopeKey, JsonNode body) {
        List<String> violations = new ArrayList<>(validateEnvelope(expectedEnvelopeKey, body));
        for (String nullPath : findExplicitNulls(body)) {
            violations.add(nullPath + ": explicit null for an optional property; "
                    + "the SIF schemas do not declare it nullable, so it must be omitted");
        }
        return violations;
    }

    /**
     * Finds every JSON pointer in the payload whose value is an explicit {@code null}.
     *
     * @param body the payload to scan, may be null
     * @return JSON-pointer-style paths of all explicit nulls, in document order
     */
    public static List<String> findExplicitNulls(JsonNode body) {
        List<String> found = new ArrayList<>();
        if (body == null) {
            return found;
        }
        Deque<Object[]> stack = new ArrayDeque<>();
        stack.push(new Object[]{body, "#"});
        while (!stack.isEmpty()) {
            Object[] frame = stack.pop();
            JsonNode node = (JsonNode) frame[0];
            String path = (String) frame[1];
            if (node.isNull()) {
                found.add(path);
            } else if (node.isObject()) {
                node.fieldNames().forEachRemaining(name ->
                        stack.push(new Object[]{node.get(name), path + "/" + name}));
            } else if (node.isArray()) {
                for (int i = node.size() - 1; i >= 0; i--) {
                    stack.push(new Object[]{node.get(i), path + "[" + i + "]"});
                }
            }
        }
        found.sort(Comparator.naturalOrder());
        return found;
    }

    /** Parses a JSON string into a node, for use with captured wire bodies. */
    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload is not valid JSON: " + e.getOriginalMessage(), e);
        }
    }

    /** Serializes an object to a node using a plain mapper, mirroring wire serialization. */
    public static JsonNode toNode(Object value) {
        return MAPPER.valueToTree(value);
    }

    /**
     * Formats violations in the style the external SIF audit produced, for example
     * {@code #/provider: required key [providerName] not found}, so that findings from this
     * harness are directly comparable with findings reported against the specification.
     */
    private static List<String> format(Set<ValidationMessage> messages) {
        return messages.stream()
                .map(m -> m.getInstanceLocation() + ": " + m.getMessage())
                .sorted()
                .collect(Collectors.toList());
    }

    private static String fieldNames(JsonNode body) {
        List<String> names = new ArrayList<>();
        body.fieldNames().forEachRemaining(names::add);
        return names.isEmpty() ? "(none)" : String.join(", ", names);
    }
}
