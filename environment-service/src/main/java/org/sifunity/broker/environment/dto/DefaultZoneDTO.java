// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing the default zone in a SIF 3.7 environment response.
 *
 * @param id the zone identifier
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DefaultZoneDTO(
    String id
) {

    /**
     * Builds a default-zone reference, or {@code null} when there is no zone.
     *
     * <p>D3 is specifically about the difference between an <b>omitted</b> {@code defaultZone} key and
     * a present-but-empty {@code {"defaultZone": {"id": null}}} — the latter is the non-conformant form.
     * A wrapper is therefore only correct when there is an id to put in it, and callers holding a
     * possibly-null id must not use the canonical constructor directly.</p>
     *
     * @param id the zone identifier, may be {@code null}
     * @return the wrapper, or {@code null} when {@code id} is null
     */
    public static DefaultZoneDTO of(String id) {
        return id == null ? null : new DefaultZoneDTO(id);
    }

    /**
     * Null-safe read of a default zone's id.
     *
     * <p>Because D3 omits the key entirely when there is no zone, {@code defaultZone()} is routinely
     * null and {@code defaultZone().id()} is routinely an NPE. Mirrors
     * {@link org.sifunity.broker.common.dto.ProtocolDTO#locationOf}.</p>
     *
     * @param zone the zone reference, may be {@code null}
     * @return the zone id, or {@code null} when {@code zone} is null
     */
    public static String idOf(DefaultZoneDTO zone) {
        return zone == null ? null : zone.id();
    }
}
