// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.List;

/**
 * Collection of namespace DTOs for list responses.
 *
 * <p>Uses {@code namespace} as the array key, consistent with the SifCollection
 * pattern used throughout the broker.</p>
 *
 * @param namespace list of namespace DTOs
 * @see NamespaceDTO
 */
public record NamespaceCollection(
    List<NamespaceDTO> namespace
) {}
