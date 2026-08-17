// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Data Transfer Object for namespace information in API responses.
 *
 * <p>Contains all namespace fields per the SIF 3.7 schema:
 * {@code id}, {@code zone}, {@code uri}, {@code url}.</p>
 *
 * @param id   the broker-assigned UUID identifier
 * @param zone the zone this namespace belongs to
 * @param uri  the namespace URI
 * @param url  the schema location URL
 */
public record NamespaceDTO(
    String id,
    String zone,
    String uri,
    String url
) {}
