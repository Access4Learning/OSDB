// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * PESC-JSON representation of a SIF extended element for API payloads.
 * 
 * <p>Uses PESC-JSON casing convention: {@code Name} (uppercase N) and
 * {@code value} (lowercase v).</p>
 * 
 * @param Name  the extended element name (uppercase per PESC-JSON)
 * @param value the extended element value (lowercase per PESC-JSON)
 * @see ExtendedElement
 */
public record SifExtendedElement(
    String Name,
    String value
) {}
