// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for creating a code set.
 * 
 * <p>Contains required fields ({@code zone}, {@code version}, {@code timestamp})
 * and optional fields ({@code id}, {@code source}, {@code codeItems})
 * per the SIF 3.7 code set schema.</p>
 * 
 * @param id        optional code set identifier (max 128 characters); UUID assigned if absent
 * @param zone      the zone for this code set (required)
 * @param version   the version of this code set (required)
 * @param timestamp the timestamp of this code set (required)
 * @param source    optional source URI
 * @param codeItems optional code items within this code set
 */
public record CodeSetCreateBody(
    String id,

    @NotBlank(message = "Zone is required")
    String zone,

    @NotBlank(message = "Version is required")
    String version,

    @NotBlank(message = "Timestamp is required")
    String timestamp,

    String source,
    CodeItemsDTO codeItems
) {}
