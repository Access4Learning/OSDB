// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for creating a context (SIF object-service convention).
 *
 * <p>This is the inner body of the wrapped context create request
 * ({@code { "context": { "id": ..., "description": ... } }}), mirroring
 * {@link CodeSetCreateBody} and the SIF 3.7 {@code context} definition.</p>
 *
 * <p>The SIF business identifier is accepted <strong>only</strong> under the canonical
 * {@code id} field. There is no legacy {@code contextId} alias: a create body that supplies the
 * identifier only under {@code contextId} (omitting {@code id}) leaves {@code id} null, so the
 * {@code @NotBlank} constraint fails and the request is rejected with {@code 400 Bad Request}.</p>
 *
 * @param id          the SIF business identifier for the context (required, must follow the naming pattern)
 * @param description optional description of the context's purpose
 *
 * @see ContextCreateRequestWrapper
 * @see ContextDTO
 */
public record ContextCreateBody(
    @NotBlank(message = "Context ID is required")
    @Pattern(
        regexp = "^[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?$",
        message = "Context ID must start with alphanumeric character and contain only alphanumeric characters, underscores, and hyphens"
    )
    String id,

    String description
) {}
