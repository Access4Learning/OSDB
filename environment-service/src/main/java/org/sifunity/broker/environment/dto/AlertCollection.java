// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.List;

/**
 * Collection of alert DTOs for list responses.
 * 
 * <p>Uses {@code alert} as the array key, consistent with the SifCollection
 * pattern used throughout the broker.</p>
 * 
 * @param alert list of alert DTOs
 * @see AlertDTO
 */
public record AlertCollection(
    List<AlertDTO> alert
) {}
