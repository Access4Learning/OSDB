// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Request wrapper for namespace creation.
 *
 * <p>Wraps the namespace creation body under a {@code namespace} key,
 * matching the SIF 3.7 request envelope pattern.</p>
 *
 * @param namespace the namespace creation body
 * @see NamespaceCreateBody
 */
public record NamespaceCreateRequest(
    NamespaceCreateBody namespace
) {}
