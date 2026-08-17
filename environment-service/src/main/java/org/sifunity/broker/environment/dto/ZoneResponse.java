// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Response wrapper for a single zone.
 * 
 * <p>Wraps the zone DTO under a {@code zone} key per SIF 3.7 object-service convention.</p>
 * 
 * @param zone the zone data
 * @see ZoneDTO
 */
public record ZoneResponse(
    ZoneDTO zone
) {}
