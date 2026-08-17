// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

/**
 * Stateless utility class for formatting the {@code relativeServicePath} HTTP header
 * used in outbound SIF message delivery.
 *
 * <p>SIF 3.3 legacy environments expect the service path to include matrix parameters
 * (e.g., {@code /StudentPersonals;contextId=DEFAULT;zoneId=zone1}), while modern SIF 3.7
 * environments expect only the service name (e.g., {@code /StudentPersonals}).</p>
 *
 * <p>This formatter produces the correct format based on a legacy flag, and appends
 * any query string when present.</p>
 *
 * @see <a href="https://specification.sifassociation.org/">SIF 3.3 Base Architecture §5.14.1</a>
 */
public final class RelativeServicePathFormatter {

    /**
     * Formats the {@code relativeServicePath} header value based on legacy preference.
     *
     * <p>For legacy format (SIF 3.3), the path includes matrix parameters in the order
     * {@code contextId} then {@code zoneId}, matching the SIF specification examples.
     * For modern format (SIF 3.7), only the service name is included.</p>
     *
     * <p>If a query string is provided (non-null, non-empty), it is appended after a
     * {@code ?} character. The path always starts with {@code /}.</p>
     *
     * @param serviceName  the SIF service name (e.g., "StudentPersonals")
     * @param zoneId       the zone ID
     * @param contextId    the context ID
     * @param queryString  the original query string (may be null or empty; should not include leading '?')
     * @param legacyFormat true if the recipient expects matrix parameters in the path
     * @return formatted path (e.g., "/StudentPersonals;contextId=DEFAULT;zoneId=zone1?where=..." or "/StudentPersonals?where=...")
     */
    public static String format(String serviceName, String zoneId, String contextId, String queryString, boolean legacyFormat) {
        StringBuilder sb = new StringBuilder();

        // Path always starts with /
        sb.append('/');

        // Append service name
        if (serviceName != null) {
            sb.append(serviceName);
        }

        // For legacy format, append matrix params: contextId first, then zoneId
        if (legacyFormat) {
            sb.append(";contextId=").append(contextId != null ? contextId : "");
            sb.append(";zoneId=").append(zoneId != null ? zoneId : "");
        }

        // Append query string if present
        if (queryString != null && !queryString.isEmpty()) {
            sb.append('?').append(queryString);
        }

        return sb.toString();
    }

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private RelativeServicePathFormatter() {
        throw new UnsupportedOperationException("Utility class");
    }
}
