// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Client-side view of the broker's {@code environment} registration response envelope (D2).
 *
 * <p>{@code POST /environments/environment} returns {@code {"environment": { ... }}}. Deserializing that
 * straight into a flat {@link EnvironmentRegistrationResponse} fails, because {@code environment} is then
 * an unknown property.</p>
 *
 * <p>Introduced as a separate envelope rather than by reshaping
 * {@code EnvironmentRegistrationResponse} so that the many callers reading {@code getId()} /
 * {@code getSessionToken()} / {@code getSharedSecret()} off the flat type keep working unchanged — the
 * generator unwraps once and returns what it always returned.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvironmentRegistrationEnvelope {
    private EnvironmentRegistrationResponse environment;
}
