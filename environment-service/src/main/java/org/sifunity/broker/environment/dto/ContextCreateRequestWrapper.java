// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request wrapper for context creation (SIF object-service convention).
 *
 * <p>Wraps the context creation body under a {@code context} key
 * ({@code { "context": { "id": ..., "description": ... } }}), mirroring
 * {@link CodeSetCreateRequest} and the SIF 3.7 request envelope pattern.</p>
 *
 * <p>The {@code @Valid} annotation cascades Bean Validation into the inner
 * {@link ContextCreateBody}, so the {@code @NotBlank}/{@code @Pattern} constraints on
 * {@code id} are enforced and an alias-only body (identifier supplied only under the
 * legacy {@code contextId}, {@code id} omitted) is rejected with {@code 400 Bad Request}.</p>
 *
 * @param context the context creation body
 * @see ContextCreateBody
 */
public record ContextCreateRequestWrapper(
    @NotNull(message = "context body is required")
    @Valid
    ContextCreateBody context
) {}
