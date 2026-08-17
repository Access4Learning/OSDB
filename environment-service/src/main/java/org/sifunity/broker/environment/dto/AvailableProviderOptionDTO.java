// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.UUID;

/**
 * Data Transfer Object representing one available provider registration option.
 *
 * <p>Each instance represents a context_service where the environment has a PROVIDE ACL
 * (accessType=PROVIDE, accessLevel=APPROVED) but no existing provider registration.
 * These options are returned from the available-options endpoint so the admin console
 * can populate cascading dropdowns for provider registration.</p>
 *
 * @param contextServiceUuid UUID of the context_service to use for registration
 * @param zoneId             Zone identifier (resolved from Zone entity)
 * @param contextId          Context identifier (resolved from Context entity)
 * @param serviceName        Service name from context_services
 * @param serviceType        Service type from context_services (e.g., OBJECT, UTILITY, FUNCTIONAL)
 *
 * @see ProviderRegistrationRequest
 */
public record AvailableProviderOptionDTO(
    /**
     * UUID of the context_service to use for registration.
     * This is the value submitted when creating a provider registration.
     */
    UUID contextServiceUuid,

    /**
     * Zone identifier resolved from the Zone entity via Context → Zone chain.
     */
    String zoneId,

    /**
     * Context identifier resolved from the Context entity.
     */
    String contextId,

    /**
     * Name of the service from the context_services table.
     */
    String serviceName,

    /**
     * Type of the service from the context_services table.
     * One of: UTILITY, OBJECT, FUNCTIONAL, SERVICEPATH, XQUERYTEMPLATE, SERVICE.
     */
    String serviceType
) {
}
