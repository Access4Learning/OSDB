// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import java.util.Set;

/**
 * Static mapping from URL resource paths to valid serviceType header values.
 *
 * <p>After the {@code /{domainId}/{solutionId}} prefix is stripped, the remaining
 * resource path determines which serviceType values are permitted per SIF 3.3 §5.1.</p>
 *
 * <p>Categorization rules:</p>
 * <ul>
 *   <li>{@code /requests/**} → REQUESTS (OBJECT, SERVICEPATH, XQUERYTEMPLATE)</li>
 *   <li>{@code /services/**} → SERVICES (FUNCTIONAL)</li>
 *   <li>{@code /events} or {@code /events/**} → EVENTS (all six SIF types)</li>
 *   <li>{@code /zones}, {@code /providers}, {@code /xquerys}, {@code /alerts},
 *       {@code /namespaces}, {@code /codesets} → UTILITY (UTILITY)</li>
 * </ul>
 *
 * <p>Paths not matching any category return {@code null} — no validation applies.</p>
 *
 * <p>Requirements: 1.1, 2.1, 3.1, 4.1</p>
 */
public final class ServiceTypePathMapping {

    private ServiceTypePathMapping() {
        // utility class
    }

    /**
     * Categories of URL paths with their corresponding valid serviceType values.
     */
    public enum PathCategory {
        REQUESTS(Set.of("OBJECT", "SERVICEPATH", "XQUERYTEMPLATE")),
        SERVICES(Set.of("FUNCTIONAL")),
        EVENTS(Set.of("OBJECT", "UTILITY", "FUNCTIONAL", "SERVICEPATH", "XQUERYTEMPLATE", "SERVICE")),
        UTILITY(Set.of("UTILITY"));

        private final Set<String> validServiceTypes;

        PathCategory(Set<String> validServiceTypes) {
            this.validServiceTypes = Set.copyOf(validServiceTypes);
        }

        /**
         * Returns the immutable set of valid serviceType values (uppercase) for this category.
         */
        public Set<String> getValidServiceTypes() {
            return validServiceTypes;
        }
    }

    private static final Set<String> UTILITY_PREFIXES = Set.of(
        "/zones", "/providers", "/xquerys", "/alerts", "/namespaces", "/codesets"
    );

    /**
     * Determines the {@link PathCategory} for a given resource path (after prefix stripping).
     *
     * @param resourcePath the resource path portion of the URL (e.g. {@code /requests/Students})
     * @return the matching {@link PathCategory}, or {@code null} if the path is not subject
     *         to serviceType validation
     */
    public static PathCategory categorize(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return null;
        }

        if (resourcePath.startsWith("/requests")) {
            return PathCategory.REQUESTS;
        }

        if (resourcePath.startsWith("/services")) {
            return PathCategory.SERVICES;
        }

        if (resourcePath.equals("/events") || resourcePath.startsWith("/events/")) {
            return PathCategory.EVENTS;
        }

        for (String prefix : UTILITY_PREFIXES) {
            if (resourcePath.startsWith(prefix)) {
                return PathCategory.UTILITY;
            }
        }

        return null;
    }
}
