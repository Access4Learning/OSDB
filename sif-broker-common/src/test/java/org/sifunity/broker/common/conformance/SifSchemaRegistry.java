// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches the authoritative SIF 3.7 JSON schemas from {@code external_docs}.
 *
 * <p>The schema files are read directly from the repository rather than copied into test
 * resources. A copy would be free to drift from the specification; reading the originals
 * means a schema update is picked up immediately and cannot be silently stale.</p>
 *
 * <p>Two documents are exposed:</p>
 * <ul>
 *   <li>{@link #createSchemaDocument()} — {@code jsonSchema_OpenAPI3.1_Create_IN.yaml},
 *       governing create (POST) request bodies and the object shapes returned for them.</li>
 *   <li>{@link #updateSchemaDocument()} — {@code jsonSchema_OpenAPI3.1_Update_IN.yaml},
 *       governing update (PUT) request bodies.</li>
 * </ul>
 *
 * <p>Both documents map envelope keys to object shapes through a root-level {@code properties}
 * block (for example {@code provider: {$ref: '#/definitions/provider'}}), with the shapes
 * themselves under {@code definitions}.</p>
 *
 * <p>Note that the document <em>root</em> is {@code type: array} — it models a list of SIF
 * messages, and its {@code items.oneOf} enumerates which envelope keys are acceptable. That
 * makes the root unusable as a schema for a single response body: validating an object against
 * it fails with "object found, array expected". {@link #envelopeSchema(String)} therefore
 * synthesizes an object schema from the root {@code properties} plus {@code definitions}, which
 * is what {@link SifSchemaValidator#validateEnvelope} uses.</p>
 *
 * <p>The schemas are draft-07: they use {@code definitions} with local {@code $ref} pointers.
 * A handful of properties carry {@code type:} as a sibling of {@code $ref:}; draft-07 ignores
 * keywords beside {@code $ref}, and in every case in these files the sibling agrees with the
 * referenced definition anyway, so the distinction is immaterial here.</p>
 */
public final class SifSchemaRegistry {

    /** Path of the authoritative schema directory, relative to the repository root. */
    private static final String SCHEMA_DIR = "external_docs/3-7-open-api-JSON/3_1";

    private static final String CREATE_SCHEMA = "jsonSchema_OpenAPI3.1_Create_IN.yaml";
    private static final String UPDATE_SCHEMA = "jsonSchema_OpenAPI3.1_Update_IN.yaml";
    private static final String EXAMPLES = "examples_IN_3.7.yaml";
    private static final String COMMON_DEFS = "commonDefs.yaml";
    private static final String INFRA_PATHS = "infraPaths.yaml";
    private static final String OPEN_API = "OpenAPI.json";

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Parsed schema documents, keyed by file name. */
    private static final Map<String, JsonNode> DOCUMENTS = new ConcurrentHashMap<>();

    /** Compiled schemas, keyed by "file#definition". Compilation is the expensive part. */
    private static final Map<String, JsonSchema> COMPILED = new ConcurrentHashMap<>();

    private static volatile Path repositoryRoot;

    private SifSchemaRegistry() {
    }

    /** The {@code Create_IN} schema document, containing create-body definitions. */
    public static JsonNode createSchemaDocument() {
        return document(CREATE_SCHEMA);
    }

    /** The {@code Update_IN} schema document, containing update-body definitions. */
    public static JsonNode updateSchemaDocument() {
        return document(UPDATE_SCHEMA);
    }

    /** The canonical wire examples, used to self-test the harness. */
    public static JsonNode examplesDocument() {
        return document(EXAMPLES);
    }

    /** Shared header and error-response definitions. */
    public static JsonNode commonDefsDocument() {
        return document(COMMON_DEFS);
    }

    /**
     * Infrastructure path definitions, including declared statuses and response headers.
     *
     * <p>Returns the contents of the file's single {@code infraPaths} root key, so callers see
     * operation keys such as {@code queueMessageGet} directly.</p>
     */
    public static JsonNode infraPathsDocument() {
        JsonNode root = document(INFRA_PATHS);
        JsonNode operations = root.get("infraPaths");
        if (operations == null) {
            throw new IllegalStateException(
                    INFRA_PATHS + " is missing its 'infraPaths' root key; the operation map "
                            + "cannot be read from it.");
        }
        return operations;
    }

    /** The OpenAPI entry point, which declares which operations exist. */
    public static JsonNode openApiDocument() {
        return document(OPEN_API);
    }

    /**
     * Compiles a schema for a single definition within a document.
     *
     * <p>The returned schema is a thin wrapper node — {@code {"$ref": "#/definitions/<name>"}}
     * plus the document's {@code definitions} block — so that sibling definitions referenced
     * transitively still resolve.</p>
     *
     * @param documentName file name within the schema directory
     * @param definitionName definition to validate against, e.g. {@code provider}
     * @return the compiled schema
     * @throws IllegalArgumentException if the definition does not exist in the document
     */
    public static JsonSchema definitionSchema(String documentName, String definitionName) {
        return COMPILED.computeIfAbsent(documentName + "#" + definitionName, key -> {
            JsonNode doc = document(documentName);
            JsonNode definitions = doc.get("definitions");
            if (definitions == null || !definitions.has(definitionName)) {
                throw new IllegalArgumentException(
                        "No definition '" + definitionName + "' in " + documentName
                                + ". Check the definition name against the authoritative schema.");
            }
            ObjectNode wrapper = JSON.createObjectNode();
            wrapper.put("$ref", "#/definitions/" + definitionName);
            wrapper.set("definitions", definitions);
            return factory().getSchema(wrapper, validatorConfig());
        });
    }

    /**
     * Builds an object schema that maps SIF envelope keys to their payload definitions.
     *
     * <p>The document root cannot be used directly: it is {@code type: array}, modelling a list
     * of SIF messages rather than one body. This method reuses the root's {@code properties}
     * block — the authoritative envelope-key-to-definition map — under an object root, so a
     * single wire body such as {@code {"provider": {...}}} validates correctly.</p>
     *
     * @param documentName file name within the schema directory
     * @return compiled object schema covering every envelope key the document declares
     */
    public static JsonSchema envelopeSchema(String documentName) {
        return COMPILED.computeIfAbsent(documentName + "#envelope", key -> {
            JsonNode doc = document(documentName);
            JsonNode properties = doc.get("properties");
            JsonNode definitions = doc.get("definitions");
            if (properties == null || definitions == null) {
                throw new IllegalStateException(
                        documentName + " is missing its root 'properties' or 'definitions' block; "
                                + "the envelope-key map cannot be derived from it.");
            }
            ObjectNode wrapper = JSON.createObjectNode();
            wrapper.put("type", "object");
            wrapper.set("properties", properties);
            wrapper.set("definitions", definitions);
            return factory().getSchema(wrapper, validatorConfig());
        });
    }

    /** True when the document declares {@code definitionName} under {@code definitions}. */
    public static boolean hasDefinition(String documentName, String definitionName) {
        JsonNode definitions = document(documentName).get("definitions");
        return definitions != null && definitions.has(definitionName);
    }

    /** Absolute path to a file in the authoritative schema directory. */
    public static Path schemaFile(String fileName) {
        return repositoryRoot().resolve(SCHEMA_DIR).resolve(fileName);
    }

    private static JsonSchemaFactory factory() {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    }

    /**
     * Validator configuration for the SIF schemas.
     *
     * <p>Uses JSON-pointer instance locations so violations read as
     * {@code /provider/providerName} rather than {@code $.provider.providerName}.</p>
     *
     * <p>{@code formatAssertionsEnabled(false)} is requested but is <em>not</em> honoured for
     * draft-07 by this library version — that flag governs the 2019-09/2020-12 format
     * vocabularies, whereas the draft-07 metaschema asserts {@code format} unconditionally. The
     * setting is left in place to document the intent, but nothing here relies on it: format
     * violations <em>are</em> reported. That turns out to be desirable, since it means a
     * malformed {@code endPoint.location} would be caught. The one consequence is that the
     * specification's own {@code pod} example fails its own schema (it carries empty strings and
     * scheme-less hostnames for {@code format: uri} fields), which is why the harness self-test
     * excludes that single example.</p>
     */
    private static SchemaValidatorsConfig validatorConfig() {
        return SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(false)
                .build();
    }

    private static JsonNode document(String fileName) {
        return DOCUMENTS.computeIfAbsent(fileName, name -> {
            Path path = schemaFile(name);
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException(
                        "Authoritative SIF schema not found: " + path
                                + ". The conformance harness reads external_docs directly; "
                                + "it cannot run without it.");
            }
            try {
                ObjectMapper mapper = name.endsWith(".json") ? JSON : YAML;
                return mapper.readTree(path.toFile());
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to parse SIF schema " + path, e);
            }
        });
    }

    /**
     * Locates the repository root by walking up from the working directory until a directory
     * containing {@code external_docs} is found.
     *
     * <p>Necessary because Maven runs each module's tests with that module as the working
     * directory, so the path to {@code external_docs} differs between a reactor build and a
     * single-module run.</p>
     */
    private static Path repositoryRoot() {
        Path cached = repositoryRoot;
        if (cached != null) {
            return cached;
        }
        Path candidate = Paths.get("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve(SCHEMA_DIR))) {
                repositoryRoot = candidate;
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "Could not locate '" + SCHEMA_DIR + "' walking up from "
                        + Paths.get("").toAbsolutePath()
                        + ". The SIF conformance harness requires the external_docs directory.");
    }
}
