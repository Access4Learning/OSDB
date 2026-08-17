// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating or updating a namespace.
 *
 * <p>Contains required fields ({@code zone}, {@code uri}, {@code url})
 * per the SIF 3.7 namespace schema. The {@code id} field is ignored on
 * create — the broker assigns a UUID.</p>
 *
 * @param id   ignored on create (broker assigns UUID)
 * @param zone the zone for this namespace (required)
 * @param uri  the namespace URI (required, max 2048 characters)
 * @param url  the schema location URL (required, max 2048 characters)
 */
public record NamespaceCreateBody(
    String id,

    @NotBlank(message = "Zone is required")
    String zone,

    @NotBlank(message = "URI is required")
    String uri,

    @NotBlank(message = "URL is required")
    String url
) {}
