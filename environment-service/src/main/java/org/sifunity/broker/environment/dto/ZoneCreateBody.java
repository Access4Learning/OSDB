// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for creating a zone (SIF object-service convention).
 *
 * <p>This is the inner body of the wrapped zone create request
 * ({@code { "zone": { "id": ..., "description": ... } }}), mirroring
 * {@link CodeSetCreateBody} and the SIF 3.7 {@code zone} definition.</p>
 *
 * <p>The SIF business identifier is accepted <strong>only</strong> under the canonical
 * {@code id} field. There is no legacy {@code zoneId} alias: a create body that supplies the
 * identifier only under {@code zoneId} (omitting {@code id}) leaves {@code id} null, so the
 * {@code @NotBlank} constraint fails and the request is rejected with {@code 400 Bad Request}.</p>
 *
 * @param id          the SIF business identifier for the zone (required, must follow the naming pattern)
 * @param description optional description of the zone's purpose
 *
 * @see ZoneCreateRequestWrapper
 * @see ZoneDTO
 */
public record ZoneCreateBody(
    @NotBlank(message = "Zone ID is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?$",
        message = "Zone ID must start with alphanumeric character and contain only alphanumeric characters, underscores, and hyphens"
    )
    String id,

    String description
) {}
