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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that Flyway migration V33 applies cleanly
 * and produces the expected schema structure on a real PostgreSQL database.
 *
 * <p>Uses Testcontainers because H2 does not support PostgreSQL-specific features
 * like CHECK constraints and partial unique indexes.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5</strong></p>
 */
@SpringBootTest
@Testcontainers
class MigrationV33IntegrationTest {

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

    /**
     * Verify migration applied cleanly — Flyway history contains V33.
     */
    @Test
    void migrationV33AppliedSuccessfully() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '33' AND success = true",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify context_services table exists with correct columns and types.
     * Requirements: 1.1
     */
    @Test
    void contextServicesTableHasCorrectStructure() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable " +
                "FROM information_schema.columns " +
                "WHERE table_name = 'context_services' ORDER BY ordinal_position");

        assertThat(columns).isNotEmpty();

        Map<String, String> columnMap = new java.util.HashMap<>();
        for (Map<String, Object> col : columns) {
            columnMap.put((String) col.get("column_name"), (String) col.get("is_nullable"));
        }

        // Verify required columns exist and are NOT NULL
        assertThat(columnMap).containsKey("id");
        assertThat(columnMap).containsKey("context_uuid");
        assertThat(columnMap).containsKey("service_name");
        assertThat(columnMap).containsKey("service_type");
        assertThat(columnMap).containsKey("created_at");

        assertThat(columnMap.get("context_uuid")).isEqualTo("NO");
        assertThat(columnMap.get("service_name")).isEqualTo("NO");
        assertThat(columnMap.get("service_type")).isEqualTo("NO");
        assertThat(columnMap.get("created_at")).isEqualTo("NO");
    }

    /**
     * Verify context_services has unique constraint on (context_uuid, service_name).
     * Requirements: 1.1
     */
    @Test
    void contextServicesHasUniqueConstraint() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                "WHERE table_name = 'context_services' AND constraint_type = 'UNIQUE' " +
                "AND constraint_name = 'uk_context_service'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify context_services has CHECK constraint on service_type.
     * Requirements: 1.1
     */
    @Test
    void contextServicesHasCheckConstraint() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                "WHERE table_name = 'context_services' AND constraint_type = 'CHECK' " +
                "AND constraint_name = 'chk_context_service_type'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify acl_entries has context_service_uuid column and does NOT have
     * service_name or context_uuid columns.
     * Requirements: 2.1, 2.2, 2.3, 2.4
     */
    @Test
    void aclEntriesHasContextServiceUuidAndNoOldColumns() {
        List<String> aclColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = 'acl_entries'",
                String.class);

        assertThat(aclColumns).contains("context_service_uuid");
        assertThat(aclColumns).doesNotContain("service_name");
        assertThat(aclColumns).doesNotContain("context_uuid");
    }

    /**
     * Verify acl_entries has FK to context_services.
     * Requirements: 2.4
     */
    @Test
    void aclEntriesHasForeignKeyToContextServices() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints tc " +
                "JOIN information_schema.referential_constraints rc ON tc.constraint_name = rc.constraint_name " +
                "WHERE tc.table_name = 'acl_entries' AND tc.constraint_type = 'FOREIGN KEY' " +
                "AND tc.constraint_name = 'fk_acl_context_service'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify acl_entries has partial unique index for single-provider constraint.
     * Requirements: 2.4
     */
    @Test
    void aclEntriesHasSingleProviderPartialIndex() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes " +
                "WHERE tablename = 'acl_entries' AND indexname = 'uk_acl_single_provider'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify provider_registry has context_service_uuid column and does NOT have
     * service_name, zone_id, or context_id columns.
     * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
     */
    @Test
    void providerRegistryHasContextServiceUuidAndNoOldColumns() {
        List<String> providerColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = 'provider_registry'",
                String.class);

        assertThat(providerColumns).contains("context_service_uuid");
        assertThat(providerColumns).doesNotContain("service_name");
        assertThat(providerColumns).doesNotContain("zone_id");
        assertThat(providerColumns).doesNotContain("context_id");
    }

    /**
     * Verify provider_registry has FK to context_services.
     * Requirements: 3.5
     */
    @Test
    void providerRegistryHasForeignKeyToContextServices() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints tc " +
                "JOIN information_schema.referential_constraints rc ON tc.constraint_name = rc.constraint_name " +
                "WHERE tc.table_name = 'provider_registry' AND tc.constraint_type = 'FOREIGN KEY' " +
                "AND tc.constraint_name = 'fk_provider_context_service'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    /**
     * Verify provider_registry has unique constraint on (environment_id, context_service_uuid).
     * Requirements: 3.5
     */
    @Test
    void providerRegistryHasUniqueConstraint() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints " +
                "WHERE table_name = 'provider_registry' AND constraint_type = 'UNIQUE' " +
                "AND constraint_name = 'uk_provider_context_service'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
