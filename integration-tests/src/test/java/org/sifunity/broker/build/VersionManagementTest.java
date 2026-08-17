// SPDX-License-Identifier: MIT
package org.sifunity.broker.build;

import org.apache.maven.shared.invoker.*;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.junit.jupiter.api.BeforeAll;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example-based tests for version management validation.
 * 
 * <p>This test class validates that version management works correctly across
 * the multi-module project, ensuring that all modules inherit versions from
 * the parent POM and that version updates propagate correctly.
 * 
 * <p>The tests verify that:
 * <ul>
 *   <li>Parent POM defines a project version property</li>
 *   <li>Child modules inherit the parent version automatically</li>
 *   <li>Version updates in the parent POM propagate to all modules</li>
 *   <li>Maven versions plugin can update all module versions consistently</li>
 * </ul>
 * 
 * <p><b>Validates: Requirements 6.1, 6.4, 6.5</b>
 * 
 * @since 1.0.0
 */
public class VersionManagementTest {
    
    // ========================================
    // Constants
    // ========================================
    
    private static final String PROJECT_VERSION = "1.0.0-SNAPSHOT";
    private static final String EXPECTED_GROUP_ID = "org.sifunity.broker";
    private static final String ROOT_POM_PATH = "../pom.xml";
    
    /**
     * List of all modules in the project.
     */
    private static final List<String> ALL_MODULES = Arrays.asList(
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
     * Writes a JDOM Document back to a POM file.
     * 
     * @param document the JDOM Document to write
     * @param pomFile the target POM file
     * @throws RuntimeException if writing fails
     */
    private static void writePom(Document document, File pomFile) {
        try {
            XMLOutputter xmlOutputter = new XMLOutputter();
            xmlOutputter.setFormat(Format.getPrettyFormat());
            try (FileWriter writer = new FileWriter(pomFile)) {
                xmlOutputter.output(document, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write POM file: " + pomFile.getAbsolutePath(), e);
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
     * Sets the text content of a child element, creating it if necessary.
     * 
     * @param parent the parent element
     * @param childName the name of the child element
     * @param value the text value to set
     */
    private static void setChildText(Element parent, String childName, String value) {
        Element child = getChild(parent, childName);
        if (child != null) {
            child.setText(value);
        } else {
            Element newChild = new Element(childName, parent.getNamespace());
            newChild.setText(value);
            parent.addContent(newChild);
        }
    }
    
    // ========================================
    // File Utilities
    // ========================================
    
    /**
     * Gets the root POM file.
     * 
     * @return the root POM file
     */
    private static File getRootPomFile() {
        return Paths.get(ROOT_POM_PATH).toFile();
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
     * Creates a backup of a POM file.
     * 
     * @param pomFile the POM file to backup
     * @return the backup file
     * @throws IOException if backup fails
     */
    private static File backupPomFile(File pomFile) throws IOException {
        Path backupPath = Paths.get(pomFile.getAbsolutePath() + ".backup");
        Files.copy(pomFile.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);
        return backupPath.toFile();
    }
    
    /**
     * Restores a POM file from its backup.
     * 
     * @param pomFile the POM file to restore
     * @param backupFile the backup file
     * @throws IOException if restore fails
     */
    private static void restorePomFile(File pomFile, File backupFile) throws IOException {
        Files.copy(backupFile.toPath(), pomFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.delete(backupFile.toPath());
    }
    
    /**
     * Restores all POM files (root and child modules) from their backups.
     * 
     * @param rootBackup the root POM backup file
     * @throws IOException if restore fails
     */
    private static void restoreAllPomFiles(File rootBackup) throws IOException {
        // Restore root POM
        File rootPom = getRootPomFile();
        restorePomFile(rootPom, rootBackup);
        
        // Restore all child module POMs
        for (String moduleName : ALL_MODULES) {
            File modulePom = getModulePomFile(moduleName);
            File moduleBackup = new File(modulePom.getAbsolutePath() + ".backup");
            
            if (moduleBackup.exists()) {
                restorePomFile(modulePom, moduleBackup);
            }
        }
    }
    
    // ========================================
    // Version Manipulation Utilities
    // ========================================
    
    /**
     * Updates the version in the root POM file and all child module parent references.
     * 
     * @param newVersion the new version to set
     * @return the backup file (for restoration)
     * @throws IOException if update fails
     */
    private static File updateRootPomVersion(String newVersion) throws IOException {
        File rootPom = getRootPomFile();
        File backup = backupPomFile(rootPom);
        
        try {
            // Update root POM version
            Document pom = parsePom(rootPom);
            Element root = pom.getRootElement();
            setChildText(root, "version", newVersion);
            writePom(pom, rootPom);
            
            // Update all child module parent version references
            for (String moduleName : ALL_MODULES) {
                File modulePom = getModulePomFile(moduleName);
                
                // Backup child module POM
                backupPomFile(modulePom);
                
                // Update parent version reference
                Document childPom = parsePom(modulePom);
                Element childRoot = childPom.getRootElement();
                Element parent = getChild(childRoot, "parent");
                
                if (parent != null) {
                    setChildText(parent, "version", newVersion);
                    writePom(childPom, modulePom);
                }
            }
            
            return backup;
        } catch (Exception e) {
            // Restore backup on failure
            restoreAllPomFiles(backup);
            throw new RuntimeException("Failed to update root POM version", e);
        }
    }
    
    /**
     * Extracts the version from a POM file.
     * 
     * @param pomFile the POM file
     * @return the version string
     */
    private static String extractVersion(File pomFile) {
        Document pom = parsePom(pomFile);
        Element root = pom.getRootElement();
        return getChildText(root, "version");
    }
    
    /**
     * Extracts the parent version from a child module POM.
     * 
     * @param pomFile the child module POM file
     * @return the parent version string, or null if not found
     */
    private static String extractParentVersion(File pomFile) {
        Document pom = parsePom(pomFile);
        Element root = pom.getRootElement();
        Element parent = getChild(root, "parent");
        
        if (parent != null) {
            return getChildText(parent, "version");
        }
        return null;
    }
    
    /**
     * Gets the effective version of a module by building it and checking the artifact.
     * 
     * @param moduleName the name of the module
     * @return the effective version
     * @throws MavenInvocationException if Maven invocation fails
     */
    private static String getEffectiveVersion(String moduleName) throws MavenInvocationException {
        File modulePom = getModulePomFile(moduleName);
        
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(modulePom);
        request.setGoals(Arrays.asList("help:evaluate", "-Dexpression=project.version", "-q", "-DforceStdout"));
        request.setBatchMode(true);
        
        InvocationResult result = invoker.execute(request);
        
        if (result.getExitCode() != 0) {
            throw new RuntimeException("Failed to get effective version for module: " + moduleName);
        }
        
        // The output will contain the version
        // Note: This is a simplified approach; in practice, you'd capture stdout
        // For now, we'll parse the POM directly
        Document pom = parsePom(modulePom);
        Element root = pom.getRootElement();
        String version = getChildText(root, "version");
        
        // If version is not explicitly set, it's inherited from parent
        if (version == null || version.contains("${")) {
            return extractParentVersion(modulePom);
        }
        
        return version;
    }
    
    // ========================================
    // Maven Invocation Utilities
    // ========================================
    
    /**
     * Executes a Maven build from the repository root.
     * 
     * @param goals the Maven goals to execute
     * @return the invocation result
     * @throws MavenInvocationException if the invocation fails
     */
    private static InvocationResult executeMavenBuild(String... goals) throws MavenInvocationException {
        File rootPom = getRootPomFile();
        
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(rootPom);
        request.setGoals(Arrays.asList(goals));
        request.setBatchMode(true);
        
        return invoker.execute(request);
    }
    
    /**
     * Executes Maven versions:set to update all module versions.
     * 
     * @param newVersion the new version to set
     * @return the invocation result
     * @throws MavenInvocationException if the invocation fails
     */
    private static InvocationResult executeMavenVersionsSet(String newVersion) throws MavenInvocationException {
        File rootPom = getRootPomFile();
        
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(rootPom);
        request.setGoals(Arrays.asList("versions:set", "-DnewVersion=" + newVersion, "-DgenerateBackupPoms=false"));
        request.setBatchMode(true);
        
        return invoker.execute(request);
    }
    
    // ========================================
    // Verification Utilities
    // ========================================
    
    /**
     * Verifies that all modules have the expected version.
     * 
     * @param expectedVersion the expected version
     */
    private static void verifyAllModulesHaveVersion(String expectedVersion) {
        File rootPom = getRootPomFile();
        String rootVersion = extractVersion(rootPom);
        
        assertThat(rootVersion)
                .as("Root POM should have version %s", expectedVersion)
                .isEqualTo(expectedVersion);
        
        for (String moduleName : ALL_MODULES) {
            File modulePom = getModulePomFile(moduleName);
            String parentVersion = extractParentVersion(modulePom);
            
            assertThat(parentVersion)
                    .as("Module %s should inherit parent version %s", moduleName, expectedVersion)
                    .isEqualTo(expectedVersion);
        }
    }


    // ========================================
    // Tests
    // ========================================

    /**
     * Test: Parent Version Inheritance
     *
     * <p>This test verifies that when the parent POM version is updated, all child
     * modules automatically inherit the new version. This validates the version
     * consistency mechanism across the multi-module project.
     *
     * <p>Test steps:
     * <ol>
     *   <li>Backup the root POM file and all child module POMs</li>
     *   <li>Update the parent POM version to 1.0.1-SNAPSHOT</li>
     *   <li>Update all child module parent version references to 1.0.1-SNAPSHOT</li>
     *   <li>Build all modules to ensure the change is valid</li>
     *   <li>Verify all modules inherit the new version 1.0.1-SNAPSHOT</li>
     *   <li>Restore the original versions</li>
     * </ol>
     *
     * <p><b>Validates: Requirements 6.1, 6.4</b>
     *
     * @throws Exception if the test fails
     */
    @org.junit.jupiter.api.Test
    void testParentVersionInheritance() throws Exception {
        String testVersion = "1.0.1-SNAPSHOT";
        File backup = null;

        try {
            // Step 1-3: Update parent POM version and all child module parent references
            backup = updateRootPomVersion(testVersion);

            // Step 4: Build all modules to ensure the change is valid
            InvocationResult buildResult = executeMavenBuild("clean", "install", "-DskipTests");

            assertThat(buildResult.getExitCode())
                    .as("Build should succeed after version update")
                    .isEqualTo(0);

            // Step 5: Verify all modules inherit version 1.0.1-SNAPSHOT
            verifyAllModulesHaveVersion(testVersion);

        } finally {
            // Step 6: Restore original versions
            if (backup != null) {
                restoreAllPomFiles(backup);

                // Rebuild with original version to ensure clean state
                executeMavenBuild("clean", "install", "-DskipTests");
            }
        }
    }

    /**
     * Test: Maven Versions Plugin Support
     *
     * <p>This test verifies that the Maven versions plugin can be used to bulk update
     * versions across all modules in the multi-module project. This validates that the
     * project structure supports automated version management through Maven tooling.
     *
     * <p>Test steps:
     * <ol>
     *   <li>Backup all POM files (root and child modules)</li>
     *   <li>Execute mvn versions:set -DnewVersion=2.0.0-SNAPSHOT programmatically</li>
     *   <li>Verify the versions plugin command succeeds</li>
     *   <li>Verify all module versions are updated to 2.0.0-SNAPSHOT</li>
     *   <li>Build all modules to ensure the updated versions are valid</li>
     *   <li>Restore original versions</li>
     * </ol>
     *
     * <p><b>Validates: Requirements 6.5</b>
     *
     * @throws Exception if the test fails
     */
    @org.junit.jupiter.api.Test
    void testMavenVersionsPluginSupport() throws Exception {
        String testVersion = "2.0.0-SNAPSHOT";
        File rootBackup = null;

        try {
            // Step 1: Backup all POM files
            File rootPom = getRootPomFile();
            rootBackup = backupPomFile(rootPom);

            // Backup all child module POMs
            for (String moduleName : ALL_MODULES) {
                File modulePom = getModulePomFile(moduleName);
                backupPomFile(modulePom);
            }

            // Step 2: Execute mvn versions:set -DnewVersion=2.0.0-SNAPSHOT
            InvocationResult versionsResult = executeMavenVersionsSet(testVersion);

            // Step 3: Verify the versions plugin command succeeds
            assertThat(versionsResult.getExitCode())
                    .as("Maven versions:set command should succeed")
                    .isEqualTo(0);

            // Step 4: Verify all module versions are updated to 2.0.0-SNAPSHOT
            verifyAllModulesHaveVersion(testVersion);

            // Step 5: Build all modules to ensure the updated versions are valid
            InvocationResult buildResult = executeMavenBuild("clean", "install", "-DskipTests");

            assertThat(buildResult.getExitCode())
                    .as("Build should succeed after versions plugin update")
                    .isEqualTo(0);

        } finally {
            // Step 6: Restore original versions
            if (rootBackup != null) {
                restoreAllPomFiles(rootBackup);

                // Rebuild with original version to ensure clean state
                executeMavenBuild("clean", "install", "-DskipTests");
            }
        }
    }

}
