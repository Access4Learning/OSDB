// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for provider registration requests (test data generation), wrapped under {@code provider} (D1).
 *
 * <p>This was a flat class carrying {@code environmentId}, {@code serviceName}, {@code zoneId},
 * {@code contextId}, {@code endpoint}, {@code serviceType}, {@code providerName} and
 * {@code supportedRequestTypes} directly. The broker now requires the SIF envelope form
 * {@code {"provider": { ... }}} and <b>rejects</b> the flat form rather than tolerating it: the broker
 * runs with {@code FAIL_ON_UNKNOWN_PROPERTIES} enabled, so every flat field reads as an unknown
 * property and the request is a 400.</p>
 *
 * @see ProviderRegistrationBody
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRegistrationRequest {
    private ProviderRegistrationBody provider;
}
