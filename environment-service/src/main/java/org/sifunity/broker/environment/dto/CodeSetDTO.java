// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object for code set information in API responses.
 * 
 * <p>Contains all code set fields per the SIF 3.7 schema:
 * {@code id}, {@code zone}, {@code version}, {@code timestamp},
 * {@code source}, and {@code codeItems}.</p>
 * 
 * @param id        the code set identifier
 * @param zone      the zone this code set belongs to
 * @param version   the version of this code set
 * @param timestamp the timestamp of the last modification
 * @param source    optional source URI
 * @param codeItems optional code items within this code set
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CodeSetDTO(
    String id,
    String zone,
    String version,
    String timestamp,
    String source,
    CodeItemsDTO codeItems
) {}
