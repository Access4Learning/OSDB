// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import java.util.UUID;

import org.sifunity.broker.common.constant.AccessType;

/**
 * Single source of truth for the ACL permission cache key used by both cache layers
 * (the gateway {@code EnvironmentServiceClient} Caffeine cache and the environment-service
 * {@code AuthorizationService} {@code @Cacheable("acl-permissions")} cache).
 *
 * <p>SIF 3.7 ACL permissions are scoped per zone and context, so the cache key MUST include
 * every dimension the authorization decision depends on. This builder produces a key of the
 * shape:</p>
 *
 * <pre>{environmentId} : {normalizedZoneId} : {normalizedContextId} : {serviceName} : {accessType}</pre>
 *
 * <p>Both layers call this class so their keys are byte-identical for identical scopes and can
 * never drift apart. Null or blank {@code zoneId}/{@code contextId} values are mapped to a
 * single stable {@link #NULL_SCOPE_SENTINEL sentinel} that cannot collide with any real SIF
 * identifier.</p>
 */
public final class PermissionCacheKey {

    /**
     * Sentinel used for a null or blank zone/context scope.
     *
     * <p>The NUL control character ({@code \u0000}) can never appear in a legal SIF zone or
     * context identifier, so the null-scope key can never collide with a real identifier (for
     * example, a real {@code contextId} whose text happens to equal a readable token).</p>
     */
    public static final String NULL_SCOPE_SENTINEL = "\u0000";

    /**
     * Normalizes a zone/context scope value for use in the cache key.
     *
     * @param value the raw zone or context identifier (may be null or blank)
     * @return {@link #NULL_SCOPE_SENTINEL} when {@code value} is null or blank; the raw value otherwise
     */
    public static String normalizeScope(String value) {
        return (value == null || value.isBlank()) ? NULL_SCOPE_SENTINEL : value;
    }

    /**
     * Builds the permission cache key for a single scope.
     *
     * @param environmentId the environment UUID (non-null)
     * @param zoneId        the zone identifier (normalized; may be null or blank)
     * @param contextId     the context identifier (normalized; may be null or blank)
     * @param serviceName   the service name
     * @param accessType    the access type
     * @return the composite key {@code env:zone:ctx:svc:accessType}
     */
    public static String of(UUID environmentId, String zoneId, String contextId,
                            String serviceName, AccessType accessType) {
        return environmentId
                + ":" + normalizeScope(zoneId)
                + ":" + normalizeScope(contextId)
                + ":" + serviceName
                + ":" + accessType;
    }

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private PermissionCacheKey() {
        throw new UnsupportedOperationException("Utility class");
    }
}
