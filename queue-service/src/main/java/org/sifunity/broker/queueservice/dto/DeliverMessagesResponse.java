// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

import java.util.List;

/**
 * Response DTO for message delivery operation.
 */
public record DeliverMessagesResponse(
    List<MessageResponse> messages
) {
}
