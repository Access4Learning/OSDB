// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Result of an any-grant entitlement check against
 * {@code POST /internal/check-any-permission} on environment-service.
 *
 * <p><strong>{@code ignoreUnknown = true} is load-bearing, not cosmetic.</strong> The shared
 * production {@code ObjectMapper} enables {@code FAIL_ON_UNKNOWN_PROPERTIES}
 * ({@code JacksonConfiguration}), so any field added to the server-side response later would
 * otherwise fail deserialization here. Because the destination-ACL gate is deliberately
 * fail-closed, that would not surface as a parse error — it would silently reject every response
 * submission. The equivalent omission on the queue-owner wrapper record already caused exactly
 * that outage once (404 on every DELAYED request), so it is pinned here from the start.</p>
 *
 * @param granted whether the environment holds at least one approved ACL grant for the
 *                service/zone/context that was checked
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnyGrantCheckResult(boolean granted) {
}
