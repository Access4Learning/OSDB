// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a message is not found.
 */
public class MessageNotFoundException extends SifBrokerException {
    
    public MessageNotFoundException(String messageId) {
        super("MSG_404", HttpStatus.NOT_FOUND, "Message not found: " + messageId);
    }
    
    public MessageNotFoundException(String messageId, String details) {
        super("MSG_404", HttpStatus.NOT_FOUND, "Message not found: " + messageId + ". " + details);
    }
}
