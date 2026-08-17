// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Response wrapper for a single context.
 * 
 * <p>Wraps the context DTO under a {@code context} key per SIF 3.7 object-service convention.</p>
 * 
 * @param context the context data
 * @see ContextDTO
 */
public record ContextResponse(
    ContextDTO context
) {}
