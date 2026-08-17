// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import org.sifunity.broker.common.dto.ProviderRegistrationDTO;

/**
 * Single-resource wrapper for SIF 3.7 provider responses.
 *
 * <p>Wraps a {@link ProviderRegistrationDTO} under a "provider" key to match
 * the SIF 3.7 specification's singular resource response format:
 * {@code {"provider": { ... }}}</p>
 *
 * @param provider the provider registration DTO
 * @see ProviderRegistrationDTO
 */
public record ProviderResponse(
    ProviderRegistrationDTO provider
) {
}
