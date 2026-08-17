// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;

/**
 * Client-side view of the broker's {@code provider} response envelope (D1).
 *
 * <p>{@code POST /providers/provider}, {@code GET /providers/lookup} and {@code PUT /providers/{id}}
 * all return {@code {"provider": { ... }}}. Deserializing those into a bare
 * {@link ProviderRegistrationDTO} fails, because {@code provider} is then an unknown property.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResponse {
    private ProviderRegistrationDTO provider;
}
