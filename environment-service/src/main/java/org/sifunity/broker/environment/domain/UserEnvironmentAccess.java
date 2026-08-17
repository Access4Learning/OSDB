// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a user-to-environment access association.
 *
 * <p>Links an {@link AdminUser} to an {@link Environment} they are authorized
 * to access. The combination of (user_id, environment_id) must be unique,
 * enforced by a database constraint.</p>
 *
 * <p>Cascade delete is handled at the database level — deleting either the
 * admin user or the environment removes the association row.</p>
 *
 * @see AdminUser
 * @see Environment
 */
@Entity
@Table(name = "user_environment_access",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_user_environment_access",
           columnNames = {"user_id", "environment_id"}
       ))
public class UserEnvironmentAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AdminUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) {
            grantedAt = Instant.now();
        }
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public AdminUser getUser() { return user; }
    public void setUser(AdminUser user) { this.user = user; }

    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }

    public Instant getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }
}
