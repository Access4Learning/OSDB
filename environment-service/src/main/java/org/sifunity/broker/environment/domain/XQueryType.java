// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

/**
 * The type of a Named Query (xQueryTemplate).
 *
 * <ul>
 *   <li>{@code SINGULAR} — returns a single object</li>
 *   <li>{@code FORMULA} — returns a computed/derived result</li>
 *   <li>{@code EXTENDED} — returns an extended/complex result set</li>
 * </ul>
 *
 * @see XQueryTemplate
 */
public enum XQueryType {
    SINGULAR,
    FORMULA,
    EXTENDED
}
