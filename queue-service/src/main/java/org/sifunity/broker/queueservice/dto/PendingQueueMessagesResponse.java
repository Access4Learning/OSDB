// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

import java.util.List;

/** Administrative response containing messages reserved by queue consumers. */
public record PendingQueueMessagesResponse(
        List<PendingQueueMessageResponse> pendingMessages) {
}
