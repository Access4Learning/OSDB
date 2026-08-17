// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * The SIF 3.7 {@code protocol} definition — a provider's endpoint (D1).
 *
 * <p>Schema: {@code required: ["location"]}. In a brokered environment {@code location} is the URL
 * where requests for this provider are re-invoked by the connector.</p>
 *
 * <p>This replaces the broker's previous flat {@code endpoint} string. The distinction matters
 * because the schema types the endpoint as an <em>object</em>: a flat string cannot carry the
 * {@code properties} that describe protocol settings, so a conforming client could not express them
 * at all.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProtocolDTO(

    /** The URL to send requests to. Required by the schema. */
    @NotBlank(message = "endPoint location is required")
    String location,

    /** Optional protocol settings as name/value pairs. */
    @Valid
    PropertiesDTO properties
) {

    /**
     * Convenience factory for the common case of a bare URL with no protocol properties.
     *
     * @param location the endpoint URL
     * @return a protocol carrying only the location, or {@code null} if {@code location} is null
     */
    public static ProtocolDTO of(String location) {
        return location == null ? null : new ProtocolDTO(location, null);
    }

    /**
     * Null-safe read of a protocol's location.
     *
     * <p>The {@code provider_registrations.endpoint} column is nullable and the schema does not list
     * {@code endPoint} as required, so a protocol reference is genuinely allowed to be absent. Callers
     * that only want the URL would otherwise each need their own null check — {@code p.endPoint()} can
     * be null, which makes a bare {@code p.endPoint().location()} an NPE where the removed
     * {@code ProviderRegistrationDTO.endpoint()} accessor passed null through.</p>
     *
     * <p>This is deliberately <em>not</em> a revival of that accessor: it lives on {@code ProtocolDTO}
     * and takes the protocol as an argument, so a reader still goes through {@code endPoint()} and
     * cannot mistake the endpoint for a flat field on the provider.</p>
     *
     * @param protocol the protocol reference, may be {@code null}
     * @return the location, or {@code null} when {@code protocol} is null
     */
    public static String locationOf(ProtocolDTO protocol) {
        return protocol == null ? null : protocol.location();
    }
}
