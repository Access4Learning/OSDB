// SPDX-License-Identifier: MIT
package org.sifunity.broker.build;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example-based tests for Maven POM structure validation.
 * 
 * <p>This test class validates the Maven aggregator/parent POM structure
 * using example-based testing. It verifies specific aspects of the root POM
 * including module lists, version properties, plugin configurations, and
 * deployment profiles.
 * 
 * <p>XML parsing is performed using JDOM2 for robust POM file analysis.
 * 
 * <p><b>Validates: Requirement 7.1</b>
 * 
 * @since 1.0.0
 */
public class PomStructureTest {
    
    // ========================================
    // Constants
    // ========================================
    
    private static final String ROOT_POM_PATH = "../pom.xml";
    private static final String EXPECTED_GROUP_ID = "org.sifunity.broker";
    private static final String EXPECTED_ARTIFACT_ID = "sif-unity-broker";
    private static final String EXPECTED_VERSION = "1.0.0-SNAPSHOT";
    
    private static Document rootPom;
    
    // ========================================
    // Setup
    // ========================================
    
    @BeforeAll
    static void setUp() {
        rootPom = parsePom(new File(ROOT_POM_PATH));
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
     * Gets all child elements with a specific name.
     * 
     * @param parent the parent element
     * @param childName the name of the child elements
     * @return list of child elements
     */
    private static List<Element> getChildren(Element parent, String childName) {
        return parent.getChildren(childName, parent.getNamespace());
    }
    
    /**
     * Extracts the list of modules from the root POM.
     * 
     * @param pom the POM document
     * @return list of module names
     */
    private static List<String> extractModules(Document pom) {
        Element root = pom.getRootElement();
        Element modulesElement = getChild(root, "modules");
        
        if (modulesElement == null) {
            return List.of();
        }
        
        return getChildren(modulesElement, "module").stream()
                .map(Element::getTextTrim)
                .collect(Collectors.toList());
    }
    
    /**
     * Extracts properties from the POM.
     * 
     * @param pom the POM document
     * @return the properties element, or null if not found
     */
    private static Element extractProperties(Document pom) {
        Element root = pom.getRootElement();
        return getChild(root, "properties");
    }
    
    /**
     * Extracts plugin management section from the POM.
     * 
     * @param pom the POM document
     * @return the pluginManagement element, or null if not found
     */
    private static Element extractPluginManagement(Document pom) {
        Element root = pom.getRootElement();
        Element build = getChild(root, "build");
        if (build == null) {
            return null;
        }
        return getChild(build, "pluginManagement");
    }
    
    /**
     * Extracts profiles from the POM.
     * 
     * @param pom the POM document
     * @return list of profile elements
     */
    private static List<Element> extractProfiles(Document pom) {
        Element root = pom.getRootElement();
        Element profilesElement = getChild(root, "profiles");
        
        if (profilesElement == null) {
            return List.of();
        }
        
        return getChildren(profilesElement, "profile");
    }
    
    /**
     * Finds a plugin in the plugin management section by artifactId.
     * 
     * @param pluginManagement the pluginManagement element
     * @param artifactId the plugin artifactId to find
     * @return the plugin element, or null if not found
     */
    private static Element findPlugin(Element pluginManagement, String artifactId) {
        if (pluginManagement == null) {
            return null;
        }
        
        Element plugins = getChild(pluginManagement, "plugins");
        if (plugins == null) {
            return null;
        }
        
        return getChildren(plugins, "plugin").stream()
                .filter(plugin -> artifactId.equals(getChildText(plugin, "artifactId")))
                .findFirst()
                .orElse(null);
    }
    
    // ========================================
    // Tests
    // ========================================
    
    // These tests will be implemented in subsequent tasks (5.2-5.5)


    // ========================================
    // Module List Tests
    // ========================================

    /**
     * Test: Root POM Contains All Service Modules
     *
     * <p>Verifies that the root POM's modules section contains exactly 11 modules
     * in the correct order, with sif-broker-common first and integration-tests last.
     * Also verifies that bruno-tests is NOT included in the modules list.
     *
     * <p><b>Validates: Requirements 1.2, 1.3, 5.4, 8.1</b>
     */
    @Test
    void rootPomContainsCorrectModuleList() {
        List<String> modules = extractModules(rootPom);

        // Verify exactly 11 modules (12 total minus observability which is excluded)
        assertThat(modules)
                .as("Root POM should contain exactly 11 modules")
                .hasSize(11);

        // Verify all expected modules are present in exact order
        assertThat(modules)
                .as("Root POM should contain all service modules in correct order")
                .containsExactly(
                        "sif-broker-common",      // Shared library - built first
                        "environment-service",    // Core services
                        "queue-service",
                        "subscription-service",
                        "job-service",
                        "gateway",                // Gateway
                        "events-connector",       // Connector services
                        "requests-connector",
                        "responses-connector",
                        "services-connector",
                        "integration-tests"       // Integration tests - built last
                );

        // Verify bruno-tests is NOT in the modules list
        assertThat(modules)
                .as("bruno-tests should NOT be in the modules list")
                .doesNotContain("bruno-tests");

        // Verify sif-broker-common is first
        assertThat(modules.get(0))
                .as("sif-broker-common should be the first module")
                .isEqualTo("sif-broker-common");

        // Verify integration-tests is last
        assertThat(modules.get(modules.size() - 1))
                .as("integration-tests should be the last module")
                .isEqualTo("integration-tests");
    }


    // ========================================
    // Version Properties Tests
    // ========================================

    /**
     * Test: Root POM Version Properties
     *
     * <p>Verifies that the root POM defines all required version properties
     * for Java, internal modules, and shared dependencies. This ensures
     * centralized version management across all modules.
     *
     * <p><b>Validates: Requirements 2.1, 2.2, 2.4</b>
     */
    @Test
    void rootPomDefinesRequiredVersionProperties() {
        Element properties = extractProperties(rootPom);

        assertThat(properties)
                .as("Root POM should have a properties section")
                .isNotNull();

        // Verify Java version is 17
        String javaVersion = getChildText(properties, "java.version");
        assertThat(javaVersion)
                .as("java.version should be set to 17")
                .isEqualTo("17");

        // Verify internal module version property exists
        String sifBrokerCommonVersion = getChildText(properties, "sif-broker-common.version");
        assertThat(sifBrokerCommonVersion)
                .as("sif-broker-common.version property should be defined")
                .isNotNull()
                .isNotEmpty();

        // Verify core dependency version properties exist
        String lombokVersion = getChildText(properties, "lombok.version");
        assertThat(lombokVersion)
                .as("lombok.version property should be defined")
                .isNotNull()
                .isNotEmpty();

        String jqwikVersion = getChildText(properties, "jqwik.version");
        assertThat(jqwikVersion)
                .as("jqwik.version property should be defined")
                .isNotNull()
                .isNotEmpty();

        String testcontainersVersion = getChildText(properties, "testcontainers.version");
        assertThat(testcontainersVersion)
                .as("testcontainers.version property should be defined")
                .isNotNull()
                .isNotEmpty();

        // Verify additional required dependency version properties
        String assertjVersion = getChildText(properties, "assertj.version");
        assertThat(assertjVersion)
                .as("assertj.version property should be defined")
                .isNotNull()
                .isNotEmpty();

        String awsSdkVersion = getChildText(properties, "aws-sdk.version");
        assertThat(awsSdkVersion)
                .as("aws-sdk.version property should be defined")
                .isNotNull()
                .isNotEmpty();

        String resilience4jVersion = getChildText(properties, "resilience4j.version");
        assertThat(resilience4jVersion)
                .as("resilience4j.version property should be defined")
                .isNotNull()
                .isNotEmpty();

        String opentelemetryVersion = getChildText(properties, "opentelemetry.version");
        assertThat(opentelemetryVersion)
                .as("opentelemetry.version property should be defined")
                .isNotNull()
                .isNotEmpty();

        String micrometerVersion = getChildText(properties, "micrometer.version");
        assertThat(micrometerVersion)
                .as("micrometer.version property should be defined")
                .isNotNull()
                .isNotEmpty();

        String wiremockVersion = getChildText(properties, "wiremock.version");
        assertThat(wiremockVersion)
                .as("wiremock.version property should be defined")
                .isNotNull()
                .isNotEmpty();

        String restAssuredVersion = getChildText(properties, "rest-assured.version");
        assertThat(restAssuredVersion)
                .as("rest-assured.version property should be defined")
                .isNotNull()
                .isNotEmpty();

        String awaitilityVersion = getChildText(properties, "awaitility.version");
        assertThat(awaitilityVersion)
                .as("awaitility.version property should be defined")
                .isNotNull()
                .isNotEmpty();
    }



    // ========================================
    // Plugin Configuration Tests
    // ========================================

    /**
     * Test: Root POM Plugin Configurations
     *
     * <p>Verifies that the root POM's pluginManagement section contains all required
     * plugin configurations with correct settings:
     * - maven-compiler-plugin configured with Java 17
     * - maven-surefire-plugin includes test patterns: *Test.java, *Tests.java, *Properties.java
     * - spring-boot-maven-plugin is configured
     * - UTF-8 encoding properties are set
     *
     * <p><b>Validates: Requirements 3.1, 3.2, 3.3, 3.5, 7.2</b>
     */
    @Test
    void rootPomConfiguresRequiredPlugins() {
        Element pluginManagement = extractPluginManagement(rootPom);

        assertThat(pluginManagement)
                .as("Root POM should have a pluginManagement section")
                .isNotNull();

        // ===== Maven Compiler Plugin =====
        Element compilerPlugin = findPlugin(pluginManagement, "maven-compiler-plugin");
        assertThat(compilerPlugin)
                .as("maven-compiler-plugin should be configured in pluginManagement")
                .isNotNull();

        // Verify Java 17 configuration
        Element compilerConfig = getChild(compilerPlugin, "configuration");
        assertThat(compilerConfig)
                .as("maven-compiler-plugin should have configuration section")
                .isNotNull();

        String source = getChildText(compilerConfig, "source");
        String target = getChildText(compilerConfig, "target");
        assertThat(source)
                .as("maven-compiler-plugin source should be set to Java 17")
                .isEqualTo("${java.version}");
        assertThat(target)
                .as("maven-compiler-plugin target should be set to Java 17")
                .isEqualTo("${java.version}");

        // Verify UTF-8 encoding
        String encoding = getChildText(compilerConfig, "encoding");
        assertThat(encoding)
                .as("maven-compiler-plugin should use UTF-8 encoding")
                .isEqualTo("${project.build.sourceEncoding}");

        // ===== Maven Surefire Plugin =====
        Element surefirePlugin = findPlugin(pluginManagement, "maven-surefire-plugin");
        assertThat(surefirePlugin)
                .as("maven-surefire-plugin should be configured in pluginManagement")
                .isNotNull();

        // Verify test patterns
        Element surefireConfig = getChild(surefirePlugin, "configuration");
        assertThat(surefireConfig)
                .as("maven-surefire-plugin should have configuration section")
                .isNotNull();

        Element includes = getChild(surefireConfig, "includes");
        assertThat(includes)
                .as("maven-surefire-plugin should have includes section")
                .isNotNull();

        List<String> includePatterns = getChildren(includes, "include").stream()
                .map(Element::getTextTrim)
                .collect(Collectors.toList());

        assertThat(includePatterns)
                .as("maven-surefire-plugin should include standard test patterns")
                .contains("**/*Test.java", "**/*Tests.java", "**/*Properties.java");

        // Verify UTF-8 encoding for surefire
        String surefireEncoding = getChildText(surefireConfig, "encoding");
        assertThat(surefireEncoding)
                .as("maven-surefire-plugin should use UTF-8 encoding")
                .isEqualTo("${project.build.sourceEncoding}");

        // ===== Spring Boot Maven Plugin =====
        Element springBootPlugin = findPlugin(pluginManagement, "spring-boot-maven-plugin");
        assertThat(springBootPlugin)
                .as("spring-boot-maven-plugin should be configured in pluginManagement")
                .isNotNull();

        // Verify plugin has configuration (even if minimal)
        Element springBootConfig = getChild(springBootPlugin, "configuration");
        assertThat(springBootConfig)
                .as("spring-boot-maven-plugin should have configuration section")
                .isNotNull();

        // ===== UTF-8 Encoding Properties =====
        Element properties = extractProperties(rootPom);
        String sourceEncoding = getChildText(properties, "project.build.sourceEncoding");
        String reportingEncoding = getChildText(properties, "project.reporting.outputEncoding");

        assertThat(sourceEncoding)
                .as("project.build.sourceEncoding should be set to UTF-8")
                .isEqualTo("UTF-8");
        assertThat(reportingEncoding)
                .as("project.reporting.outputEncoding should be set to UTF-8")
                .isEqualTo("UTF-8");
    }


    // ========================================
    // Deployment Profiles Tests
    // ========================================

    /**
     * Test: Root POM Deployment Profiles
     *
     * <p>Verifies that the root POM defines deployment profiles for different environments:
     * - dev profile exists and is active by default
     * - staging profile exists
     * - production profile exists
     * - skip-integration-tests profile exists
     *
     * <p><b>Validates: Requirements 10.2</b>
     */
    @Test
    void rootPomDefinesDeploymentProfiles() {
        List<Element> profiles = extractProfiles(rootPom);

        assertThat(profiles)
                .as("Root POM should have profiles defined")
                .isNotEmpty();

        // Extract profile IDs
        List<String> profileIds = profiles.stream()
                .map(profile -> getChildText(profile, "id"))
                .collect(Collectors.toList());

        // Verify all required profiles exist
        assertThat(profileIds)
                .as("Root POM should define dev, staging, production, and skip-integration-tests profiles")
                .contains("dev", "staging", "production", "skip-integration-tests");

        // ===== Verify dev profile is active by default =====
        Element devProfile = profiles.stream()
                .filter(profile -> "dev".equals(getChildText(profile, "id")))
                .findFirst()
                .orElse(null);

        assertThat(devProfile)
                .as("dev profile should exist")
                .isNotNull();

        Element devActivation = getChild(devProfile, "activation");
        assertThat(devActivation)
                .as("dev profile should have activation section")
                .isNotNull();

        String activeByDefault = getChildText(devActivation, "activeByDefault");
        assertThat(activeByDefault)
                .as("dev profile should be active by default")
                .isEqualTo("true");

        // ===== Verify staging profile exists =====
        Element stagingProfile = profiles.stream()
                .filter(profile -> "staging".equals(getChildText(profile, "id")))
                .findFirst()
                .orElse(null);

        assertThat(stagingProfile)
                .as("staging profile should exist")
                .isNotNull();

        // ===== Verify production profile exists =====
        Element productionProfile = profiles.stream()
                .filter(profile -> "production".equals(getChildText(profile, "id")))
                .findFirst()
                .orElse(null);

        assertThat(productionProfile)
                .as("production profile should exist")
                .isNotNull();

        // ===== Verify skip-integration-tests profile exists =====
        Element skipITsProfile = profiles.stream()
                .filter(profile -> "skip-integration-tests".equals(getChildText(profile, "id")))
                .findFirst()
                .orElse(null);

        assertThat(skipITsProfile)
                .as("skip-integration-tests profile should exist")
                .isNotNull();
    }




}
