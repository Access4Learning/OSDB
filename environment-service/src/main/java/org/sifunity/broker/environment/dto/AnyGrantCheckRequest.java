// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to check whether an environment holds ANY approved ACL grant for a service,
 * regardless of access type.
 *
 * <p>Deliberately a separate record from {@link PermissionCheckRequest} rather than reusing it
 * with a nullable {@code accessType}: that field is {@code @NotNull} there, and relaxing it would
 * weaken validation for every existing caller of the far hotter
 * {@code POST /internal/check-permission}.</p>
 *
 * <p>Used by {@code responses-connector} to decide whether the environment owning a
 * caller-supplied destination queue is entitled to the object being delivered. The specific
 * access type cannot be known at that point — a delayed response is routed to the queue of the
 * consumer that made the original request, and that consumer may hold only {@code CREATE},
 * {@code UPDATE} or {@code DELETE} rather than {@code QUERY}. Requiring a specific type would
 * reject legitimate responses.</p>
 *
 * @param environmentId the environment whose entitlement is being checked — normally the owner of
 *                      the destination queue, NOT the caller
 * @param serviceName the SIF object/service name being delivered
 * @param zoneId the zone qualifying the service
 * @param contextId the context qualifying the service; when null the lookup is context-wildcard,
 *                  matching {@link PermissionCheckRequest} semantics
 */
public record AnyGrantCheckRequest(

    @NotNull(message = "Environment ID is required")
    UUID environmentId,

    @NotNull(message = "Service name is required")
    String serviceName,

    String zoneId,

    String contextId

) {
}
