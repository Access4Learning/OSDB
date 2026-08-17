// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing a Named Query template (xQueryTemplate) per SIF 3.7.
 *
 * <p>A Named Query template is registered by a provider and defines a parameterized
 * query that consumers can invoke by token. The broker maintains these templates
 * as a registry and routes consumer invocations to the owning provider.</p>
 *
 * <p>The combination of {@code queryToken} and {@code solutionUuid} must be unique
 * (enforced by database constraint {@code uk_xquery_token_solution}).</p>
 *
 * @see XQueryType
 * @see XQueryStatus
 * @see XQueryParameter
 */
@Entity
@Table(name = "xquery_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XQueryTemplate {

    /**
     * Unique identifier for the template (internal primary key).
     * Generated as UUID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The SIF "id" field — the URL-addressable token used to invoke this query.
     * Must be unique within a solution.
     */
    @Column(name = "query_token", nullable = false)
    private String queryToken;

    /**
     * The type of Named Query: SINGULAR, FORMULA, or EXTENDED.
     */
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private XQueryType type;

    /**
     * The approval status of this template: PENDING, APPROVED, or DISALLOWED.
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private XQueryStatus status;

    /**
     * Optional qualifier — a reason string when status is DISALLOWED.
     */
    @Column(name = "qualifier")
    private String qualifier;

    /**
     * Human-readable description of what this named query does.
     */
    @Column(name = "description")
    private String description;

    /**
     * The query script or expression (provider-defined, opaque to the broker).
     */
    @Column(name = "script", columnDefinition = "TEXT")
    private String script;

    /**
     * Structured list of parameter definitions for this query.
     * Stored as JSONB in PostgreSQL via Hibernate's {@code @JdbcTypeCode(SqlTypes.JSON)}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private List<XQueryParameter> parameters;

    /**
     * URI string indicating the return type of this query (provider-defined).
     */
    @Column(name = "return_type")
    private String returnType;

    /**
     * The provider environment that registered this template.
     * Many templates can belong to one environment.
     * Lazy loading for performance.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;

    /**
     * Solution scope — references the parent solution's UUID primary key.
     * Used for database-enforced referential integrity via FK constraint.
     */
    @Column(name = "solution_uuid", nullable = false)
    private UUID solutionUuid;

    /**
     * Timestamp when the template was created.
     * Set automatically during registration.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Sets the created timestamp to current time.
     * Called automatically before persisting new entities.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Explicit getters and setters for all fields to work around Lombok issues

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getQueryToken() { return queryToken; }
    public void setQueryToken(String queryToken) { this.queryToken = queryToken; }

    public XQueryType getType() { return type; }
    public void setType(XQueryType type) { this.type = type; }

    public XQueryStatus getStatus() { return status; }
    public void setStatus(XQueryStatus status) { this.status = status; }

    public String getQualifier() { return qualifier; }
    public void setQualifier(String qualifier) { this.qualifier = qualifier; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }

    public List<XQueryParameter> getParameters() { return parameters; }
    public void setParameters(List<XQueryParameter> parameters) { this.parameters = parameters; }

    public String getReturnType() { return returnType; }
    public void setReturnType(String returnType) { this.returnType = returnType; }

    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }

    public UUID getSolutionUuid() { return solutionUuid; }
    public void setSolutionUuid(UUID solutionUuid) { this.solutionUuid = solutionUuid; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
