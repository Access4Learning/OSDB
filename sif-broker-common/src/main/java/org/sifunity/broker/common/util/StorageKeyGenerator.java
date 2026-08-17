// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import java.util.Set;
import java.util.UUID;

/**
 * Utility class for generating hierarchical storage keys for message persistence.
 * 
 * <p>This class provides methods for generating storage keys that organize messages
 * in a hierarchical structure. The primary method produces keys in the SIF 3.7 format:
 * <pre>{prefix}/{domainId}/{solutionId}/{environmentId}/{zone}/{context}/{messageType}/{service}/{messageId}.json</pre>
 * 
 * <p>Legacy methods using the older format are retained for backward compatibility:
 * <pre>{environment}/{zone}/{context}/{service}/{messageId}.json</pre>
 * 
 * <p>Special characters in path components are sanitized by replacing them with
 * underscores to ensure compatibility with various storage systems.</p>
 * 
 * @see org.sifunity.broker.common.model.SifMessageEnvelope
 */
public final class StorageKeyGenerator {

    private static final Set<String> VALID_MESSAGE_TYPES = Set.of("event", "request", "response");

    /**
     * Generates a hierarchical storage key with domain/solution hierarchy and configurable prefix.
     * 
     * <p>This is the primary method for SIF 3.7 storage key generation. The format is:
     * <pre>{prefix}/{domainId}/{solutionId}/{environmentId}/{zone}/{context}/{messageType}/{service}/{messageId}.json</pre>
     * 
     * <p>If {@code prefix} is null or empty, the key starts directly with {@code domainId/...}.
     * If {@code context} is null or empty, it defaults to {@code "default"}.
     * The {@code messageType} must be one of: {@code event}, {@code request}, {@code response}.</p>
     * 
     * @param prefix        configurable path prefix (may be null or empty)
     * @param domainId      human-readable domain identifier
     * @param solutionId    human-readable solution identifier
     * @param environmentId environment UUID as string
     * @param zone          sanitized zone identifier
     * @param context       sanitized context identifier (may be null, defaults to "default")
     * @param messageType   one of: event, request, response
     * @param service       the service/object name (e.g., "StudentPersonal")
     * @param messageId     the message UUID
     * @return the hierarchical storage key ending with ".json"
     * @throws IllegalArgumentException if messageType is not one of event, request, response
     */
    public static String generateKey(String prefix, String domainId, String solutionId,
                                      String environmentId, String zone, String context,
                                      String messageType, String service, UUID messageId) {
        if (messageType == null || !VALID_MESSAGE_TYPES.contains(messageType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "messageType must be one of: event, request, response; got: " + messageType);
        }

        String resolvedContext = (context == null || context.isEmpty()) ? "default" : context;

        StringBuilder key = new StringBuilder();

        if (prefix != null && !prefix.isEmpty()) {
            key.append(sanitize(prefix)).append("/");
        }

        key.append(sanitize(domainId)).append("/");
        key.append(sanitize(solutionId)).append("/");
        key.append(sanitize(environmentId)).append("/");
        key.append(sanitize(zone)).append("/");
        key.append(sanitize(resolvedContext)).append("/");
        key.append(sanitize(messageType.toLowerCase())).append("/");
        key.append(sanitize(service)).append("/");
        key.append(messageId.toString()).append(".json");

        return key.toString();
    }

    // ========== Legacy methods (backward compatibility) ==========

    /**
     * Generates a hierarchical storage key with all parameters (legacy format).
     * 
     * <p>This method creates a storage key in the format:
     * <pre>{environment}/{zone}/{context}/{service}/{messageId}.json</pre>
     * 
     * <p>All path components (environment, zone, context, service) are sanitized
     * to remove special characters that might cause issues in storage paths.
     * If context is null, it defaults to "default".</p>
     * 
     * @param environment the environment name or ID
     * @param zone        the zone ID
     * @param context     the context ID (may be null, defaults to "default")
     * @param service     the service name
     * @param messageId   the message UUID
     * @return the hierarchical storage key ending with ".json"
     */
    public static String generateKey(String environment, String zone, String context, 
                                     String service, UUID messageId) {
        StringBuilder key = new StringBuilder();
        key.append(sanitize(environment)).append("/");
        key.append(sanitize(zone)).append("/");
        key.append(sanitize(context != null ? context : "default")).append("/");
        key.append(sanitize(service)).append("/");
        key.append(messageId.toString()).append(".json");
        return key.toString();
    }
    
    /**
     * Generates a hierarchical storage key without context parameter (legacy format).
     * 
     * <p>This is a convenience overload that calls
     * {@link #generateKey(String, String, String, String, UUID)} with
     * context set to null, which results in "default" being used.</p>
     * 
     * <p>The generated key format is:
     * <pre>{environment}/{zone}/default/{service}/{messageId}.json</pre>
     * 
     * @param environment the environment name or ID
     * @param zone        the zone ID
     * @param service     the service name
     * @param messageId   the message UUID
     * @return the hierarchical storage key with "default" context, ending with ".json"
     */
    public static String generateKey(String environment, String zone, String service, UUID messageId) {
        return generateKey(environment, zone, null, service, messageId);
    }
    
    /**
     * Sanitizes a string value for use in storage paths.
     * 
     * <p>This method removes special characters that might cause issues in
     * storage paths by replacing any character that is not alphanumeric,
     * hyphen, or underscore with an underscore.</p>
     * 
     * <p>Examples:
     * <ul>
     *   <li>"env-123" → "env-123" (no change)</li>
     *   <li>"zone@default" → "zone_default" (@ replaced)</li>
     *   <li>"context/school" → "context_school" (/ replaced)</li>
     *   <li>"service.name" → "service_name" (. replaced)</li>
     * </ul>
     * 
     * @param value the string value to sanitize
     * @return the sanitized string with special characters replaced by underscores
     */
    static String sanitize(String value) {
        if (value == null) return "default";
        return value.replaceAll("[^a-zA-Z0-9-_]", "_");
    }
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private StorageKeyGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }
}
