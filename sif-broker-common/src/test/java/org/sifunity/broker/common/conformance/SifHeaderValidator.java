// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.conformance;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks SIF response headers and declared status codes for a given operation.
 *
 * <h2>Scope is the whole point</h2>
 * <p>Header conformance applies <em>only</em> to operations that declare response headers. In
 * {@code OpenAPI.json} the infrastructure object operations — {@code /environments/environment},
 * {@code /providers/provider}, {@code /subscriptions/subscription}, {@code /alerts/alert},
 * {@code /codeSets}, {@code /xquerys/xquery}, {@code /zones} — declare status codes only, with
 * no {@code headers} block at all. Emitting SIF message headers there would be invention, not
 * conformance, so {@link #findMissingHeaders} reports nothing for such operations.</p>
 *
 * <p>The operations that <em>do</em> declare response headers live in {@code infraPaths.yaml}:
 * {@code queueMessageGet}, {@code queueMessageDelete}, and the job-phase paths.</p>
 *
 * <h2>Where required-ness comes from</h2>
 * <p>{@code commonDefs.yaml} does not mark response headers with a {@code required} key —
 * OpenAPI response headers default to optional. Required-ness is carried in the description
 * prose instead, as a {@code "Required: ..."} or {@code "Required for DELAYED responses. ..."}
 * prefix. This class reads that prose, so the classification tracks the specification rather
 * than a hand-maintained list that could drift:</p>
 * <ul>
 *   <li><b>Always required:</b> {@code messageId}, {@code messageType}, {@code responseAction},
 *       {@code timestamp}</li>
 *   <li><b>Required for DELAYED responses:</b> {@code relativeServicePath}, {@code requestId}</li>
 *   <li><b>Optional:</b> everything else ({@code content-profile}, {@code fingerprint},
 *       {@code serviceType}, {@code vary}, ...)</li>
 * </ul>
 */
public final class SifHeaderValidator {

    /** Operation key for the queue-message GET (get next message, optionally acknowledging). */
    public static final String QUEUE_MESSAGE_GET = "queueMessageGet";

    /** Operation key for the queue-message DELETE. */
    public static final String QUEUE_MESSAGE_DELETE = "queueMessageDelete";

    private static final String REQUIRED_PREFIX = "required:";
    private static final String REQUIRED_DELAYED = "required for delayed";

    private SifHeaderValidator() {
    }

    /**
     * Response headers declared for an operation at a given status, in specification order.
     *
     * @param operationKey an {@code infraPaths.yaml} operation key, e.g. {@link #QUEUE_MESSAGE_GET}
     * @param status the HTTP status to look up
     * @return declared header names; empty when the operation or status declares none
     */
    public static Set<String> declaredResponseHeaders(String operationKey, int status) {
        JsonNode responses = responses(operationKey);
        if (responses == null) {
            return Collections.emptySet();
        }
        JsonNode response = responses.get(String.valueOf(status));
        if (response == null || response.get("headers") == null) {
            return Collections.emptySet();
        }
        Set<String> names = new LinkedHashSet<>();
        response.get("headers").fieldNames().forEachRemaining(names::add);
        return names;
    }

    /**
     * Status codes declared for an operation.
     *
     * @param operationKey an {@code infraPaths.yaml} operation key
     * @return declared statuses in ascending order; empty when the operation is unknown
     */
    public static Set<Integer> declaredStatuses(String operationKey) {
        JsonNode responses = responses(operationKey);
        if (responses == null) {
            return Collections.emptySet();
        }
        Set<Integer> statuses = new TreeSet<>();
        responses.fieldNames().forEachRemaining(name -> {
            try {
                statuses.add(Integer.parseInt(name));
            } catch (NumberFormatException ignored) {
                // 'default' and similar non-numeric response keys are not statuses.
            }
        });
        return statuses;
    }

    /**
     * True when the operation declares the status it actually returned.
     *
     * @param operationKey an {@code infraPaths.yaml} operation key
     * @param status the status actually returned
     * @return false when the status is undeclared, which is a conformance defect
     */
    public static boolean isDeclaredStatus(String operationKey, int status) {
        return declaredStatuses(operationKey).contains(status);
    }

    /**
     * Required response headers that the operation declares but the response did not emit.
     *
     * @param operationKey an {@code infraPaths.yaml} operation key
     * @param status the status actually returned
     * @param actualHeaderNames headers actually emitted; matched case-insensitively, as HTTP
     *     header names are not case sensitive
     * @param delayedResponse true when this is a DELAYED response, which makes
     *     {@code relativeServicePath} and {@code requestId} required as well
     * @return missing required header names; empty when conformant, and always empty for
     *     operations that declare no headers
     */
    public static List<String> findMissingHeaders(String operationKey,
                                                 int status,
                                                 Set<String> actualHeaderNames,
                                                 boolean delayedResponse) {
        Set<String> declared = declaredResponseHeaders(operationKey, status);
        if (declared.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Requirement> requirements = responseHeaderRequirements();
        Set<String> actualLower = new LinkedHashSet<>();
        for (String name : actualHeaderNames) {
            actualLower.add(name.toLowerCase(Locale.ROOT));
        }

        List<String> missing = new ArrayList<>();
        for (String header : declared) {
            Requirement requirement = requirements.getOrDefault(header, Requirement.OPTIONAL);
            boolean mustBePresent = requirement == Requirement.ALWAYS
                    || (requirement == Requirement.DELAYED_ONLY && delayedResponse);
            if (mustBePresent && !actualLower.contains(header.toLowerCase(Locale.ROOT))) {
                missing.add(header);
            }
        }
        return missing;
    }

    /**
     * Required-ness of every response header defined in {@code commonDefs.yaml}, derived from
     * the description prose.
     *
     * @return header name to requirement, in specification order
     */
    public static Map<String, Requirement> responseHeaderRequirements() {
        JsonNode responseHeaders = SifSchemaRegistry.commonDefsDocument()
                .path("components").path("schemas").path("httpHeaders").path("response");

        Map<String, Requirement> requirements = new LinkedHashMap<>();
        responseHeaders.fields().forEachRemaining(entry -> {
            String description = entry.getValue().path("description").asText("")
                    .trim().toLowerCase(Locale.ROOT);
            Requirement requirement;
            if (description.startsWith(REQUIRED_DELAYED)) {
                requirement = Requirement.DELAYED_ONLY;
            } else if (description.startsWith(REQUIRED_PREFIX)) {
                requirement = Requirement.ALWAYS;
            } else {
                requirement = Requirement.OPTIONAL;
            }
            requirements.put(entry.getKey(), requirement);
        });
        return requirements;
    }

    /**
     * Locates the {@code responses} block for an operation key, tolerating whichever HTTP
     * method the operation is defined under.
     */
    private static JsonNode responses(String operationKey) {
        JsonNode operation = SifSchemaRegistry.infraPathsDocument().get(operationKey);
        if (operation == null) {
            return null;
        }
        for (JsonNode method : operation) {
            JsonNode responses = method.get("responses");
            if (responses != null) {
                return responses;
            }
        }
        return null;
    }

    /** How strictly a declared response header must be emitted. */
    public enum Requirement {
        /** Must be present on every response. */
        ALWAYS,
        /** Must be present on DELAYED responses only. */
        DELAYED_ONLY,
        /** May be omitted. */
        OPTIONAL
    }
}
