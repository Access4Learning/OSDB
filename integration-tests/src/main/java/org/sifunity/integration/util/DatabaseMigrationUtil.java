// SPDX-License-Identifier: MIT
package org.sifunity.integration.util;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for applying database migrations from all microservices during integration tests.
 * 
 * <p>This utility uses Flyway to apply migrations from multiple microservice modules:
 * <ul>
 *   <li>Environment Service: environments, zones, contexts, ACL entries, provider registry</li>
 *   <li>Queue Service: queues</li>
 *   <li>Subscription Service: subscriptions</li>
 *   <li>Job Service: jobs, job phases</li>
 * </ul>
 * 
 * <p>Migrations are applied in the correct order to handle dependencies between tables.
 * The utility verifies that all expected tables are created after migrations complete.
 * 
 * @since 1.0.0
 */
@Slf4j
public class DatabaseMigrationUtil {
    
    /**
     * Migration locations for each microservice.
     * These paths point to the migration files in each service's resources directory.
     */
    private static final String[] MIGRATION_LOCATIONS = {
        "classpath:db/migration/environment-service"
    };
    
    /**
     * Expected tables that should exist after migrations.
     * Used for verification.
     */
    private static final String[] EXPECTED_TABLES = {
        "environments",
        "zones",
        "contexts",
        "acl_entries",
        "provider_registry",
        "domains",
        "solutions",
        "queues",
        "subscriptions",
        "jobs",
        "job_phases",
        "flyway_schema_history"
    };
    
    /**
     * Applies all database migrations from all microservices.
     * 
     * @param dataSource the data source to apply migrations to
     * @return the migration result
     * @throws RuntimeException if migrations fail
     */
    public static MigrateResult applyMigrations(DataSource dataSource) {
        log.info("Starting database migrations for integration tests");
        
        try {
            // Configure Flyway
            Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(MIGRATION_LOCATIONS)
                .baselineOnMigrate(true)
                .cleanDisabled(false) // Allow clean for test databases
                .load();
            
            // Clean database (remove all objects)
            log.info("Cleaning database before migrations");
            flyway.clean();
            
            // Apply migrations
            log.info("Applying migrations from locations: {}", String.join(", ", MIGRATION_LOCATIONS));
            MigrateResult result = flyway.migrate();
            
            // Log migration results
            logMigrationResults(result);
            
            // Verify migrations
            verifyMigrations(flyway);
            
            log.info("Database migrations completed successfully: {} migrations applied", 
                result.migrationsExecuted);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to apply database migrations", e);
            throw new RuntimeException("Database migration failed", e);
        }
    }
    
    /**
     * Applies migrations using JDBC URL, username, and password.
     * 
     * @param jdbcUrl the JDBC URL
     * @param username the database username
     * @param password the database password
     * @return the migration result
     */
    public static MigrateResult applyMigrations(String jdbcUrl, String username, String password) {
        log.info("Starting database migrations for integration tests");
        log.info("JDBC URL: {}", jdbcUrl);
        
        try {
            // Configure Flyway
            Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations(MIGRATION_LOCATIONS)
                .baselineOnMigrate(true)
                .cleanDisabled(false) // Allow clean for test databases
                .load();
            
            // Clean database (remove all objects)
            log.info("Cleaning database before migrations");
            flyway.clean();
            
            // Apply migrations
            log.info("Applying migrations from locations: {}", String.join(", ", MIGRATION_LOCATIONS));
            MigrateResult result = flyway.migrate();
            
            // Log migration results
            logMigrationResults(result);
            
            // Verify migrations
            verifyMigrations(flyway);
            
            log.info("Database migrations completed successfully: {} migrations applied", 
                result.migrationsExecuted);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to apply database migrations", e);
            throw new RuntimeException("Database migration failed", e);
        }
    }
    
    /**
     * Logs detailed information about applied migrations.
     * 
     * @param result the migration result
     */
    private static void logMigrationResults(MigrateResult result) {
        log.info("Migration summary:");
        log.info("  - Migrations executed: {}", result.migrationsExecuted);
        log.info("  - Target schema version: {}", result.targetSchemaVersion);
        log.info("  - Database: {}", result.database);
        
        if (result.warnings != null && !result.warnings.isEmpty()) {
            log.warn("Migration warnings:");
            result.warnings.forEach(warning -> log.warn("  - {}", warning));
        }
    }
    
    /**
     * Verifies that all expected tables were created by migrations.
     * 
     * @param flyway the Flyway instance
     * @throws RuntimeException if verification fails
     */
    private static void verifyMigrations(Flyway flyway) {
        log.info("Verifying database migrations");
        
        // Get migration info
        MigrationInfo[] migrations = flyway.info().all();
        
        log.info("Applied migrations:");
        for (MigrationInfo migration : migrations) {
            log.info("  - {} ({}): {}", 
                migration.getVersion(), 
                migration.getState(), 
                migration.getDescription());
        }
        
        // Check for pending migrations
        MigrationInfo[] pending = flyway.info().pending();
        if (pending.length > 0) {
            log.warn("Pending migrations found: {}", pending.length);
            for (MigrationInfo migration : pending) {
                log.warn("  - {} ({}): {}", 
                    migration.getVersion(), 
                    migration.getState(), 
                    migration.getDescription());
            }
        }
        
        // Check for failed migrations
        MigrationInfo[] all = flyway.info().all();
        int failedCount = 0;
        for (MigrationInfo migration : all) {
            if (migration.getState().isFailed()) {
                failedCount++;
                log.error("  - {} ({}): {}", 
                    migration.getVersion(), 
                    migration.getState(), 
                    migration.getDescription());
            }
        }
        if (failedCount > 0) {
            log.error("Failed migrations found: {}", failedCount);
            throw new RuntimeException("Database migrations failed");
        }
        
        log.info("Database migration verification completed successfully");
    }
    
    /**
     * Gets the list of expected tables after migrations.
     * 
     * @return array of expected table names
     */
    public static String[] getExpectedTables() {
        return EXPECTED_TABLES.clone();
    }
    
    /**
     * Cleans the database by removing all objects.
     * This should only be used in test environments.
     * 
     * @param dataSource the data source
     */
    public static void cleanDatabase(DataSource dataSource) {
        log.info("Cleaning database");
        
        try {
            Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load();
            
            flyway.clean();
            
            log.info("Database cleaned successfully");
            
        } catch (Exception e) {
            log.error("Failed to clean database", e);
            throw new RuntimeException("Database clean failed", e);
        }
    }
    
    /**
     * Cleans the database using JDBC URL, username, and password.
     * 
     * @param jdbcUrl the JDBC URL
     * @param username the database username
     * @param password the database password
     */
    public static void cleanDatabase(String jdbcUrl, String username, String password) {
        log.info("Cleaning database: {}", jdbcUrl);
        
        try {
            Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .cleanDisabled(false)
                .load();
            
            flyway.clean();
            
            log.info("Database cleaned successfully");
            
        } catch (Exception e) {
            log.error("Failed to clean database", e);
            throw new RuntimeException("Database clean failed", e);
        }
    }
}
