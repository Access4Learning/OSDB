// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Response wrapper for a single namespace.
 *
 * <p>Wraps the namespace DTO under a {@code namespace} key per SIF 3.7 convention.</p>
 *
 * @param namespace the namespace data
 * @see NamespaceDTO
 */
public record NamespaceResponse(
    NamespaceDTO namespace
) {}
