// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

/**
 * The approval status of a Named Query (xQueryTemplate).
 *
 * <ul>
 *   <li>{@code PENDING} — awaiting approval</li>
 *   <li>{@code APPROVED} — approved for use by consumers</li>
 *   <li>{@code DISALLOWED} — rejected, not available for invocation</li>
 * </ul>
 *
 * @see XQueryTemplate
 */
public enum XQueryStatus {
    PENDING,
    APPROVED,
    DISALLOWED
}
