// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Property-based test for UUID FK migration round-trip integrity.
 *
 * <p><strong>Property 1: Data migration preserves parent-child relationships (round-trip integrity)</strong></p>
 *
 * <p>For any entity (solution, zone, context, or environment) that existed before the migration,
 * the UUID foreign key column populated by the migration must point to the same parent entity
 * that the old string column referenced.</p>
 *
 * <p>This test operates at the raw JDBC/Flyway level (no Spring Boot context) because it needs
 * to control migration application order: apply V1–V20 first, seed data into the old schema,
 * then apply V21 and verify the UUID FKs are correct.</p>
 *
 * <p><strong>Validates: Requirements 1.2, 2.2, 3.2, 4.2, 10.5</strong></p>
 */
class UuidFkMigrationIntegrityPropertyTest {

    static PostgreSQLContainer<?> postgres;

    @BeforeContainer
    static void startContainer() {
        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    @AfterContainer
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeTry
    void resetDatabase() {
        // Clean and re-apply V1-V20 migrations before each try
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .target("20")
                .load();
        flyway.clean();
        flyway.migrate();
    }

    /**
     * Property 1: Data migration preserves parent-child relationships (round-trip integrity)
     *
     * <p><strong>Validates: Requirements 1.2, 2.2, 3.2, 4.2, 10.5</strong></p>
     *
     * <p>Seeds a random hierarchy into the pre-V21 schema, applies V21, and verifies
     * that each entity's UUID FK points to the correct parent.</p>
     */
    @Property(tries = 10)
    @Tag("uuid-foreign-key-hierarchy-property-1")
    void migrationPreservesParentChildRelationships(
            @ForAll("randomHierarchies") Hierarchy hierarchy) throws SQLException {

        // 1. Seed data into pre-V21 schema
        Map<String, UUID> domainPks = new LinkedHashMap<>();
        Map<String, UUID> solutionPks = new LinkedHashMap<>();
        Map<String, UUID> zonePks = new LinkedHashMap<>();
        Map<String, UUID> contextPks = new LinkedHashMap<>();
        Map<String, UUID> environmentPks = new LinkedHashMap<>();

        // Track expected parent relationships (child PK -> expected parent PK)
        Map<UUID, UUID> expectedSolutionDomain = new LinkedHashMap<>();
        Map<UUID, UUID> expectedZoneSolution = new LinkedHashMap<>();
        Map<UUID, UUID> expectedContextZone = new LinkedHashMap<>();
        Map<UUID, UUID> expectedEnvSolution = new LinkedHashMap<>();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

            // Insert domains
            for (DomainData domain : hierarchy.domains) {
                UUID pk = UUID.randomUUID();
                domainPks.put(domain.domainId, pk);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO domains (id, domain_id, description, created_at) VALUES (?, ?, ?, NOW())")) {
                    ps.setObject(1, pk);
                    ps.setString(2, domain.domainId);
                    ps.setString(3, "Domain " + domain.domainId);
                    ps.executeUpdate();
                }
            }

            // Insert solutions
            for (SolutionData solution : hierarchy.solutions) {
                UUID pk = UUID.randomUUID();
                solutionPks.put(solution.solutionId, pk);
                UUID domainPk = domainPks.get(solution.domainId);
                expectedSolutionDomain.put(pk, domainPk);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO solutions (id, domain_id, solution_id, description, created_at) VALUES (?, ?, ?, ?, NOW())")) {
                    ps.setObject(1, pk);
                    ps.setString(2, solution.domainId);
                    ps.setString(3, solution.solutionId);
                    ps.setString(4, "Solution " + solution.solutionId);
                    ps.executeUpdate();
                }
            }

            // Insert zones
            for (ZoneData zone : hierarchy.zones) {
                UUID pk = UUID.randomUUID();
                zonePks.put(zone.zoneId, pk);
                UUID solutionPk = solutionPks.get(zone.solutionId);
                expectedZoneSolution.put(pk, solutionPk);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO zones (id, zone_id, solution_id, description, created_at) VALUES (?, ?, ?, ?, NOW())")) {
                    ps.setObject(1, pk);
                    ps.setString(2, zone.zoneId);
                    ps.setString(3, zone.solutionId);
                    ps.setString(4, "Zone " + zone.zoneId);
                    ps.executeUpdate();
                }
            }

            // Insert contexts
            for (ContextData context : hierarchy.contexts) {
                UUID pk = UUID.randomUUID();
                contextPks.put(context.contextId, pk);
                UUID zonePk = zonePks.get(context.zoneId);
                expectedContextZone.put(pk, zonePk);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO contexts (id, zone_id, context_id, description, created_at) VALUES (?, ?, ?, ?, NOW())")) {
                    ps.setObject(1, pk);
                    ps.setString(2, context.zoneId);
                    ps.setString(3, context.contextId);
                    ps.setString(4, "Context " + context.contextId);
                    ps.executeUpdate();
                }
            }

            // Insert environments
            for (EnvironmentData env : hierarchy.environments) {
                UUID pk = UUID.randomUUID();
                environmentPks.put(env.applicationName, pk);
                UUID solutionPk = solutionPks.get(env.solutionId);
                expectedEnvSolution.put(pk, solutionPk);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO environments (id, application_name, session_token, shared_secret, " +
                        "created_at, default_zone_id, type, solution_id) VALUES (?, ?, ?, ?, NOW(), ?, 'BROKERED', ?)")) {
                    ps.setObject(1, pk);
                    ps.setString(2, env.applicationName);
                    ps.setString(3, env.sessionToken);
                    ps.setString(4, env.sharedSecret);
                    ps.setString(5, null); // default_zone_id nullable after V16
                    ps.setString(6, env.solutionId);
                    ps.executeUpdate();
                }
            }
        }

        // 2. Apply V21 migration
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("21")
                .load();
        flyway.migrate();

        // 3. Verify UUID FKs point to correct parents
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

            // Verify solutions.domain_uuid
            for (Map.Entry<UUID, UUID> entry : expectedSolutionDomain.entrySet()) {
                UUID solutionPk = entry.getKey();
                UUID expectedDomainPk = entry.getValue();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT domain_uuid FROM solutions WHERE id = ?")) {
                    ps.setObject(1, solutionPk);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).as("Solution %s should exist after migration", solutionPk).isTrue();
                        UUID actualDomainUuid = rs.getObject("domain_uuid", UUID.class);
                        assertThat(actualDomainUuid)
                                .as("Solution %s domain_uuid should match domain PK", solutionPk)
                                .isEqualTo(expectedDomainPk);
                    }
                }
            }

            // Verify zones.solution_uuid
            for (Map.Entry<UUID, UUID> entry : expectedZoneSolution.entrySet()) {
                UUID zonePk = entry.getKey();
                UUID expectedSolutionPk = entry.getValue();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT solution_uuid FROM zones WHERE id = ?")) {
                    ps.setObject(1, zonePk);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).as("Zone %s should exist after migration", zonePk).isTrue();
                        UUID actualSolutionUuid = rs.getObject("solution_uuid", UUID.class);
                        assertThat(actualSolutionUuid)
                                .as("Zone %s solution_uuid should match solution PK", zonePk)
                                .isEqualTo(expectedSolutionPk);
                    }
                }
            }

            // Verify contexts.zone_uuid
            for (Map.Entry<UUID, UUID> entry : expectedContextZone.entrySet()) {
                UUID contextPk = entry.getKey();
                UUID expectedZonePk = entry.getValue();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT zone_uuid FROM contexts WHERE id = ?")) {
                    ps.setObject(1, contextPk);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).as("Context %s should exist after migration", contextPk).isTrue();
                        UUID actualZoneUuid = rs.getObject("zone_uuid", UUID.class);
                        assertThat(actualZoneUuid)
                                .as("Context %s zone_uuid should match zone PK", contextPk)
                                .isEqualTo(expectedZonePk);
                    }
                }
            }

            // Verify environments.solution_uuid
            for (Map.Entry<UUID, UUID> entry : expectedEnvSolution.entrySet()) {
                UUID envPk = entry.getKey();
                UUID expectedSolutionPk = entry.getValue();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT solution_uuid FROM environments WHERE id = ?")) {
                    ps.setObject(1, envPk);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).as("Environment %s should exist after migration", envPk).isTrue();
                        UUID actualSolutionUuid = rs.getObject("solution_uuid", UUID.class);
                        assertThat(actualSolutionUuid)
                                .as("Environment %s solution_uuid should match solution PK", envPk)
                                .isEqualTo(expectedSolutionPk);
                    }
                }
            }

            // Verify old string columns no longer exist
            assertColumnDoesNotExist(conn, "solutions", "domain_id");
            assertColumnDoesNotExist(conn, "zones", "solution_id");
            assertColumnDoesNotExist(conn, "contexts", "zone_id");
            assertColumnDoesNotExist(conn, "environments", "solution_id");
        }
    }

    private void assertColumnDoesNotExist(Connection conn, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("Column %s.%s should not exist after V21 migration", table, column)
                        .isZero();
            }
        }
    }

    // ========== Arbitraries ==========

    /**
     * Generates random hierarchies with domains→solutions→zones→contexts→environments.
     * Each hierarchy has globally unique string IDs to avoid conflicts with V15 bootstrap data
     * (GLOBAL_DOMAIN, GLOBAL_SOLUTION).
     */
    @Provide
    Arbitrary<Hierarchy> randomHierarchies() {
        return Arbitraries.lazyOf(() -> {
            // Generate a unique prefix per hierarchy to avoid cross-try collisions
            String prefix = UUID.randomUUID().toString().substring(0, 8);

            return Arbitraries.integers().between(1, 3).flatMap(numDomains -> {
                List<DomainData> domains = new ArrayList<>();
                for (int d = 0; d < numDomains; d++) {
                    domains.add(new DomainData(prefix + "_dom_" + d));
                }

                return Arbitraries.integers().between(1, 3).flatMap(numSolutionsPerDomain -> {
                    List<SolutionData> solutions = new ArrayList<>();
                    for (DomainData domain : domains) {
                        for (int s = 0; s < numSolutionsPerDomain; s++) {
                            solutions.add(new SolutionData(
                                    domain.domainId,
                                    prefix + "_sol_" + domain.domainId + "_" + s));
                        }
                    }

                    return Arbitraries.integers().between(1, 2).flatMap(numZonesPerSolution -> {
                        List<ZoneData> zones = new ArrayList<>();
                        for (SolutionData solution : solutions) {
                            for (int z = 0; z < numZonesPerSolution; z++) {
                                zones.add(new ZoneData(
                                        solution.solutionId,
                                        prefix + "_zone_" + solution.solutionId + "_" + z));
                            }
                        }

                        return Arbitraries.integers().between(1, 2).flatMap(numContextsPerZone -> {
                            List<ContextData> contexts = new ArrayList<>();
                            for (ZoneData zone : zones) {
                                for (int c = 0; c < numContextsPerZone; c++) {
                                    contexts.add(new ContextData(
                                            zone.zoneId,
                                            prefix + "_ctx_" + zone.zoneId + "_" + c));
                                }
                            }

                            return Arbitraries.integers().between(1, 2).map(numEnvsPerSolution -> {
                                List<EnvironmentData> environments = new ArrayList<>();
                                for (SolutionData solution : solutions) {
                                    for (int e = 0; e < numEnvsPerSolution; e++) {
                                        String appName = prefix + "_app_" + solution.solutionId + "_" + e;
                                        environments.add(new EnvironmentData(
                                                solution.solutionId,
                                                appName,
                                                UUID.randomUUID().toString(),
                                                UUID.randomUUID().toString()));
                                    }
                                }
                                return new Hierarchy(domains, solutions, zones, contexts, environments);
                            });
                        });
                    });
                });
            });
        });
    }

    // ========== Data Classes ==========

    record Hierarchy(
            List<DomainData> domains,
            List<SolutionData> solutions,
            List<ZoneData> zones,
            List<ContextData> contexts,
            List<EnvironmentData> environments) {
        @Override
        public String toString() {
            return String.format("Hierarchy[%d domains, %d solutions, %d zones, %d contexts, %d environments]",
                    domains.size(), solutions.size(), zones.size(), contexts.size(), environments.size());
        }
    }

    record DomainData(String domainId) {}

    record SolutionData(String domainId, String solutionId) {}

    record ZoneData(String solutionId, String zoneId) {}

    record ContextData(String zoneId, String contextId) {}

    record EnvironmentData(String solutionId, String applicationName, String sessionToken, String sharedSecret) {}
}
