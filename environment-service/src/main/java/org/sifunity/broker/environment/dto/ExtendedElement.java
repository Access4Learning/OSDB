// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Internal representation of a SIF extended element for JSONB storage.
 * 
 * <p>Stored as JSON in the {@code extended_elements} JSONB column of the
 * {@code alerts} table. Uses lowercase field names for internal storage,
 * unlike the PESC-JSON format used in API payloads.</p>
 * 
 * @param name  the extended element name
 * @param value the extended element value
 * @see SifExtendedElement
 */
public record ExtendedElement(
    String name,
    String value
) {}
