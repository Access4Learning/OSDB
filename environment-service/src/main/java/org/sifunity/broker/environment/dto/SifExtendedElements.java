// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.List;

/**
 * Wrapper for a list of SIF extended elements in PESC-JSON format.
 * 
 * <p>Matches the SIF 3.7 structure:
 * {@code { "SIF_ExtendedElements": { "SIF_ExtendedElement": [...] } }}</p>
 * 
 * @param SIF_ExtendedElement list of extended element name/value pairs
 * @see SifExtendedElement
 */
public record SifExtendedElements(
    List<SifExtendedElement> SIF_ExtendedElement
) {}
