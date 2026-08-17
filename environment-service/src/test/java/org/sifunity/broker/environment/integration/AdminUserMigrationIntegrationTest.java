// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that Flyway migrations V36 and V37 apply cleanly
 * and produce the expected schema structure on a real PostgreSQL database.
 *
 * <p>Uses Testcontainers because H2 does not support PostgreSQL-specific features
 * like gen_random_uuid(), CASCADE delete behavior, and pg_indexes catalog.</p>
 *
 * <p>Verifies:
 * <ul>
 *   <li>admin_users table exists with correct columns</li>
 *   <li>user_environment_access table exists with correct columns</li>
 *   <li>Unique indexes uk_admin_users_username_scope and uk_user_environment_access exist</li>
 *   <li>Foreign key constraints exist with CASCADE delete</li>
 *   <li>Indexes on domain_uuid, solution_uuid, user_id, environment_id exist</li>
 *   <li>Cascade delete works: deleting a domain cascades to admin_users,
 *       deleting admin_user cascades to user_environment_access</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7</strong></p>
 */
@SpringBootTest
@Testcontainers
class AdminUserMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("secrets.backend.type", () -> "plaintext");
    }

    @BeforeAll
    static void beforeAll() {
        assertThat(postgres.isRunning()).isTrue();
    }

    // ========== V36 Migration Tests ==========

    /**
     * Verify V36 migration applied cleanly — Flyway history contains V36.
     */
    @Test
    void migrationV36AppliedSuccessfully() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '36' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify V37 migration applied cleanly — Flyway history contains V37.
     */
    @Test
    void migrationV37AppliedSuccessfully() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '37' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify admin_users table exists with correct columns and nullability.
     * Requirements: 8.1
     */
    @Test
    void adminUsersTableHasCorrectStructure() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'admin_users' ORDER BY ordinal_position");

        assertThat(columns).isNotEmpty();

        Map<String, String> columnNullability = new HashMap<>();
        Map<String, String> columnTypes = new HashMap<>();
        for (Map<String, Object> col : columns) {
            String name = (String) col.get("column_name");
            columnNullability.put(name, (String) col.get("is_nullable"));
            columnTypes.put(name, (String) col.get("data_type"));
        }

        // Verify all required columns exist
        assertThat(columnNullability).containsKey("id");
        assertThat(columnNullability).containsKey("username");
        assertThat(columnNullability).containsKey("password_hash");
        assertThat(columnNullability).containsKey("domain_uuid");
        assertThat(columnNullability).containsKey("solution_uuid");
        assertThat(columnNullability).containsKey("created_at");
        assertThat(columnNullability).containsKey("updated_at");

        // Verify NOT NULL constraints
        assertThat(columnNullability.get("id")).isEqualTo("NO");
        assertThat(columnNullability.get("username")).isEqualTo("NO");
        assertThat(columnNullability.get("password_hash")).isEqualTo("NO");
        assertThat(columnNullability.get("domain_uuid")).isEqualTo("NO");
        assertThat(columnNullability.get("solution_uuid")).isEqualTo("NO");
        assertThat(columnNullability.get("created_at")).isEqualTo("NO");

        // updated_at is nullable
        assertThat(columnNullability.get("updated_at")).isEqualTo("YES");

        // Verify data types
        assertThat(columnTypes.get("id")).isEqualTo("uuid");
        assertThat(columnTypes.get("username")).isEqualTo("character varying");
        assertThat(columnTypes.get("password_hash")).isEqualTo("character varying");
        assertThat(columnTypes.get("domain_uuid")).isEqualTo("uuid");
        assertThat(columnTypes.get("solution_uuid")).isEqualTo("uuid");
    }

    /**
     * Verify user_environment_access table exists with correct columns and nullability.
     * Requirements: 8.2
     */
    @Test
    void userEnvironmentAccessTableHasCorrectStructure() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'user_environment_access' ORDER BY ordinal_position");

        assertThat(columns).isNotEmpty();

        Map<String, String> columnNullability = new HashMap<>();
        Map<String, String> columnTypes = new HashMap<>();
        for (Map<String, Object> col : columns) {
            String name = (String) col.get("column_name");
            columnNullability.put(name, (String) col.get("is_nullable"));
            columnTypes.put(name, (String) col.get("data_type"));
        }

        // Verify all required columns exist
        assertThat(columnNullability).containsKey("id");
        assertThat(columnNullability).containsKey("user_id");
        assertThat(columnNullability).containsKey("environment_id");
        assertThat(columnNullability).containsKey("granted_at");

        // Verify NOT NULL constraints
        assertThat(columnNullability.get("id")).isEqualTo("NO");
        assertThat(columnNullability.get("user_id")).isEqualTo("NO");
        assertThat(columnNullability.get("environment_id")).isEqualTo("NO");
        assertThat(columnNullability.get("granted_at")).isEqualTo("NO");

        // Verify data types
        assertThat(columnTypes.get("id")).isEqualTo("uuid");
        assertThat(columnTypes.get("user_id")).isEqualTo("uuid");
        assertThat(columnTypes.get("environment_id")).isEqualTo("uuid");
    }

    /**
     * Verify unique index uk_admin_users_username_scope exists on admin_users.
     * Requirements: 8.3
     */
    @Test
    void adminUsersHasUniqueIndexOnUsernameScope() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes " +
                "WHERE tablename = 'admin_users' AND indexname = 'uk_admin_users_username_scope'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify unique index uk_user_environment_access exists on user_environment_access.
     * Requirements: 8.4
     */
    @Test
    void userEnvironmentAccessHasUniqueIndex() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes " +
                "WHERE tablename = 'user_environment_access' AND indexname = 'uk_user_environment_access'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify index on admin_users(domain_uuid) exists.
     * Requirements: 8.7
     */
    @Test
    void adminUsersHasIndexOnDomainUuid() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes " +
                "WHERE tablename = 'admin_users' AND indexname = 'idx_admin_users_domain_uuid'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify index on admin_users(solution_uuid) exists.
     * Requirements: 8.7
     */
    @Test
    void adminUsersHasIndexOnSolutionUuid() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes " +
                "WHERE tablename = 'admin_users' AND indexname = 'idx_admin_users_solution_uuid'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify index on user_environment_access(user_id) exists.
     * Requirements: 8.7
     */
    @Test
    void userEnvironmentAccessHasIndexOnUserId() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes " +
                "WHERE tablename = 'user_environment_access' AND indexname = 'idx_user_env_access_user_id'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify index on user_environment_access(environment_id) exists.
     * Requirements: 8.7
     */
    @Test
    void userEnvironmentAccessHasIndexOnEnvironmentId() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes " +
                "WHERE tablename = 'user_environment_access' AND indexname = 'idx_user_env_access_environment_id'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    // ========== Foreign Key Constraint Tests ==========

    /**
     * Verify admin_users has FK to domains(id) with CASCADE delete.
     * Requirements: 8.5
     */
    @Test
    void adminUsersHasForeignKeyToDomains() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints tc " +
                "JOIN information_schema.referential_constraints rc ON tc.constraint_name = rc.constraint_name " +
                "WHERE tc.table_name = 'admin_users' AND tc.constraint_type = 'FOREIGN KEY' " +
                "AND tc.constraint_name = 'fk_admin_users_domain'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify admin_users FK to domains has CASCADE delete rule.
     * Requirements: 8.5
     */
    @Test
    void adminUsersDomainFkHasCascadeDelete() {
        String deleteRule = jdbcTemplate.queryForObject(
                "SELECT rc.delete_rule FROM information_schema.referential_constraints rc " +
                "WHERE rc.constraint_name = 'fk_admin_users_domain'",
                String.class);
        assertThat(deleteRule).isEqualTo("CASCADE");
    }

    /**
     * Verify admin_users has FK to solutions(id) with CASCADE delete.
     * Requirements: 8.5
     */
    @Test
    void adminUsersHasForeignKeyToSolutions() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints tc " +
                "JOIN information_schema.referential_constraints rc ON tc.constraint_name = rc.constraint_name " +
                "WHERE tc.table_name = 'admin_users' AND tc.constraint_type = 'FOREIGN KEY' " +
                "AND tc.constraint_name = 'fk_admin_users_solution'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify admin_users FK to solutions has CASCADE delete rule.
     * Requirements: 8.5
     */
    @Test
    void adminUsersSolutionFkHasCascadeDelete() {
        String deleteRule = jdbcTemplate.queryForObject(
                "SELECT rc.delete_rule FROM information_schema.referential_constraints rc " +
                "WHERE rc.constraint_name = 'fk_admin_users_solution'",
                String.class);
        assertThat(deleteRule).isEqualTo("CASCADE");
    }

    /**
     * Verify user_environment_access has FK to admin_users(id) with CASCADE delete.
     * Requirements: 8.5
     */
    @Test
    void userEnvironmentAccessHasForeignKeyToAdminUsers() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints tc " +
                "JOIN information_schema.referential_constraints rc ON tc.constraint_name = rc.constraint_name " +
                "WHERE tc.table_name = 'user_environment_access' AND tc.constraint_type = 'FOREIGN KEY' " +
                "AND tc.constraint_name = 'fk_user_env_access_user'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify user_environment_access FK to admin_users has CASCADE delete rule.
     * Requirements: 8.5
     */
    @Test
    void userEnvironmentAccessUserFkHasCascadeDelete() {
        String deleteRule = jdbcTemplate.queryForObject(
                "SELECT rc.delete_rule FROM information_schema.referential_constraints rc " +
                "WHERE rc.constraint_name = 'fk_user_env_access_user'",
                String.class);
        assertThat(deleteRule).isEqualTo("CASCADE");
    }

    /**
     * Verify user_environment_access has FK to environments(id) with CASCADE delete.
     * Requirements: 8.5
     */
    @Test
    void userEnvironmentAccessHasForeignKeyToEnvironments() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints tc " +
                "JOIN information_schema.referential_constraints rc ON tc.constraint_name = rc.constraint_name " +
                "WHERE tc.table_name = 'user_environment_access' AND tc.constraint_type = 'FOREIGN KEY' " +
                "AND tc.constraint_name = 'fk_user_env_access_environment'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify user_environment_access FK to environments has CASCADE delete rule.
     * Requirements: 8.5
     */
    @Test
    void userEnvironmentAccessEnvironmentFkHasCascadeDelete() {
        String deleteRule = jdbcTemplate.queryForObject(
                "SELECT rc.delete_rule FROM information_schema.referential_constraints rc " +
                "WHERE rc.constraint_name = 'fk_user_env_access_environment'",
                String.class);
        assertThat(deleteRule).isEqualTo("CASCADE");
    }

    // ========== Cascade Delete Behavior Tests ==========

    /**
     * Verify cascade delete: deleting a domain cascades to admin_users.
     * Requirements: 8.5
     */
    @Test
    void deletingDomainCascadesToAdminUsers() {
        // Create a domain directly via SQL
        UUID domainId = UUID.randomUUID();
        String domainIdStr = "cascade-test-domain-" + System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO domains (id, domain_id, description) VALUES (?, ?, ?)",
                domainId, domainIdStr, "Test domain for cascade");

        // Create a solution in that domain
        UUID solutionId = UUID.randomUUID();
        String solutionIdStr = "cascade-test-solution-" + System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO solutions (id, solution_id, domain_uuid, description) VALUES (?, ?, ?, ?)",
                solutionId, solutionIdStr, domainId, "Test solution for cascade");

        // Create an admin user in that domain+solution
        UUID adminUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO admin_users (id, username, password_hash, domain_uuid, solution_uuid) " +
                "VALUES (?, ?, ?, ?, ?)",
                adminUserId, "cascade-test-user", "$2a$10$dummyhashvalue", domainId, solutionId);

        // Verify admin user exists
        Integer countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_users WHERE id = ?",
                Integer.class, adminUserId);
        assertThat(countBefore).isEqualTo(1);

        // Delete the domain
        jdbcTemplate.update("DELETE FROM domains WHERE id = ?", domainId);

        // Verify admin user was cascade-deleted
        Integer countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_users WHERE id = ?",
                Integer.class, adminUserId);
        assertThat(countAfter).isEqualTo(0);
    }

    /**
     * Verify cascade delete: deleting an admin_user cascades to user_environment_access.
     * Requirements: 8.5
     */
    @Test
    void deletingAdminUserCascadesToUserEnvironmentAccess() {
        // Create prerequisite domain and solution
        UUID domainId = UUID.randomUUID();
        String domainIdStr = "cascade-user-domain-" + System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO domains (id, domain_id, description) VALUES (?, ?, ?)",
                domainId, domainIdStr, "Test domain");

        UUID solutionId = UUID.randomUUID();
        String solutionIdStr = "cascade-user-solution-" + System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO solutions (id, solution_id, domain_uuid, description) VALUES (?, ?, ?, ?)",
                solutionId, solutionIdStr, domainId, "Test solution");

        // Create an environment (need to insert into environments table)
        UUID environmentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO environments (id, application_name, session_token, shared_secret, created_at, type, solution_uuid) " +
                "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)",
                environmentId, "test-app-" + System.currentTimeMillis(), UUID.randomUUID().toString(),
                "dummysecret", "BROKERED", solutionId);

        // Create an admin user
        UUID adminUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO admin_users (id, username, password_hash, domain_uuid, solution_uuid) " +
                "VALUES (?, ?, ?, ?, ?)",
                adminUserId, "cascade-access-user", "$2a$10$dummyhashvalue", domainId, solutionId);

        // Create a user_environment_access record
        UUID accessId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO user_environment_access (id, user_id, environment_id) VALUES (?, ?, ?)",
                accessId, adminUserId, environmentId);

        // Verify access record exists
        Integer countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_environment_access WHERE id = ?",
                Integer.class, accessId);
        assertThat(countBefore).isEqualTo(1);

        // Delete the admin user
        jdbcTemplate.update("DELETE FROM admin_users WHERE id = ?", adminUserId);

        // Verify access record was cascade-deleted
        Integer countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_environment_access WHERE id = ?",
                Integer.class, accessId);
        assertThat(countAfter).isEqualTo(0);
    }

    /**
     * Verify cascade delete: deleting an environment cascades to user_environment_access.
     * Requirements: 8.5
     */
    @Test
    void deletingEnvironmentCascadesToUserEnvironmentAccess() {
        // Create prerequisite domain and solution
        UUID domainId = UUID.randomUUID();
        String domainIdStr = "cascade-env-domain-" + System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO domains (id, domain_id, description) VALUES (?, ?, ?)",
                domainId, domainIdStr, "Test domain");

        UUID solutionId = UUID.randomUUID();
        String solutionIdStr = "cascade-env-solution-" + System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO solutions (id, solution_id, domain_uuid, description) VALUES (?, ?, ?, ?)",
                solutionId, solutionIdStr, domainId, "Test solution");

        // Create an environment
        UUID environmentId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO environments (id, application_name, session_token, shared_secret, created_at, type, solution_uuid) " +
                "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)",
                environmentId, "test-app-env-cascade-" + System.currentTimeMillis(), UUID.randomUUID().toString(),
                "dummysecret", "BROKERED", solutionId);

        // Create an admin user
        UUID adminUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO admin_users (id, username, password_hash, domain_uuid, solution_uuid) " +
                "VALUES (?, ?, ?, ?, ?)",
                adminUserId, "cascade-env-user", "$2a$10$dummyhashvalue", domainId, solutionId);

        // Create a user_environment_access record
        UUID accessId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO user_environment_access (id, user_id, environment_id) VALUES (?, ?, ?)",
                accessId, adminUserId, environmentId);

        // Verify access record exists
        Integer countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_environment_access WHERE id = ?",
                Integer.class, accessId);
        assertThat(countBefore).isEqualTo(1);

        // Delete the environment
        jdbcTemplate.update("DELETE FROM environments WHERE id = ?", environmentId);

        // Verify access record was cascade-deleted
        Integer countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_environment_access WHERE id = ?",
                Integer.class, accessId);
        assertThat(countAfter).isEqualTo(0);
    }
}
