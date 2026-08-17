// SPDX-License-Identifier: MIT
package org.sifunity.integration.container;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * PostgreSQL test container configuration for integration tests.
 * 
 * <p>This container provides a PostgreSQL 15 database instance for all microservices
 * during integration testing. The container is configured with:
 * <ul>
 *   <li>Database name: sif_broker_test</li>
 *   <li>Username: test_user</li>
 *   <li>Password: test_password</li>
 *   <li>Health check: Wait for database to accept connections</li>
 * </ul>
 * 
 * <p>The container is started once before all tests and stopped after all tests complete.
 * Database migrations are applied after the container starts.
 * 
 * @since 1.0.0
 */
@Slf4j
public class PostgresTestContainer extends PostgreSQLContainer<PostgresTestContainer> {
    
    private static final String IMAGE_VERSION = "postgres:15-alpine";
    private static final String DATABASE_NAME = "sif_broker_test";
    private static final String USERNAME = "test_user";
    private static final String PASSWORD = "test_password";
    
    private static PostgresTestContainer container;
    
    /**
     * Creates a new PostgreSQL test container with default configuration.
     */
    private PostgresTestContainer() {
        super(DockerImageName.parse(IMAGE_VERSION));
        
        // Configure database
        withDatabaseName(DATABASE_NAME);
        withUsername(USERNAME);
        withPassword(PASSWORD);
        
        // Configure health check
        waitingFor(Wait.forListeningPort()
            .withStartupTimeout(Duration.ofSeconds(60)));
        
        // Enable reuse for faster test execution (optional)
        withReuse(false); // Set to true for development to reuse containers
        
        log.info("PostgreSQL test container configured: database={}, user={}", 
            DATABASE_NAME, USERNAME);
    }
    
    /**
     * Gets the singleton instance of the PostgreSQL test container.
     * 
     * @return the PostgreSQL test container instance
     */
    public static PostgresTestContainer getInstance() {
        if (container == null) {
            container = new PostgresTestContainer();
        }
        return container;
    }
    
    /**
     * Starts the PostgreSQL container if not already started.
     */
    @Override
    public void start() {
        super.start();
        log.info("PostgreSQL test container started: jdbcUrl={}", getJdbcUrl());
        log.info("PostgreSQL connection details: host={}, port={}, database={}", 
            getHost(), getMappedPort(POSTGRESQL_PORT), getDatabaseName());
    }
    
    /**
     * Stops the PostgreSQL container.
     */
    @Override
    public void stop() {
        log.info("Stopping PostgreSQL test container");
        super.stop();
    }
    
    /**
     * Gets the JDBC URL for connecting to the test database.
     * 
     * @return the JDBC URL
     */
    public String getJdbcUrl() {
        return super.getJdbcUrl();
    }
    
    /**
     * Gets the database username.
     * 
     * @return the username
     */
    public String getUsername() {
        return super.getUsername();
    }
    
    /**
     * Gets the database password.
     * 
     * @return the password
     */
    public String getPassword() {
        return super.getPassword();
    }
    
    /**
     * Gets the database name.
     * 
     * @return the database name
     */
    public String getDatabaseName() {
        return super.getDatabaseName();
    }
    
    /**
     * Gets the mapped port for PostgreSQL.
     * 
     * @return the mapped port
     */
    public Integer getPort() {
        return getMappedPort(POSTGRESQL_PORT);
    }
}
