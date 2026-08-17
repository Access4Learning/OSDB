// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 2: Consistent Testcontainers Configuration Across Database Modules.
 *
 * <p>For any database-backed module (queue-service, subscription-service, job-service,
 * environment-service), the {@code application-test.yml} shall satisfy all of:</p>
 * <ul>
 *   <li>{@code spring.datasource.url} matches the pattern {@code jdbc:tc:postgresql:.*:///.*}</li>
 *   <li>{@code spring.datasource.driver-class-name} equals {@code org.testcontainers.jdbc.ContainerDatabaseDriver}</li>
 *   <li>{@code spring.jpa.hibernate.ddl-auto} equals {@code validate}</li>
 *   <li>{@code spring.jpa.properties.hibernate.dialect} equals {@code org.hibernate.dialect.PostgreSQLDialect}</li>
 *   <li>{@code spring.flyway.enabled} equals {@code true}</li>
 *   <li>{@code spring.flyway.locations} contains {@code classpath:db/migration}</li>
 * </ul>
 *
 * <p>This property is verified by parsing each module's {@code application-test.yml} as YAML
 * and checking each key-value pair.</p>
 *
 * <p><strong>Validates: Requirements 4.4, 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3</strong></p>
 */
class ConsistentTestcontainersConfigurationPropertyTest {

    private static final String TESTCONTAINERS_URL_PATTERN = "jdbc:tc:postgresql:.*:///.*";
    private static final String EXPECTED_DRIVER = "org.testcontainers.jdbc.ContainerDatabaseDriver";
    private static final String EXPECTED_DDL_AUTO = "validate";
    private static final String EXPECTED_DIALECT = "org.hibernate.dialect.PostgreSQLDialect";
    private static final String EXPECTED_FLYWAY_LOCATION = "classpath:db/migration";

    /** Resolve the project root by navigating up from the current module directory. */
    private static final Path PROJECT_ROOT = resolveProjectRoot();

    private static Path resolveProjectRoot() {
        Path moduleDir = Paths.get(System.getProperty("user.dir"));
        Path candidate = moduleDir.getParent();
        if (candidate != null && candidate.resolve("pom.xml").toFile().exists()) {
            return candidate;
        }
        if (moduleDir.resolve("queue-service").resolve("pom.xml").toFile().exists()) {
            return moduleDir;
        }
        throw new IllegalStateException(
                "Cannot resolve project root from user.dir=" + moduleDir);
    }

    @Provide
    Arbitrary<String> databaseBackedModules() {
        return Arbitraries.of(
                "environment-service", "queue-service", "subscription-service", "job-service");
    }

    /**
     * For any database-backed module, the application-test.yml must contain
     * consistent Testcontainers configuration matching the reference pattern:
     * <ul>
     *   <li>Testcontainers JDBC URL for PostgreSQL</li>
     *   <li>Testcontainers JDBC driver</li>
     *   <li>Hibernate ddl-auto set to validate</li>
     *   <li>PostgreSQL dialect</li>
     *   <li>Flyway enabled with correct migration location</li>
     * </ul>
     */
    @Property(tries = 10)
    @Tag("Feature: h2-removal-testcontainers-standardization, Property 2: Consistent Testcontainers Configuration Across Database Modules")
    void applicationTestYmlHasConsistentTestcontainersConfiguration(
            @ForAll("databaseBackedModules") String moduleName) throws IOException {

        Path yamlPath = PROJECT_ROOT
                .resolve(moduleName)
                .resolve("src/test/resources/application-test.yml");
        assertThat(yamlPath.toFile())
                .as("application-test.yml must exist for module %s", moduleName)
                .exists();

        Map<String, Object> root = parseYaml(yamlPath);
        Map<String, Object> spring = getNestedMap(root, "spring", moduleName);

        // Verify spring.datasource.url matches Testcontainers PostgreSQL pattern
        Map<String, Object> datasource = getNestedMap(spring, "datasource", moduleName);
        String url = getString(datasource, "url", moduleName + " spring.datasource.url");
        assertThat(url)
                .as("spring.datasource.url in %s must match Testcontainers PostgreSQL pattern", moduleName)
                .matches(TESTCONTAINERS_URL_PATTERN);

        // Verify spring.datasource.driver-class-name
        String driver = getString(datasource, "driver-class-name", moduleName + " spring.datasource.driver-class-name");
        assertThat(driver)
                .as("spring.datasource.driver-class-name in %s", moduleName)
                .isEqualTo(EXPECTED_DRIVER);

        // Verify spring.jpa.hibernate.ddl-auto
        Map<String, Object> jpa = getNestedMap(spring, "jpa", moduleName);
        Map<String, Object> hibernate = getNestedMap(jpa, "hibernate", moduleName);
        String ddlAuto = getString(hibernate, "ddl-auto", moduleName + " spring.jpa.hibernate.ddl-auto");
        assertThat(ddlAuto)
                .as("spring.jpa.hibernate.ddl-auto in %s", moduleName)
                .isEqualTo(EXPECTED_DDL_AUTO);

        // Verify spring.jpa.properties.hibernate.dialect
        Map<String, Object> jpaProperties = getNestedMap(jpa, "properties", moduleName);
        Map<String, Object> hibernateProps = getNestedMap(jpaProperties, "hibernate", moduleName);
        String dialect = getString(hibernateProps, "dialect", moduleName + " spring.jpa.properties.hibernate.dialect");
        assertThat(dialect)
                .as("spring.jpa.properties.hibernate.dialect in %s", moduleName)
                .isEqualTo(EXPECTED_DIALECT);

        // Verify spring.flyway.enabled
        Map<String, Object> flyway = getNestedMap(spring, "flyway", moduleName);
        Object flywayEnabled = flyway.get("enabled");
        assertThat(flywayEnabled)
                .as("spring.flyway.enabled in %s must be present", moduleName)
                .isNotNull();
        assertThat(Boolean.TRUE.equals(flywayEnabled))
                .as("spring.flyway.enabled in %s must be true", moduleName)
                .isTrue();

        // Verify spring.flyway.locations contains classpath:db/migration
        String locations = getString(flyway, "locations", moduleName + " spring.flyway.locations");
        assertThat(locations)
                .as("spring.flyway.locations in %s must contain classpath:db/migration", moduleName)
                .contains(EXPECTED_FLYWAY_LOCATION);
    }

    // ========== Helpers ==========

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYaml(Path yamlPath) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(yamlPath)) {
            Object loaded = yaml.load(in);
            assertThat(loaded)
                    .as("YAML file %s must parse to a Map", yamlPath)
                    .isInstanceOf(Map.class);
            return (Map<String, Object>) loaded;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedMap(Map<String, Object> parent, String key, String context) {
        Object value = parent.get(key);
        assertThat(value)
                .as("Key '%s' must exist in %s", key, context)
                .isNotNull()
                .isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private String getString(Map<String, Object> map, String key, String context) {
        Object value = map.get(key);
        assertThat(value)
                .as("Key '%s' must exist in %s", key, context)
                .isNotNull();
        return value.toString();
    }
}
