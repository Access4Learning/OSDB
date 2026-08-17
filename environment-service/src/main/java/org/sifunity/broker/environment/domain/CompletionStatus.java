// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

/**
 * Completion status for a provision request.
 *
 * <p>Indicates the aggregate resolution outcome of all right assertions
 * in a provision request:</p>
 * <ul>
 *   <li>{@code ACCEPTED} — all assertions were APPROVED</li>
 *   <li>{@code MIXED} — some assertions were APPROVED and some REJECTED</li>
 *   <li>{@code REJECTED} — all assertions were REJECTED</li>
 * </ul>
 *
 * @see ProvisionRequest
 */
public enum CompletionStatus {
    ACCEPTED,
    MIXED,
    REJECTED
}
