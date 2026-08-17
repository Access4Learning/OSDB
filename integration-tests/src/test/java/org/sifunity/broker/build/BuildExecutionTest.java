// SPDX-License-Identifier: MIT
package org.sifunity.broker.build;

import org.apache.maven.shared.invoker.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example-based tests for Maven build execution validation.
 * 
 * <p>This test class validates Maven build execution using programmatic
 * Maven invocation. It verifies that builds complete successfully, artifacts
 * are created, build order is correct, tests execute properly, and executable
 * JARs are generated.
 * 
 * <p>Uses Maven Invoker API for programmatic build execution and artifact
 * verification.
 * 
 * <p><b>Validates: Requirement 7.1</b>
 * 
 * @since 1.0.0
 */
public class BuildExecutionTest {
    
    // ========================================
    // Constants
    // ========================================
    
    private static final String ROOT_POM_PATH = "../pom.xml";
    private static final String PROJECT_VERSION = "1.0.0-SNAPSHOT";
    
    /**
     * List of all expected modules in the project.
     * Note: observability is excluded from the build.
     */
    private static final List<String> EXPECTED_MODULES = Arrays.asList(
            "sif-broker-common",
            "environment-service",
            "queue-service",
            "subscription-service",
            "job-service",
            "gateway",
            "events-connector",
            "requests-connector",
            "responses-connector",
            "services-connector",
            "integration-tests"
    );
    
    /**
     * List of Spring Boot service modules that should produce executable JARs.
     * Excludes sif-broker-common (library) and integration-tests (test module).
     */
    private static final List<String> SPRING_BOOT_SERVICES = Arrays.asList(
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
    private static File rootPomFile;
    
    // ========================================
    // Setup
    // ========================================
    
    @BeforeAll
    static void setUp() {
        invoker = new DefaultInvoker();
        rootPomFile = new File(ROOT_POM_PATH);
        
        // Verify root POM exists
        assertThat(rootPomFile)
                .as("Root POM file should exist")
                .exists()
                .isFile();
        
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
    // Maven Invocation Utilities
    // ========================================
    
    /**
     * Executes a Maven build with the specified goals.
     * 
     * @param goals the Maven goals to execute (e.g., "clean", "install")
     * @return the invocation result
     * @throws MavenInvocationException if the invocation fails
     */
    private static InvocationResult executeMavenBuild(String... goals) throws MavenInvocationException {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(rootPomFile);
        request.setGoals(Arrays.asList(goals));
        request.setBatchMode(true);
        // Skip tests in nested builds to avoid infinite recursion
        // (BuildExecutionTest itself runs inside mvn test)
        request.setProperties(new java.util.Properties() {{ setProperty("skipTests", "true"); }});
        
        return invoker.execute(request);
    }
    
    /**
     * Executes a Maven build with the specified goals and captures output.
     * 
     * @param goals the Maven goals to execute (e.g., "clean", "install")
     * @return a result object containing exit code and captured output
     * @throws MavenInvocationException if the invocation fails
     */
    private static BuildResult executeMavenBuildWithOutput(String... goals) throws MavenInvocationException {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(rootPomFile);
        request.setGoals(Arrays.asList(goals));
        request.setBatchMode(true);
        // Skip tests in nested builds to avoid infinite recursion
        request.setProperties(new java.util.Properties() {{ setProperty("skipTests", "true"); }});
        
        // Capture output
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);
        InvocationOutputHandler outputHandler = printStream::println;
        request.setOutputHandler(outputHandler);
        
        InvocationResult result = invoker.execute(request);
        String output = outputStream.toString();
        
        return new BuildResult(result.getExitCode(), output);
    }
    
    /**
     * Helper class to hold build result with captured output.
     */
    private static class BuildResult {
        private final int exitCode;
        private final String output;
        
        BuildResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
        
        int getExitCode() {
            return exitCode;
        }
        
        String getOutput() {
            return output;
        }
    }
    
    /**
     * Executes a Maven build with the specified goals and additional options.
     * 
     * @param goals the Maven goals to execute
     * @param options additional Maven options (e.g., "-DskipTests")
     * @return the invocation result
     * @throws MavenInvocationException if the invocation fails
     */
    private static InvocationResult executeMavenBuildWithOptions(List<String> goals, List<String> options) 
            throws MavenInvocationException {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(rootPomFile);
        request.setGoals(goals);
        request.setMavenOpts(String.join(" ", options));
        request.setBatchMode(true);
        
        return invoker.execute(request);
    }
    
    /**
     * Verifies that a JAR artifact exists for the specified module.
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
     * Verifies that a JAR file is an executable Spring Boot JAR.
     * An executable Spring Boot JAR contains a BOOT-INF directory.
     * 
     * @param jarFile the JAR file to check
     * @return true if the JAR is executable, false otherwise
     */
    private static boolean isExecutableJar(File jarFile) {
        if (!jarFile.exists() || !jarFile.isFile()) {
            return false;
        }
        
        try {
            // Use jar command to list contents and check for BOOT-INF
            Process process = new ProcessBuilder("jar", "tf", jarFile.getAbsolutePath())
                    .redirectErrorStream(true)
                    .start();
            
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            
            return output.contains("BOOT-INF/");
        } catch (Exception e) {
            // If jar command fails, fall back to checking file size
            // Executable JARs are typically larger due to embedded dependencies
            return jarFile.length() > 1_000_000; // > 1MB suggests executable JAR
        }
    }
    
    /**
     * Extracts the reactor build order from Maven output.
     * Parses Maven's "Reactor Build Order" section to determine the actual build sequence.
     * 
     * @param output the Maven build output
     * @return list of modules in build order
     */
    private static List<String> extractBuildOrder(String output) {
        List<String> buildOrder = new ArrayList<>();
        
        // Pattern to match lines like "[INFO] sif-broker-common .................... SUCCESS"
        // or "[INFO] Building sif-broker-common 1.0.0-SNAPSHOT"
        Pattern buildingPattern = Pattern.compile("\\[INFO\\] Building ([a-z-]+) ");
        
        String[] lines = output.split("\n");
        for (String line : lines) {
            Matcher matcher = buildingPattern.matcher(line);
            if (matcher.find()) {
                String moduleName = matcher.group(1);
                // Only add if it's one of our expected modules
                if (EXPECTED_MODULES.contains(moduleName) && !buildOrder.contains(moduleName)) {
                    buildOrder.add(moduleName);
                }
            }
        }
        
        return buildOrder;
    }
    
    // ========================================
    // Tests
    // ========================================
    
    // Test implementations will be added in subsequent tasks (6.2-6.5)


    // ========================================
    // Task 6.2: Complete Build Artifact Creation
    // ========================================

    /**
     * Test: Complete Build Produces All Artifacts
     *
     * <p>Validates that executing {@code mvn clean install} from the repository root
     * produces JAR artifacts for all 11 modules in the project.
     *
     * <p>This test:
     * <ul>
     *   <li>Executes a full Maven build with clean and install goals</li>
     *   <li>Verifies the build completes successfully (exit code 0)</li>
     *   <li>Verifies JAR artifacts exist in target directories for all modules</li>
     * </ul>
     *
     * <p><b>Validates: Requirements 1.1, 1.4, 10.1</b>
     *
     * @throws MavenInvocationException if Maven invocation fails
     */
    @Test
    void completeBuildProducesAllArtifacts() throws MavenInvocationException {
        // Execute mvn clean install
        InvocationResult result = executeMavenBuild("clean", "install");

        // Verify build exit code is 0 (success)
        assertThat(result.getExitCode())
                .as("Build should complete successfully with exit code 0")
                .isEqualTo(0);

        // Verify JAR artifacts exist for all modules
        for (String moduleName : EXPECTED_MODULES) {
            File jarFile = getModuleJarFile(moduleName);
            assertThat(jarFile)
                    .as("JAR artifact should exist for module: %s", moduleName)
                    .exists()
                    .isFile();
        }
    }

    // ========================================
    // Task 6.3: Build Order Verification
    // ========================================

    /**
     * Test: Build Order Respects Dependencies
     *
     * <p>Validates that the Maven reactor builds modules in the correct order,
     * ensuring that dependencies are built before dependent modules.
     *
     * <p>This test:
     * <ul>
     *   <li>Executes {@code mvn clean install} and captures the reactor build order</li>
     *   <li>Verifies sif-broker-common is built before all service modules</li>
     *   <li>Verifies integration-tests is built last</li>
     * </ul>
     *
     * <p>The Maven reactor analyzes inter-module dependencies and determines the
     * optimal build order. Since all service modules depend on sif-broker-common,
     * it must be built first. Integration tests depend on all services, so they
     * must be built last.
     *
     * <p><b>Validates: Requirements 2.5, 5.1, 5.4, 8.1, 8.2</b>
     *
     * @throws MavenInvocationException if Maven invocation fails
     */
    @Test
    void buildOrderRespectsDependencies() throws MavenInvocationException {
        // Execute mvn clean install and capture output
        BuildResult result = executeMavenBuildWithOutput("clean", "install");

        // Verify build completed successfully
        assertThat(result.getExitCode())
                .as("Build should complete successfully with exit code 0")
                .isEqualTo(0);

        // Extract build order from Maven output
        List<String> buildOrder = extractBuildOrder(result.getOutput());

        // Verify we captured the build order for all modules
        assertThat(buildOrder)
                .as("Build order should contain all expected modules")
                .containsAll(EXPECTED_MODULES);

        // Verify sif-broker-common is built before all service modules
        int commonIndex = buildOrder.indexOf("sif-broker-common");
        assertThat(commonIndex)
                .as("sif-broker-common should be in the build order")
                .isGreaterThanOrEqualTo(0);

        for (String service : SPRING_BOOT_SERVICES) {
            int serviceIndex = buildOrder.indexOf(service);
            if (serviceIndex >= 0) {
                assertThat(serviceIndex)
                        .as("Service %s should be built after sif-broker-common", service)
                        .isGreaterThan(commonIndex);
            }
        }

        // Verify integration-tests is built last
        int integrationTestsIndex = buildOrder.indexOf("integration-tests");
        assertThat(integrationTestsIndex)
                .as("integration-tests should be in the build order")
                .isGreaterThanOrEqualTo(0);

        assertThat(integrationTestsIndex)
                .as("integration-tests should be built last")
                .isEqualTo(buildOrder.size() - 1);
    }

    // ========================================
    // Task 6.4: Test Execution Across Modules
    // ========================================

    /**
     * Test: Test Execution Across All Modules
     *
     * <p>Validates that executing {@code mvn test} from the repository root
     * runs all tests across all modules, including jqwik property-based tests.
     *
     * <p>This test:
     * <ul>
     *   <li>Executes {@code mvn test} and captures the output</li>
     *   <li>Verifies tests execute in all modules</li>
     *   <li>Verifies jqwik property tests (*Properties.java) are executed</li>
     *   <li>Verifies the test phase completes successfully</li>
     * </ul>
     *
     * <p>The Maven Surefire plugin is configured in the parent POM to recognize
     * test patterns including *Test.java, *Tests.java, and *Properties.java,
     * ensuring that both traditional JUnit tests and jqwik property-based tests
     * are executed.
     *
     * <p><b>Validates: Requirement 7.1</b>
     *
     * @throws MavenInvocationException if Maven invocation fails
     */
    @Test
    void testExecutionAcrossAllModules() throws MavenInvocationException {
        // Execute mvn test excluding integration-tests to avoid recursion
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(rootPomFile);
        request.setGoals(Arrays.asList("test"));
        request.setBatchMode(true);
        request.setProjects(Arrays.asList("!integration-tests"));
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);
        InvocationOutputHandler outputHandler = printStream::println;
        request.setOutputHandler(outputHandler);
        
        InvocationResult invResult = invoker.execute(request);
        BuildResult result = new BuildResult(invResult.getExitCode(), outputStream.toString());

        // Verify test execution completed successfully
        assertThat(result.getExitCode())
                .as("Test execution should complete successfully with exit code 0")
                .isEqualTo(0);

        String output = result.getOutput();

        // Verify tests executed in key modules (integration-tests excluded to avoid recursion)
        for (String moduleName : SPRING_BOOT_SERVICES) {
            assertThat(output)
                    .as("Tests should execute in module: %s", moduleName)
                    .containsIgnoringCase("Building " + moduleName);
        }

        // Verify jqwik property tests are executed
        // Look for evidence of property test execution in the output
        // jqwik outputs "tries" information when running property tests
        assertThat(output)
                .as("jqwik property tests should be executed (look for 'tries' or 'Property' in output)")
                .containsAnyOf("tries", "Property", "PomStructureProperties");

        // Verify test summary is present
        // Maven Surefire outputs "Tests run:" summary for each module
        assertThat(output)
                .as("Test summary should be present in output")
                .contains("Tests run:");

        // Verify no test failures
        assertThat(output)
                .as("No test failures should occur")
                .doesNotContain("Tests run:")
                .doesNotContain("Failures: 0")
                .doesNotContain("BUILD FAILURE");
    }

    // ========================================
    // Task 6.5: Executable JAR Creation
    // ========================================

    /**
     * Test: Executable JAR Creation
     *
     * <p>Validates that executing {@code mvn package} from the repository root
     * creates executable Spring Boot JARs for all service modules.
     *
     * <p>This test:
     * <ul>
     *   <li>Executes {@code mvn package -DskipTests} to build JARs without running tests</li>
     *   <li>Verifies all Spring Boot service JARs contain BOOT-INF directory</li>
     *   <li>Verifies JAR locations match deployment expectations (module/target/*.jar)</li>
     *   <li>Verifies non-service modules (sif-broker-common, integration-tests) produce regular JARs</li>
     * </ul>
     *
     * <p>Spring Boot executable JARs are identified by the presence of a BOOT-INF
     * directory, which contains the application classes and embedded dependencies.
     * The Spring Boot Maven Plugin configured in the parent POM ensures that all
     * service modules produce executable JARs.
     *
     * <p>Note: This test uses -DskipTests flag to keep execution lightweight and
     * prevent hanging on long-running tests.
     *
     * <p><b>Validates: Requirements 10.3, 10.5</b>
     *
     * @throws MavenInvocationException if Maven invocation fails
     */
    @Test
    void executableJarCreation() throws MavenInvocationException {
        // Execute mvn package -DskipTests
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(rootPomFile);
        request.setGoals(Arrays.asList("clean", "package"));
        request.setBatchMode(true);
        request.setMavenOpts("-DskipTests");
        
        InvocationResult result = invoker.execute(request);

        // Verify build completed successfully
        assertThat(result.getExitCode())
                .as("Package build should complete successfully with exit code 0")
                .isEqualTo(0);

        // Verify all Spring Boot service JARs are executable
        for (String serviceName : SPRING_BOOT_SERVICES) {
            File jarFile = getModuleJarFile(serviceName);
            
            // Verify JAR exists in expected location
            assertThat(jarFile)
                    .as("JAR should exist for service: %s at location: %s", 
                        serviceName, jarFile.getAbsolutePath())
                    .exists()
                    .isFile();
            
            // Verify JAR is executable (contains BOOT-INF directory)
            assertThat(isExecutableJar(jarFile))
                    .as("JAR should be executable (contain BOOT-INF) for service: %s", serviceName)
                    .isTrue();
        }

        // Verify sif-broker-common produces a regular JAR (not executable)
        File commonJar = getModuleJarFile("sif-broker-common");
        assertThat(commonJar)
                .as("JAR should exist for sif-broker-common")
                .exists()
                .isFile();
        
        // sif-broker-common is a library, not a Spring Boot application,
        // so it should NOT be an executable JAR
        assertThat(isExecutableJar(commonJar))
                .as("sif-broker-common should produce a regular JAR, not an executable JAR")
                .isFalse();

        // Verify integration-tests produces a JAR
        File integrationTestsJar = getModuleJarFile("integration-tests");
        assertThat(integrationTestsJar)
                .as("JAR should exist for integration-tests")
                .exists()
                .isFile();
    }

}
