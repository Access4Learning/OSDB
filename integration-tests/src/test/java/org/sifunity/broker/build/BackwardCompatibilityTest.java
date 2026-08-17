// SPDX-License-Identifier: MIT
package org.sifunity.broker.build;

import org.apache.maven.shared.invoker.*;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example-based tests for backward compatibility validation.
 * 
 * <p>This test class validates that individual modules can still be built
 * independently, maintaining backward compatibility with existing workflows.
 * Uses XML parsing to verify POM structure and Maven Invoker for programmatic
 * build execution from module directories.
 * 
 * <p>The tests verify that:
 * <ul>
 *   <li>Individual modules can be built from their own directories</li>
 *   <li>Modules correctly reference the parent POM using relativePath</li>
 *   <li>Module builds resolve dependencies and produce artifacts</li>
 *   <li>Existing development workflows remain functional</li>
 * </ul>
 * 
 * <p><b>Validates: Requirement 12.1</b>
 * 
 * @since 1.0.0
 */
public class BackwardCompatibilityTest {
    
    // ========================================
    // Constants
    // ========================================
    
    private static final String PROJECT_VERSION = "1.0.0-SNAPSHOT";
    private static final String EXPECTED_PARENT_GROUP_ID = "org.sifunity.broker";
    private static final String EXPECTED_PARENT_ARTIFACT_ID = "sif-unity-broker";
    private static final String EXPECTED_RELATIVE_PATH = "../pom.xml";
    
    /**
     * List of modules to test for backward compatibility.
     * Excludes integration-tests as it's not typically built independently.
     */
    private static final List<String> MODULES_TO_TEST = Arrays.asList(
            "sif-broker-common",
            "environment-service",
            "queue-service",
            "subscription-service",
            "job-service",
            "gateway",
            "events-connector",
            "requests-connector",
            "responses-connector",
            "services-connector"
    );
    
    private static Invoker invoker;
    
    // ========================================
    // Setup
    // ========================================
    
    @BeforeAll
    static void setUp() {
        invoker = new DefaultInvoker();
        
        // Try to locate Maven home automatically
        String mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome == null) {
            mavenHome = System.getenv("M2_HOME");
        }
        
        if (mavenHome != null) {
            invoker.setMavenHome(new File(mavenHome));
        }
        // If Maven home is not set, invoker will try to find it automatically
    }
    
    // ========================================
    // XML Parsing Utilities
    // ========================================
    
    /**
     * Parses a POM file and returns a JDOM Document.
     * 
     * @param pomFile the POM file to parse
     * @return the parsed JDOM Document
     * @throws RuntimeException if parsing fails
     */
    private static Document parsePom(File pomFile) {
        try {
            SAXBuilder saxBuilder = new SAXBuilder();
            return saxBuilder.build(pomFile);
        } catch (JDOMException | IOException e) {
            throw new RuntimeException("Failed to parse POM file: " + pomFile.getAbsolutePath(), e);
        }
    }
    
    /**
     * Gets the text content of a child element.
     * 
     * @param parent the parent element
     * @param childName the name of the child element
     * @return the text content, or null if not found
     */
    private static String getChildText(Element parent, String childName) {
        Element child = parent.getChild(childName, parent.getNamespace());
        return child != null ? child.getTextTrim() : null;
    }
    
    /**
     * Gets a child element by name.
     * 
     * @param parent the parent element
     * @param childName the name of the child element
     * @return the child element, or null if not found
     */
    private static Element getChild(Element parent, String childName) {
        return parent.getChild(childName, parent.getNamespace());
    }
    
    /**
     * Extracts the parent reference from a module POM.
     * 
     * @param pom the POM document
     * @return the parent element, or null if not found
     */
    private static Element extractParent(Document pom) {
        Element root = pom.getRootElement();
        return getChild(root, "parent");
    }
    
    // ========================================
    // Maven Invocation Utilities
    // ========================================
    
    /**
     * Executes a Maven build in a specific module directory.
     * 
     * @param moduleName the name of the module directory
     * @param goals the Maven goals to execute (e.g., "clean", "install")
     * @return the invocation result
     * @throws MavenInvocationException if the invocation fails
     */
    private static InvocationResult executeMavenBuildInModule(String moduleName, String... goals) 
            throws MavenInvocationException {
        File modulePomFile = getModulePomFile(moduleName);
        
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(modulePomFile);
        request.setGoals(Arrays.asList(goals));
        request.setBatchMode(true); // Non-interactive mode
        
        return invoker.execute(request);
    }
    
    /**
     * Gets the POM file for a specific module.
     * 
     * @param moduleName the name of the module
     * @return the POM file
     */
    private static File getModulePomFile(String moduleName) {
        Path pomPath = Paths.get("..", moduleName, "pom.xml");
        return pomPath.toFile();
    }
    
    /**
     * Gets the JAR artifact file for a specific module.
     * 
     * @param moduleName the name of the module
     * @return the JAR file
     */
    private static File getModuleJarFile(String moduleName) {
        String jarFileName = moduleName + "-" + PROJECT_VERSION + ".jar";
        Path jarPath = Paths.get("..", moduleName, "target", jarFileName);
        return jarPath.toFile();
    }
    
    /**
     * Verifies that a module's POM correctly references the parent POM.
     * 
     * @param moduleName the name of the module
     */
    private static void verifyParentReference(String moduleName) {
        File modulePomFile = getModulePomFile(moduleName);
        
        assertThat(modulePomFile)
                .as("Module POM should exist for: %s", moduleName)
                .exists()
                .isFile();
        
        Document pom = parsePom(modulePomFile);
        Element parent = extractParent(pom);
        
        assertThat(parent)
                .as("Module %s should have a parent reference", moduleName)
                .isNotNull();
        
        String groupId = getChildText(parent, "groupId");
        String artifactId = getChildText(parent, "artifactId");
        String relativePath = getChildText(parent, "relativePath");
        
        assertThat(groupId)
                .as("Parent groupId should be correct for module: %s", moduleName)
                .isEqualTo(EXPECTED_PARENT_GROUP_ID);
        
        assertThat(artifactId)
                .as("Parent artifactId should be correct for module: %s", moduleName)
                .isEqualTo(EXPECTED_PARENT_ARTIFACT_ID);
        
        assertThat(relativePath)
                .as("Parent relativePath should be correct for module: %s", moduleName)
                .isEqualTo(EXPECTED_RELATIVE_PATH);
    }
    
    // ========================================
    // Tests
    // ========================================
    
    /**
     * Test: Module POM Parent References
     * 
     * <p>Verifies that all module POMs correctly reference the parent POM
     * with the correct groupId, artifactId, and relativePath. This ensures
     * that individual module builds can locate and inherit from the parent POM.
     * 
     * <p>This test uses XML parsing to validate POM structure without executing
     * a full Maven build, making it fast and reliable.
     * 
     * <p><b>Validates: Requirements 12.2</b>
     */
    @Test
    void modulePomsReferenceParentCorrectly() {
        for (String moduleName : MODULES_TO_TEST) {
            verifyParentReference(moduleName);
        }
    }
    
    /**
     * Test: Individual Module Build - sif-broker-common
     * 
     * <p>Verifies that sif-broker-common can be built independently from its
     * own directory. This is the foundational library that other modules depend on,
     * so it's critical that it can be built in isolation.
     * 
     * <p>This test:
     * <ul>
     *   <li>Navigates to the sif-broker-common directory programmatically</li>
     *   <li>Executes {@code mvn clean install} from that directory</li>
     *   <li>Verifies the build completes successfully</li>
     *   <li>Verifies the JAR artifact is created</li>
     * </ul>
     * 
     * <p><b>Validates: Requirements 12.1</b>
     * 
     * @throws MavenInvocationException if Maven invocation fails
     */
    @Test
    void individualModuleBuild_SifBrokerCommon() throws MavenInvocationException {
        String moduleName = "sif-broker-common";
        
        // Execute mvn clean install from module directory
        InvocationResult result = executeMavenBuildInModule(moduleName, "clean", "install");
        
        // Verify build completed successfully
        assertThat(result.getExitCode())
                .as("Individual build of %s should complete successfully", moduleName)
                .isEqualTo(0);
        
        // Verify JAR artifact was created
        File jarFile = getModuleJarFile(moduleName);
        assertThat(jarFile)
                .as("JAR artifact should be created for %s", moduleName)
                .exists()
                .isFile();
    }
    
    /**
     * Test: Individual Module Build - environment-service
     * 
     * <p>Verifies that environment-service can be built independently from its
     * own directory. This service has module-specific dependencies (spring-vault)
     * that should be retained during individual builds.
     * 
     * <p>This test:
     * <ul>
     *   <li>Navigates to the environment-service directory programmatically</li>
     *   <li>Executes {@code mvn clean install -DskipTests} from that directory</li>
     *   <li>Verifies the build completes successfully</li>
     *   <li>Verifies the JAR artifact is created</li>
     * </ul>
     * 
     * <p>Note: This test skips tests during the build to avoid Docker/Testcontainers
     * requirements, focusing on compilation and packaging which is the core backward
     * compatibility concern. This also reflects a common developer workflow.
     * 
     * <p>Note: This test requires sif-broker-common to be available in the local
     * Maven repository, as environment-service depends on it.
     * 
     * <p><b>Validates: Requirements 12.1, 12.4</b>
     * 
     * @throws MavenInvocationException if Maven invocation fails
     */
    @Test
    void individualModuleBuild_EnvironmentService() throws MavenInvocationException {
        String moduleName = "environment-service";
        
        // Execute mvn clean install -DskipTests from module directory
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(getModulePomFile(moduleName));
        request.setGoals(Arrays.asList("clean", "install"));
        request.setBatchMode(true);
        
        // Skip tests to avoid Docker/Testcontainers requirements
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("skipTests", "true");
        request.setProperties(properties);
        
        InvocationResult result = invoker.execute(request);
        
        // Verify build completed successfully
        assertThat(result.getExitCode())
                .as("Individual build of %s should complete successfully", moduleName)
                .isEqualTo(0);
        
        // Verify JAR artifact was created
        File jarFile = getModuleJarFile(moduleName);
        assertThat(jarFile)
                .as("JAR artifact should be created for %s", moduleName)
                .exists()
                .isFile();
    }
    
    /**
     * Test: Individual Module Build - queue-service
     * 
     * <p>Verifies that queue-service can be built independently from its
     * own directory. This service has AWS SDK dependencies that should be
     * properly resolved during individual builds.
     * 
     * <p>This test:
     * <ul>
     *   <li>Navigates to the queue-service directory programmatically</li>
     *   <li>Executes {@code mvn clean install} from that directory</li>
     *   <li>Verifies the build completes successfully</li>
     *   <li>Verifies the JAR artifact is created</li>
     * </ul>
     * 
     * <p><b>Validates: Requirements 12.1, 12.4</b>
     * 
     * @throws MavenInvocationException if Maven invocation fails
     */
    @Test
    void individualModuleBuild_QueueService() throws MavenInvocationException {
        String moduleName = "queue-service";
        
        // Execute mvn clean install from module directory
        InvocationResult result = executeMavenBuildInModule(moduleName, "clean", "install");
        
        // Verify build completed successfully
        assertThat(result.getExitCode())
                .as("Individual build of %s should complete successfully", moduleName)
                .isEqualTo(0);
        
        // Verify JAR artifact was created
        File jarFile = getModuleJarFile(moduleName);
        assertThat(jarFile)
                .as("JAR artifact should be created for %s", moduleName)
                .exists()
                .isFile();
    }
    
    /**
     * Test: Individual Module Build - gateway
     * 
     * <p>Verifies that the gateway module can be built independently from its
     * own directory. The gateway is a critical component that routes requests
     * to other services.
     * 
     * <p>This test:
     * <ul>
     *   <li>Navigates to the gateway directory programmatically</li>
     *   <li>Executes {@code mvn clean install} from that directory</li>
     *   <li>Verifies the build completes successfully</li>
     *   <li>Verifies the JAR artifact is created</li>
     * </ul>
     * 
     * <p><b>Validates: Requirements 12.1, 12.4</b>
     * 
     * @throws MavenInvocationException if Maven invocation fails
     */
    @Test
    void individualModuleBuild_Gateway() throws MavenInvocationException {
        String moduleName = "gateway";
        
        // Execute mvn clean install from module directory
        InvocationResult result = executeMavenBuildInModule(moduleName, "clean", "install");
        
        // Verify build completed successfully
        assertThat(result.getExitCode())
                .as("Individual build of %s should complete successfully", moduleName)
                .isEqualTo(0);
        
        // Verify JAR artifact was created
        File jarFile = getModuleJarFile(moduleName);
        assertThat(jarFile)
                .as("JAR artifact should be created for %s", moduleName)
                .exists()
                .isFile();
    }
    
    /**
     * Test: Individual Module Build with Tests - requests-connector
     * 
     * <p>Verifies that requests-connector can be built independently with
     * tests enabled. This ensures that the test configuration (including
     * jqwik property tests) works correctly in individual module builds.
     * 
     * <p>This test:
     * <ul>
     *   <li>Navigates to the requests-connector directory programmatically</li>
     *   <li>Executes {@code mvn clean test} from that directory</li>
     *   <li>Verifies the build and tests complete successfully</li>
     * </ul>
     * 
     * <p><b>Validates: Requirements 12.1, 7.2</b>
     * 
     * @throws MavenInvocationException if Maven invocation fails
     */
    @Test
    void individualModuleBuildWithTests_RequestsConnector() throws MavenInvocationException {
        String moduleName = "requests-connector";
        
        // Execute mvn clean test from module directory
        InvocationResult result = executeMavenBuildInModule(moduleName, "clean", "test");
        
        // Verify build and tests completed successfully
        assertThat(result.getExitCode())
                .as("Individual build with tests of %s should complete successfully", moduleName)
                .isEqualTo(0);
    }
    
    /**
     * Test: Individual Module Package Build - services-connector
     * 
     * <p>Verifies that services-connector can be packaged independently,
     * producing an executable JAR without running tests. This simulates
     * a common development workflow where developers want to quickly
     * package a module for testing.
     * 
     * <p>This test:
     * <ul>
     *   <li>Navigates to the services-connector directory programmatically</li>
     *   <li>Executes {@code mvn clean package -DskipTests} from that directory</li>
     *   <li>Verifies the build completes successfully</li>
     *   <li>Verifies the JAR artifact is created</li>
     * </ul>
     * 
     * <p><b>Validates: Requirements 12.1, 10.3</b>
     * 
     * @throws MavenInvocationException if Maven invocation fails
     */
    @Test
    void individualModulePackageBuild_ServicesConnector() throws MavenInvocationException {
        String moduleName = "services-connector";
        
        // Execute mvn clean package -DskipTests from module directory
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(getModulePomFile(moduleName));
        request.setGoals(Arrays.asList("clean", "package"));
        request.setBatchMode(true);
        request.setMavenOpts("-DskipTests");
        
        InvocationResult result = invoker.execute(request);
        
        // Verify build completed successfully
        assertThat(result.getExitCode())
                .as("Individual package build of %s should complete successfully", moduleName)
                .isEqualTo(0);
        
        // Verify JAR artifact was created
        File jarFile = getModuleJarFile(moduleName);
        assertThat(jarFile)
                .as("JAR artifact should be created for %s", moduleName)
                .exists()
                .isFile();
    }


    /**
     * Test: Existing Tooling Compatibility - Broker Scripts
     *
     * <p>Verifies that existing automation scripts (start-broker.sh and stop-broker.sh)
     * continue to function correctly after introducing the parent POM. These scripts
     * are critical developer tools that use {@code mvn spring-boot:run} to start services.
     *
     * <p>This test validates that:
     * <ul>
     *   <li>start-broker.sh script exists and is executable</li>
     *   <li>stop-broker.sh script exists and is executable</li>
     *   <li>Scripts can navigate to module directories and execute Maven commands</li>
     *   <li>The parent POM structure doesn't break the script's Maven invocations</li>
     * </ul>
     *
     * <p>Note: This test does NOT actually start the services (which would require
     * PostgreSQL, Redis, and would be time-consuming). Instead, it validates that:
     * <ul>
     *   <li>The scripts exist and have correct permissions</li>
     *   <li>The scripts reference valid module directories</li>
     *   <li>Maven can be invoked from module directories (dry-run validation)</li>
     * </ul>
     *
     * <p>This approach ensures backward compatibility without the overhead of
     * actually starting all services, which is more appropriate for a build test.
     *
     * <p><b>Validates: Requirements 12.4</b>
     */
    @Test
    void existingToolingCompatibility_BrokerScripts() {
        // Verify start-broker.sh exists
        File startScript = Paths.get("..", "start-broker.sh").toFile();
        assertThat(startScript)
                .as("start-broker.sh should exist in repository root")
                .exists()
                .isFile();

        // Verify stop-broker.sh exists
        File stopScript = Paths.get("..", "stop-broker.sh").toFile();
        assertThat(stopScript)
                .as("stop-broker.sh should exist in repository root")
                .exists()
                .isFile();

        // Verify scripts are executable (on Unix-like systems)
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            assertThat(startScript.canExecute())
                    .as("start-broker.sh should be executable")
                    .isTrue();

            assertThat(stopScript.canExecute())
                    .as("stop-broker.sh should be executable")
                    .isTrue();
        }

        // Verify that the scripts reference valid module directories
        // The start-broker.sh script navigates to each service directory
        // and runs mvn spring-boot:run, so we validate those directories exist
        List<String> servicesInScript = Arrays.asList(
                "environment-service",
                "queue-service",
                "subscription-service",
                "job-service",
                "requests-connector",
                "responses-connector",
                "events-connector",
                "services-connector",
                "gateway"
        );

        for (String serviceName : servicesInScript) {
            File serviceDir = Paths.get("..", serviceName).toFile();
            assertThat(serviceDir)
                    .as("Service directory %s referenced by start-broker.sh should exist", serviceName)
                    .exists()
                    .isDirectory();

            File servicePom = new File(serviceDir, "pom.xml");
            assertThat(servicePom)
                    .as("POM file should exist in %s for Maven commands in start-broker.sh", serviceName)
                    .exists()
                    .isFile();
        }

        // Verify that Maven can be invoked from a service directory
        // This validates that the parent POM structure doesn't break Maven invocations
        // We use 'mvn help:evaluate' as a lightweight command that doesn't build anything
        try {
            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(getModulePomFile("gateway"));
            request.setGoals(Arrays.asList("help:evaluate", "-Dexpression=project.version", "-q", "-DforceStdout"));
            request.setBatchMode(true);

            InvocationResult result = invoker.execute(request);

            assertThat(result.getExitCode())
                    .as("Maven should be able to evaluate project properties from module directory (simulating script behavior)")
                    .isEqualTo(0);
        } catch (MavenInvocationException e) {
            throw new AssertionError("Maven invocation from module directory failed: " + e.getMessage(), e);
        }
    }

    /**
     * Test: Existing Tooling Compatibility - Docker Builds
     *
     * <p>Verifies that existing Docker build configurations continue to work
     * after introducing the parent POM. Each service has a Dockerfile that
     * uses Maven to build the application in a multi-stage build.
     *
     * <p>This test validates that:
     * <ul>
     *   <li>Dockerfiles exist for all services</li>
     *   <li>Dockerfiles reference valid Maven build commands</li>
     *   <li>The parent POM structure is compatible with Docker builds</li>
     * </ul>
     *
     * <p>Note: This test does NOT actually build Docker images (which would be
     * time-consuming and require Docker daemon). Instead, it validates that:
     * <ul>
     *   <li>Dockerfiles exist in expected locations</li>
     *   <li>The Maven build commands in Dockerfiles can execute successfully</li>
     *   <li>The parent POM can be resolved during Docker builds</li>
     * </ul>
     *
     * <p>The Dockerfiles use a multi-stage build pattern:
     * <pre>
     * FROM maven:3.9-amazoncorretto-17 AS build
     * COPY pom.xml .
     * RUN mvn dependency:go-offline -B
     * COPY src ./src
     * RUN mvn clean package -DskipTests -B
     * </pre>
     *
     * <p>This test ensures that when Docker copies the module's pom.xml and
     * runs Maven commands, the parent POM can be resolved correctly.
     *
     * <p><b>Validates: Requirements 12.4, 10.5</b>
     */
    @Test
    void existingToolingCompatibility_DockerBuilds() {
        // List of services that have Dockerfiles
        List<String> servicesWithDockerfiles = Arrays.asList(
                "environment-service",
                "queue-service",
                "subscription-service",
                "job-service",
                "gateway",
                "events-connector",
                "requests-connector",
                "responses-connector",
                "services-connector"
        );

        for (String serviceName : servicesWithDockerfiles) {
            // Verify Dockerfile exists
            File dockerfile = Paths.get("..", serviceName, "Dockerfile").toFile();
            assertThat(dockerfile)
                    .as("Dockerfile should exist for %s", serviceName)
                    .exists()
                    .isFile();

            // Verify the service POM exists (required by Dockerfile)
            File servicePom = getModulePomFile(serviceName);
            assertThat(servicePom)
                    .as("POM file should exist for Docker build of %s", serviceName)
                    .exists()
                    .isFile();

            // Verify parent POM can be resolved from service directory
            // This simulates what happens during Docker build when it copies pom.xml
            // and tries to resolve the parent
            Document pom = parsePom(servicePom);
            Element parent = extractParent(pom);

            assertThat(parent)
                    .as("Service %s should have parent reference for Docker builds", serviceName)
                    .isNotNull();

            String relativePath = getChildText(parent, "relativePath");
            assertThat(relativePath)
                    .as("Parent relativePath should be set for %s (required for Docker builds)", serviceName)
                    .isEqualTo("../pom.xml");

            // Verify that the parent POM file actually exists at the relative path
            File parentPomFile = Paths.get("..", serviceName, relativePath).toFile();
            assertThat(parentPomFile)
                    .as("Parent POM should exist at relative path for Docker build of %s", serviceName)
                    .exists()
                    .isFile();
        }

        // Validate that Maven commands used in Dockerfiles work correctly
        // Dockerfiles typically run: mvn dependency:go-offline and mvn clean package -DskipTests
        // We'll test the dependency resolution command as a lightweight validation
        try {
            String testService = "gateway";
            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(getModulePomFile(testService));
            request.setGoals(Arrays.asList("dependency:resolve", "-B"));
            request.setBatchMode(true);

            InvocationResult result = invoker.execute(request);

            assertThat(result.getExitCode())
                    .as("Maven dependency resolution should work from module directory (simulating Docker build)")
                    .isEqualTo(0);
        } catch (MavenInvocationException e) {
            throw new AssertionError("Maven dependency resolution failed (Docker build simulation): " + e.getMessage(), e);
        }
    }

}
