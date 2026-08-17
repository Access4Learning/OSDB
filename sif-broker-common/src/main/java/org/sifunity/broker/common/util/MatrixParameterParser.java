// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stateless utility class for parsing and reconstructing SIF 3.3 matrix parameters.
 *
 * <p>SIF 3.3 conveys zone, context, and message identifiers as URL matrix parameters
 * in the last path segment (e.g., {@code /StudentPersonals;zoneId=zone1;contextId=DEFAULT}).
 * This parser extracts recognized key-value pairs, URL-decodes values, and returns
 * a clean path stripped of matrix parameters.</p>
 *
 * <p>Only recognized keys are extracted: {@code zoneId}, {@code contextId}, and
 * {@code messageId}. Unrecognized keys are left in the path (skipped). Malformed
 * pairs (no {@code =} sign) are skipped with a warning log.</p>
 *
 * @see <a href="https://specification.sifassociation.org/">SIF 3.3 Base Architecture §4.3.3</a>
 */
public final class MatrixParameterParser {

    private static final Logger log = LoggerFactory.getLogger(MatrixParameterParser.class);

    /**
     * The set of matrix parameter keys recognized by the parser.
     * Only these keys are extracted from the URL path; all others are ignored.
     */
    private static final Set<String> RECOGNIZED_KEYS = Set.of("zoneId", "contextId", "messageId");

    /**
     * Result of parsing matrix parameters from a URL path.
     *
     * @param cleanPath the URL path with recognized matrix parameters removed from the last segment
     * @param params    an unmodifiable map of recognized key-value pairs extracted from the last segment
     */
    public record ParseResult(String cleanPath, Map<String, String> params) {
        public ParseResult {
            params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }
    }

    /**
     * Parses matrix parameters from the last segment of a URL path.
     *
     * <p>The method splits on {@code ?} first to isolate any query string, then
     * extracts {@code ;key=value} pairs from the last path segment. Only recognized
     * keys are extracted; unrecognized keys remain in the path. Values are URL-decoded.</p>
     *
     * <p>Edge cases:</p>
     * <ul>
     *   <li>Empty or null path returns empty path and empty map</li>
     *   <li>Path with query string: query string is preserved on the clean path</li>
     *   <li>Malformed pairs (no {@code =}) are skipped with a WARN log</li>
     *   <li>Unrecognized keys are left in the last segment (not extracted)</li>
     * </ul>
     *
     * @param rawPath the raw URL path, possibly containing matrix parameters and query string
     * @return a {@link ParseResult} with the clean path and extracted parameters
     */
    public static ParseResult parse(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return new ParseResult("", Map.of());
        }

        // Split off query string first
        String pathPart;
        String queryPart;
        int queryIdx = rawPath.indexOf('?');
        if (queryIdx >= 0) {
            pathPart = rawPath.substring(0, queryIdx);
            queryPart = rawPath.substring(queryIdx); // includes '?'
        } else {
            pathPart = rawPath;
            queryPart = "";
        }

        // Find the last path segment
        int lastSlash = pathPart.lastIndexOf('/');
        String prefix;
        String lastSegment;
        if (lastSlash >= 0) {
            prefix = pathPart.substring(0, lastSlash + 1); // includes trailing '/'
            lastSegment = pathPart.substring(lastSlash + 1);
        } else {
            prefix = "";
            lastSegment = pathPart;
        }

        // Split last segment on ';'
        String[] parts = lastSegment.split(";", -1);
        String segmentBase = parts[0]; // the service name portion

        Map<String, String> extractedParams = new LinkedHashMap<>();
        StringBuilder unrecognizedParts = new StringBuilder();

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }

            int eqIdx = part.indexOf('=');
            if (eqIdx < 0) {
                log.warn("Malformed matrix parameter (no '=' sign): '{}'", part);
                continue;
            }

            String key = part.substring(0, eqIdx);
            String rawValue = part.substring(eqIdx + 1);

            if (RECOGNIZED_KEYS.contains(key)) {
                String decodedValue = urlDecode(rawValue);
                extractedParams.put(key, decodedValue);
            } else {
                // Unrecognized key — keep it in the path
                unrecognizedParts.append(';').append(part);
            }
        }

        // Reconstruct clean path
        String cleanLastSegment = segmentBase + unrecognizedParts;
        String cleanPath = prefix + cleanLastSegment + queryPart;

        return new ParseResult(cleanPath, extractedParams);
    }

    /**
     * Reconstructs a path by appending matrix parameters to a service name.
     *
     * <p>Parameters are appended in iteration order as {@code ;key=value} pairs.
     * Values are URL-encoded. This is used for outbound {@code relativeServicePath}
     * formatting when delivering to legacy SIF 3.3 environments.</p>
     *
     * @param serviceName the base service name (e.g., "StudentPersonals")
     * @param params      the matrix parameters to append (may be empty or null)
     * @return the service name with appended matrix parameters
     */
    public static String reconstruct(String serviceName, Map<String, String> params) {
        if (serviceName == null) {
            serviceName = "";
        }
        if (params == null || params.isEmpty()) {
            return serviceName;
        }

        StringBuilder sb = new StringBuilder(serviceName);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sb.append(';')
              .append(entry.getKey())
              .append('=')
              .append(urlEncode(entry.getValue()));
        }
        return sb.toString();
    }

    /**
     * URL-decodes a value using UTF-8. If decoding fails, returns the raw value
     * and logs a warning.
     */
    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to URL-decode matrix parameter value '{}': {}", value, e.getMessage());
            return value;
        }
    }

    /**
     * URL-encodes a value using UTF-8.
     */
    private static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private MatrixParameterParser() {
        throw new UnsupportedOperationException("Utility class");
    }
}
