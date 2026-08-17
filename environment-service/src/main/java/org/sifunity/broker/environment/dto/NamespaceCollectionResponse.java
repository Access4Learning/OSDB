// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Response wrapper for a collection of namespaces.
 *
 * <p>Wraps the namespace collection under a {@code namespaces} key per SIF 3.7 convention.</p>
 *
 * @param namespaces the namespace collection
 * @see NamespaceCollection
 */
public record NamespaceCollectionResponse(
    NamespaceCollection namespaces
) {}
