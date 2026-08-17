// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Single-resource wrapper for SIF 3.7 environment responses.
 *
 * <p>Wraps a {@link SifEnvironmentResponse} under an "environment" key to match
 * the SIF 3.7 specification's singular resource response format:
 * {@code {"environment": { ... }}}</p>
 *
 * @param environment the environment DTO
 * @see SifEnvironmentResponse
 */
public record EnvironmentResponse(
    SifEnvironmentResponse environment
) {
}
