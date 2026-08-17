// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for environment registration requests (test data generation), wrapped under {@code environment} (D2).
 *
 * <p>This was a flat class carrying {@code applicationName}, {@code type} and {@code defaultZoneId}
 * directly. The broker now requires the SIF envelope form {@code {"environment": { ... }}} and
 * <b>rejects</b> the flat form rather than tolerating it: {@code FAIL_ON_UNKNOWN_PROPERTIES} is enabled,
 * so the old field names read as unknown properties and the request is a 400.</p>
 *
 * @see EnvironmentRegistrationBody
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentRegistrationRequest {
    private EnvironmentRegistrationBody environment;
}
