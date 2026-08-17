// SPDX-License-Identifier: MIT
package org.sifunity.gateway.util;

/**
 * Utility class for parsing URL paths in the SIF Unity Broker gateway.
 *
 * <p>Handles three URL tiers:</p>
 * <ul>
 *   <li>Domain management: {@code /domains/**} → domainId=null, solutionId=null</li>
 *   <li>Solution management: {@code /{domainId}/solutions/**} → solutionId=null</li>
 *   <li>Resource paths: {@code /{domainId}/{solutionId}/**} → both populated</li>
 * </ul>
 *
 * <p>This is a pure utility class with no Spring dependencies.</p>
 */
public final class UrlPrefixParser {

    private static final String DOMAINS_PREFIX = "/domains";
    private static final String SOLUTIONS_SEGMENT = "solutions";

    private UrlPrefixParser() {
        // Utility class — no instantiation
    }

    /**
     * Parsed representation of a URL path, decomposed into domain/solution prefix
     * and the remaining resource path.
     *
     * @param domainId    the domain ID from the path, or null for domain-management paths
     * @param solutionId  the solution ID from the path, or null for domain/solution-management paths
     * @param resourcePath the resource path after the prefix (always starts with '/')
     */
    public record ParsedPath(
            String domainId,
            String solutionId,
            String resourcePath
    ) {

        /**
         * Returns true if this is a domain management path ({@code /domains/**}).
         */
        public boolean isDomainManagementPath() {
            return domainId == null && solutionId == null;
        }

        /**
         * Returns true if this is a solution management path ({@code /{domainId}/solutions/**}).
         */
        public boolean isSolutionManagementPath() {
            return domainId != null && solutionId == null;
        }

        /**
         * Returns true if this is a full resource path ({@code /{domainId}/{solutionId}/**}).
         */
        public boolean isResourcePath() {
            return domainId != null && solutionId != null;
        }
    }

    /**
     * Parses a URL path into its domain/solution prefix components and the remaining resource path.
     *
     * <p>Parsing rules:</p>
     * <ol>
     *   <li>{@code /domains/**} → domainId=null, solutionId=null, resourcePath=full path</li>
     *   <li>{@code /{domainId}/solutions/**} → domainId=segment[1], solutionId=null,
     *       resourcePath=/solutions/...</li>
     *   <li>{@code /{domainId}/{solutionId}/**} → domainId=segment[1], solutionId=segment[2],
     *       resourcePath=remaining (or "/" if nothing follows)</li>
     * </ol>
     *
     * <p>Paths that don't start with '/' or are blank are treated as domain-management
     * (returned as-is in resourcePath with both IDs null).</p>
     *
     * @param path the raw URL path (e.g. {@code /iowa/production/environments})
     * @return a {@link ParsedPath} with the extracted components
     */
    public static ParsedPath parse(String path) {
        if (path == null || path.isBlank()) {
            return new ParsedPath(null, null, "/");
        }

        // Normalise: ensure leading slash, strip trailing slash (unless path is just "/")
        String normalised = path.startsWith("/") ? path : "/" + path;

        // Tier 1 — domain management: /domains or /domains/...
        if (normalised.equals(DOMAINS_PREFIX) || normalised.startsWith(DOMAINS_PREFIX + "/")) {
            return new ParsedPath(null, null, normalised);
        }

        // Actuator / other well-known root paths — treat as domain-management tier
        if (normalised.startsWith("/actuator")) {
            return new ParsedPath(null, null, normalised);
        }

        // Split into segments: "/iowa/production/environments" → ["", "iowa", "production", "environments"]
        String[] segments = normalised.split("/", -1);

        // Need at least 2 real segments (index 1 = domainId, index 2 = something)
        if (segments.length < 3 || segments[1].isEmpty()) {
            // Single-segment path like "/something" — treat as domain-management tier
            return new ParsedPath(null, null, normalised);
        }

        String domainId = segments[1];

        // Tier 2 — solution management: /{domainId}/solutions or /{domainId}/solutions/...
        if (SOLUTIONS_SEGMENT.equals(segments[2])) {
            String resourcePath = normalised.substring(1 + domainId.length()); // strip /{domainId}
            return new ParsedPath(domainId, null, resourcePath);
        }

        // Tier 3 — full resource path: /{domainId}/{solutionId}/...
        String solutionId = segments[2];
        int prefixLength = 1 + domainId.length() + 1 + solutionId.length(); // /{domainId}/{solutionId}
        String resourcePath = normalised.length() > prefixLength
                ? normalised.substring(prefixLength)
                : "/";

        return new ParsedPath(domainId, solutionId, resourcePath);
    }
}
