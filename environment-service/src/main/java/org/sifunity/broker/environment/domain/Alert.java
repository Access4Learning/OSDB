// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.Exchange;
import org.sifunity.broker.environment.dto.ExtendedElement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing an alert in the SIF 3.7 Alerts Utility Service.
 * 
 * <p>An alert is an immutable log entry recording an error, warning, or status
 * change. Alerts are created by authenticated environments and scoped by
 * ownership for non-admin queries.</p>
 * 
 * <p>Maps to the {@code alerts} table created by Flyway migration V28.</p>
 * 
 * @see Exchange
 * @see AlertLevel
 * @see ExtendedElementsConverter
 */
@Entity
@Table(name = "alerts")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    /**
     * Unique identifier for the alert.
     * Broker-assigned UUID; any client-provided ID is ignored.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The environment that created this alert.
     * Used for ownership-based access control.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;

    /**
     * The environment this alert is directed at (resolved from messageID context).
     * NULL for broadcast alerts (no messageID) and pre-migration alerts.
     */
    @Column(name = "target_environment_id")
    private UUID targetEnvironmentId;

    /**
     * Application name identifying the reporter of this alert.
     */
    @Column(nullable = false)
    private String reporter;

    /**
     * Application or component that caused the condition being reported.
     */
    @Column
    private String cause;

    /**
     * Classification of the message exchange that generated the alert.
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Exchange exchange;

    /**
     * Severity level of the alert.
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertLevel level;

    /**
     * Human-readable description of the alert condition.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * ID of the originating message, used for message context correlation.
     */
    @Column(name = "message_id")
    private String messageId;

    /**
     * Body content of the message that triggered the alert.
     */
    @Column(columnDefinition = "TEXT")
    private String body;

    /**
     * Error details (e.g., stack trace or error message).
     */
    @Column(columnDefinition = "TEXT")
    private String error;

    /**
     * XPath expression identifying the element that caused the alert.
     */
    @Column
    private String xpath;

    /**
     * SIF error category code.
     */
    @Column
    private Integer category;

    /**
     * SIF error code within the category.
     */
    @Column
    private Integer code;

    /**
     * Internal application-specific error identifier.
     */
    @Column
    private String internal;

    /**
     * Extended elements stored as JSONB.
     * Contains both server-populated and caller-provided name/value pairs.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extended_elements", columnDefinition = "jsonb")
    private List<ExtendedElement> extendedElements;

    /**
     * Timestamp when the alert was persisted (UTC).
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Sets the created timestamp to current time if not already set.
     * Called automatically before persisting new entities.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }

    public UUID getTargetEnvironmentId() { return targetEnvironmentId; }
    public void setTargetEnvironmentId(UUID targetEnvironmentId) { this.targetEnvironmentId = targetEnvironmentId; }

    public String getReporter() { return reporter; }
    public void setReporter(String reporter) { this.reporter = reporter; }

    public String getCause() { return cause; }
    public void setCause(String cause) { this.cause = cause; }

    public Exchange getExchange() { return exchange; }
    public void setExchange(Exchange exchange) { this.exchange = exchange; }

    public AlertLevel getLevel() { return level; }
    public void setLevel(AlertLevel level) { this.level = level; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getXpath() { return xpath; }
    public void setXpath(String xpath) { this.xpath = xpath; }

    public Integer getCategory() { return category; }
    public void setCategory(Integer category) { this.category = category; }

    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }

    public String getInternal() { return internal; }
    public void setInternal(String internal) { this.internal = internal; }

    public List<ExtendedElement> getExtendedElements() { return extendedElements; }
    public void setExtendedElements(List<ExtendedElement> extendedElements) { this.extendedElements = extendedElements; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
