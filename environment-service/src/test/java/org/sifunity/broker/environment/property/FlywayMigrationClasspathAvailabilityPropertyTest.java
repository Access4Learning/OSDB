// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Property 3: Flyway Migration Classpath Availability.
 *
 * <p>For any database-backed module that does not own its own migration scripts
 * (queue-service, subscription-service, job-service), the module's Maven build
 * configuration shall include a {@code maven-resources-plugin} execution that
 * copies Flyway migration files from {@code environment-service/src/main/resources/db/migration}
 * to the module's test classpath at {@code db/migration/}.</p>
 *
 * <p>This property is verified by parsing each module's {@code pom.xml} and checking
 * for the {@code copy-flyway-migrations} execution in the {@code maven-resources-plugin}
 * configuration.</p>
 *
 * <p><strong>Validates: Requirements 7.1, 7.2, 7.3, 7.4</strong></p>
 */
class FlywayMigrationClasspathAvailabilityPropertyTest {

    private static final String MAVEN_RESOURCES_PLUGIN_ARTIFACT_ID = "maven-resources-plugin";
    private static final String COPY_FLYWAY_MIGRATIONS_ID = "copy-flyway-migrations";
    private static final String EXPECTED_PHASE = "generate-test-resources";
    private static final String EXPECTED_GOAL = "copy-resources";
    private static final String EXPECTED_SOURCE_SUFFIX = "environment-service/src/main/resources/db/migration";
    private static final String EXPECTED_OUTPUT_SUFFIX = "test-classes/db/migration";

    /** Resolve the project root by navigating up from the current module directory. */
    private static final Path PROJECT_ROOT = resolveProjectRoot();

    private static Path resolveProjectRoot() {
        // When tests run, user.dir is the module directory (e.g., environment-service/).
        // The project root is one level up.
        Path moduleDir = Paths.get(System.getProperty("user.dir"));
        Path candidate = moduleDir.getParent();
        if (candidate != null && candidate.resolve("pom.xml").toFile().exists()) {
            return candidate;
        }
        // Fallback: if already at root (e.g., running from project root)
        if (moduleDir.resolve("queue-service").resolve("pom.xml").toFile().exists()) {
            return moduleDir;
        }
        throw new IllegalStateException(
                "Cannot resolve project root from user.dir=" + moduleDir);
    }

    @Provide
    Arbitrary<String> nonEnvironmentServiceDatabaseModules() {
        return Arbitraries.of("queue-service", "subscription-service", "job-service");
    }

    /**
     * For any non-environment-service database module, the pom.xml must contain
     * a maven-resources-plugin execution with id "copy-flyway-migrations" that:
     * <ul>
     *   <li>Runs in the generate-test-resources phase</li>
     *   <li>Uses the copy-resources goal</li>
     *   <li>Copies from the environment-service migration directory</li>
     *   <li>Outputs to test-classes/db/migration</li>
     * </ul>
     */
    @Property(tries = 10)
    @Tag("Feature: h2-removal-testcontainers-standardization, Property 3: Flyway Migration Classpath Availability")
    void flywayMigrationCopyExecutionExistsInPom(
            @ForAll("nonEnvironmentServiceDatabaseModules") String moduleName) throws Exception {

        Path pomPath = PROJECT_ROOT.resolve(moduleName).resolve("pom.xml");
        assertThat(pomPath.toFile())
                .as("pom.xml must exist for module %s", moduleName)
                .exists();

        Document doc = parsePom(pomPath);

        // Find the maven-resources-plugin with copy-flyway-migrations execution
        Element execution = findCopyFlywayMigrationsExecution(doc, moduleName);
        assertThat(execution)
                .as("Module %s must have a '%s' execution in %s",
                        moduleName, COPY_FLYWAY_MIGRATIONS_ID, MAVEN_RESOURCES_PLUGIN_ARTIFACT_ID)
                .isNotNull();

        // Verify phase
        String phase = getChildText(execution, "phase");
        assertThat(phase)
                .as("Execution phase in %s must be '%s'", moduleName, EXPECTED_PHASE)
                .isEqualTo(EXPECTED_PHASE);

        // Verify goal
        NodeList goals = execution.getElementsByTagName("goal");
        assertThat(goals.getLength())
                .as("Execution in %s must have at least one goal", moduleName)
                .isGreaterThan(0);
        boolean hasExpectedGoal = false;
        for (int i = 0; i < goals.getLength(); i++) {
            if (EXPECTED_GOAL.equals(goals.item(i).getTextContent().trim())) {
                hasExpectedGoal = true;
                break;
            }
        }
        assertThat(hasExpectedGoal)
                .as("Execution in %s must include goal '%s'", moduleName, EXPECTED_GOAL)
                .isTrue();

        // Verify configuration: outputDirectory contains test-classes/db/migration
        NodeList outputDirs = execution.getElementsByTagName("outputDirectory");
        assertThat(outputDirs.getLength())
                .as("Execution in %s must specify an outputDirectory", moduleName)
                .isGreaterThan(0);
        String outputDir = outputDirs.item(0).getTextContent().trim();
        assertThat(outputDir)
                .as("outputDirectory in %s must target test-classes/db/migration", moduleName)
                .contains(EXPECTED_OUTPUT_SUFFIX);

        // Verify configuration: resource directory references environment-service migrations
        NodeList directories = execution.getElementsByTagName("directory");
        assertThat(directories.getLength())
                .as("Execution in %s must specify a resource directory", moduleName)
                .isGreaterThan(0);
        String directory = directories.item(0).getTextContent().trim();
        assertThat(directory)
                .as("Resource directory in %s must reference environment-service migrations", moduleName)
                .contains(EXPECTED_SOURCE_SUFFIX);
    }

    // ========== Helpers ==========

    private Document parsePom(Path pomPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Disable external entities for security
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(pomPath.toFile());
    }

    /**
     * Finds the {@code <execution>} element with id {@code copy-flyway-migrations}
     * inside a {@code maven-resources-plugin} plugin declaration.
     */
    private Element findCopyFlywayMigrationsExecution(Document doc, String moduleName) {
        NodeList plugins = doc.getElementsByTagName("plugin");
        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            String artifactId = getChildText(plugin, "artifactId");
            if (!MAVEN_RESOURCES_PLUGIN_ARTIFACT_ID.equals(artifactId)) {
                continue;
            }

            NodeList executions = plugin.getElementsByTagName("execution");
            for (int j = 0; j < executions.getLength(); j++) {
                Element execution = (Element) executions.item(j);
                String id = getChildText(execution, "id");
                if (COPY_FLYWAY_MIGRATIONS_ID.equals(id)) {
                    return execution;
                }
            }
        }
        return null;
    }

    /**
     * Returns the trimmed text content of the first direct child element
     * with the given tag name, or {@code null} if not found.
     */
    private String getChildText(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() == 0) {
            return null;
        }
        return children.item(0).getTextContent().trim();
    }
}
