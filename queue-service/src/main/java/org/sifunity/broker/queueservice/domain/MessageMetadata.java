// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Message metadata stored in Redis.
 * Contains tracking information for message delivery and acknowledgment.
 */
public class MessageMetadata {
    
    private UUID messageId;
    private UUID queueId;
    private String storageKey;
    private Instant enqueuedAt;
    private Instant deliveredAt;
    private Integer deliveryAttempts;
    private MessageStatus status;
    private Instant visibilityTimeout;
    private Object payload;
    private String streamId;
    private Map<String, String> sifHeaders;
    
    // Constructors
    public MessageMetadata() {
    }
    
    public MessageMetadata(UUID messageId, UUID queueId, String storageKey, Instant enqueuedAt,
                          Instant deliveredAt, Integer deliveryAttempts, MessageStatus status,
                          Instant visibilityTimeout, Object payload) {
        this.messageId = messageId;
        this.queueId = queueId;
        this.storageKey = storageKey;
        this.enqueuedAt = enqueuedAt;
        this.deliveredAt = deliveredAt;
        this.deliveryAttempts = deliveryAttempts;
        this.status = status;
        this.visibilityTimeout = visibilityTimeout;
        this.payload = payload;
    }
    
    public MessageMetadata(UUID messageId, UUID queueId, String storageKey, Instant enqueuedAt,
                          Instant deliveredAt, Integer deliveryAttempts, MessageStatus status,
                          Instant visibilityTimeout, Object payload, String streamId) {
        this(messageId, queueId, storageKey, enqueuedAt, deliveredAt, deliveryAttempts,
             status, visibilityTimeout, payload);
        this.streamId = streamId;
    }
    
    // Getters and Setters
    public UUID getMessageId() {
        return messageId;
    }
    
    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }
    
    public UUID getQueueId() {
        return queueId;
    }
    
    public void setQueueId(UUID queueId) {
        this.queueId = queueId;
    }
    
    public String getStorageKey() {
        return storageKey;
    }
    
    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }
    
    public Instant getEnqueuedAt() {
        return enqueuedAt;
    }
    
    public void setEnqueuedAt(Instant enqueuedAt) {
        this.enqueuedAt = enqueuedAt;
    }
    
    public Instant getDeliveredAt() {
        return deliveredAt;
    }
    
    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
    
    public Integer getDeliveryAttempts() {
        return deliveryAttempts;
    }
    
    public void setDeliveryAttempts(Integer deliveryAttempts) {
        this.deliveryAttempts = deliveryAttempts;
    }
    
    public MessageStatus getStatus() {
        return status;
    }
    
    public void setStatus(MessageStatus status) {
        this.status = status;
    }
    
    public Instant getVisibilityTimeout() {
        return visibilityTimeout;
    }
    
    public void setVisibilityTimeout(Instant visibilityTimeout) {
        this.visibilityTimeout = visibilityTimeout;
    }
    
    public Object getPayload() {
        return payload;
    }
    
    public void setPayload(Object payload) {
        this.payload = payload;
    }
    
    public String getStreamId() {
        return streamId;
    }
    
    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public Map<String, String> getSifHeaders() { return sifHeaders; }
    public void setSifHeaders(Map<String, String> sifHeaders) { this.sifHeaders = sifHeaders; }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private UUID messageId;
        private UUID queueId;
        private String storageKey;
        private Instant enqueuedAt;
        private Instant deliveredAt;
        private Integer deliveryAttempts;
        private MessageStatus status;
        private Instant visibilityTimeout;
        private Object payload;
        private String streamId;
        private Map<String, String> sifHeaders;
        
        public Builder messageId(UUID messageId) {
            this.messageId = messageId;
            return this;
        }
        
        public Builder queueId(UUID queueId) {
            this.queueId = queueId;
            return this;
        }
        
        public Builder storageKey(String storageKey) {
            this.storageKey = storageKey;
            return this;
        }
        
        public Builder enqueuedAt(Instant enqueuedAt) {
            this.enqueuedAt = enqueuedAt;
            return this;
        }
        
        public Builder deliveredAt(Instant deliveredAt) {
            this.deliveredAt = deliveredAt;
            return this;
        }
        
        public Builder deliveryAttempts(Integer deliveryAttempts) {
            this.deliveryAttempts = deliveryAttempts;
            return this;
        }
        
        public Builder status(MessageStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder visibilityTimeout(Instant visibilityTimeout) {
            this.visibilityTimeout = visibilityTimeout;
            return this;
        }
        
        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }
        
        public Builder streamId(String streamId) {
            this.streamId = streamId;
            return this;
        }

        public Builder sifHeaders(Map<String, String> sifHeaders) {
            this.sifHeaders = sifHeaders;
            return this;
        }
        
        public MessageMetadata build() {
            MessageMetadata m = new MessageMetadata(messageId, queueId, storageKey, enqueuedAt,
                    deliveredAt, deliveryAttempts, status, visibilityTimeout, payload, streamId);
            m.setSifHeaders(sifHeaders);
            return m;
        }
    }
}
