// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Message context retrieved from the Redis cache.
 * 
 * <p>Contains metadata about the originating message, used to populate
 * server-side extended elements when creating alerts with a {@code messageID}.</p>
 * 
 * @param sourceEnvironmentId UUID of the environment that sent the original message
 * @param zoneId              zone ID from the original request
 * @param contextId           context ID from the original request (or "DEFAULT")
 */
public record MessageContext(
    String sourceEnvironmentId,
    String zoneId,
    String contextId
) {}
