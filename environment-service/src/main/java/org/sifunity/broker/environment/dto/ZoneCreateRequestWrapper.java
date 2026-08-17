// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request wrapper for zone creation (SIF object-service convention).
 *
 * <p>Wraps the zone creation body under a {@code zone} key
 * ({@code { "zone": { "id": ..., "description": ... } }}), mirroring
 * {@link CodeSetCreateRequest} and the SIF 3.7 request envelope pattern.</p>
 *
 * <p>The {@code @Valid} annotation cascades Bean Validation into the inner
 * {@link ZoneCreateBody}, so the {@code @NotBlank}/{@code @Pattern} constraints on
 * {@code id} are enforced and an alias-only body (identifier supplied only under the
 * legacy {@code zoneId}, {@code id} omitted) is rejected with {@code 400 Bad Request}.</p>
 *
 * @param zone the zone creation body
 * @see ZoneCreateBody
 */
public record ZoneCreateRequestWrapper(
    @NotNull(message = "zone body is required")
    @Valid
    ZoneCreateBody zone
) {}
