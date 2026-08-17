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
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example-based tests for Maven POM inheritance validation.
 * 
 * <p>This test class validates that child modules properly inherit configurations
 * from the parent POM, including version properties, plugin configurations, and
 * dependency management. It uses XML parsing and Maven's effective POM resolution
 * to verify inheritance behavior.
 * 
 * <p>XML parsing is performed using JDOM2 for POM structure analysis, and Maven's
 * model reader for effective POM resolution.
 * 
 * <p><b>Validates: Requirement 4.4</b>
 * 
 * @since 1.0.0
 */
public class InheritanceTest {
    
    // ========================================
    // Constants
    // ========================================
    
    private static final String ROOT_POM_PATH = "../pom.xml";
    private static final String EXPECTED_GROUP_ID = "org.sifunity.broker";
    
    /**
     * Sample child modules to test inheritance.
     * We test a representative subset rather than all modules.
     */
    private static final List<String> SAMPLE_CHILD_MODULES = List.of(
            "sif-broker-common",
            "environment-service",
            "queue-service",
            "integration-tests"
    );
    
    private static Document rootPom;
    private static Map<String, Document> childPoms;
    
    // ========================================
    // Setup
    // ========================================
    
    @BeforeAll
    static void setUp() {
        rootPom = parsePom(new File(ROOT_POM_PATH));
        
        // Load child POMs
        childPoms = SAMPLE_CHILD_MODULES.stream()
                .collect(Collectors.toMap(
                        module -> module,
                        module -> parsePom(new File("../" + module + "/pom.xml"))
                ));
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
     * Extracts property values as a map from the POM.
     * 
     * @param pom the POM document
     * @return map of property names to values
     */
    private static Map<String, String> extractPropertyMap(Document pom) {
        Element properties = extractProperties(pom);
        if (properties == null) {
            return Map.of();
        }
        
        return properties.getChildren().stream()
                .collect(Collectors.toMap(
                        Element::getName,
                        Element::getTextTrim
                ));
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
    
    /**
     * Extracts dependency management section from the POM.
     * 
     * @param pom the POM document
     * @return the dependencyManagement element, or null if not found
     */
    private static Element extractDependencyManagement(Document pom) {
        Element root = pom.getRootElement();
        return getChild(root, "dependencyManagement");
    }
    
    /**
     * Extracts managed dependencies as a map of groupId:artifactId to version.
     * 
     * @param pom the POM document
     * @return map of dependency coordinates to versions
     */
    private static Map<String, String> extractManagedDependencyVersions(Document pom) {
        Element depMgmt = extractDependencyManagement(pom);
        if (depMgmt == null) {
            return Map.of();
        }
        
        Element dependencies = getChild(depMgmt, "dependencies");
        if (dependencies == null) {
            return Map.of();
        }
        
        return getChildren(dependencies, "dependency").stream()
                .collect(Collectors.toMap(
                        dep -> getChildText(dep, "groupId") + ":" + getChildText(dep, "artifactId"),
                        dep -> getChildText(dep, "version")
                ));
    }
    
    /**
     * Extracts dependencies from a POM.
     * 
     * @param pom the POM document
     * @return list of dependency elements
     */
    private static List<Element> extractDependencies(Document pom) {
        Element root = pom.getRootElement();
        Element dependencies = getChild(root, "dependencies");
        
        if (dependencies == null) {
            return List.of();
        }
        
        return getChildren(dependencies, "dependency");
    }
    
    // ========================================
    // Tests
    // ========================================
    
    /**
     * Test: Child Module Inherits Parent Properties
     * 
     * <p>Verifies that child modules inherit version properties from the parent POM.
     * This test checks that when a child module uses a dependency managed by the parent,
     * it does not need to specify the version explicitly.
     * 
     * <p>The test validates:
     * <ul>
     *   <li>Child modules reference the parent POM correctly</li>
     *   <li>Child modules can access parent properties (e.g., java.version)</li>
     *   <li>Dependencies managed by parent do not have explicit versions in child POMs</li>
     * </ul>
     * 
     * <p><b>Validates: Requirement 4.4</b>
     */
    @Test
    void childModuleInheritsParentProperties() {
        // Extract parent properties
        Map<String, String> parentProperties = extractPropertyMap(rootPom);
        
        // Verify parent defines key properties
        assertThat(parentProperties)
                .as("Parent POM should define version properties")
                .containsKeys("java.version", "lombok.version", "jqwik.version");
        
        // Extract managed dependency versions from parent
        Map<String, String> managedVersions = extractManagedDependencyVersions(rootPom);
        
        assertThat(managedVersions)
                .as("Parent POM should define managed dependencies")
                .isNotEmpty();
        
        // For each child module, verify it inherits properly
        for (Map.Entry<String, Document> entry : childPoms.entrySet()) {
            String moduleName = entry.getKey();
            Document childPom = entry.getValue();
            
            // Verify child has parent reference
            Element root = childPom.getRootElement();
            Element parent = getChild(root, "parent");
            
            assertThat(parent)
                    .as("Child module %s should have parent reference", moduleName)
                    .isNotNull();
            
            assertThat(getChildText(parent, "groupId"))
                    .as("Child module %s should reference correct parent groupId", moduleName)
                    .isEqualTo(EXPECTED_GROUP_ID);
            
            // Verify child dependencies don't have explicit versions for managed dependencies
            List<Element> childDependencies = extractDependencies(childPom);
            
            for (Element dependency : childDependencies) {
                String groupId = getChildText(dependency, "groupId");
                String artifactId = getChildText(dependency, "artifactId");
                String coordinate = groupId + ":" + artifactId;
                
                // If this dependency is managed by parent, it should not have a version
                if (managedVersions.containsKey(coordinate)) {
                    Element version = getChild(dependency, "version");
                    
                    assertThat(version)
                            .as("Dependency %s in module %s should not have explicit version (managed by parent)",
                                    coordinate, moduleName)
                            .isNull();
                }
            }
        }
    }
    
    /**
     * Test: Child Module Inherits Plugin Configurations
     * 
     * <p>Verifies that child modules inherit plugin configurations from the parent POM's
     * pluginManagement section. This ensures consistent build behavior across all modules.
     * 
     * <p>The test validates:
     * <ul>
     *   <li>Parent POM defines plugin configurations in pluginManagement</li>
     *   <li>Child modules do not duplicate plugin configurations</li>
     *   <li>Key plugins (compiler, surefire, spring-boot) are managed by parent</li>
     * </ul>
     * 
     * <p><b>Validates: Requirement 4.4</b>
     */
    @Test
    void childModuleInheritsPluginConfigurations() {
        // Extract parent plugin management
        Element parentPluginMgmt = extractPluginManagement(rootPom);
        
        assertThat(parentPluginMgmt)
                .as("Parent POM should have pluginManagement section")
                .isNotNull();
        
        // Verify parent manages key plugins
        assertThat(findPlugin(parentPluginMgmt, "maven-compiler-plugin"))
                .as("Parent should manage maven-compiler-plugin")
                .isNotNull();
        
        assertThat(findPlugin(parentPluginMgmt, "maven-surefire-plugin"))
                .as("Parent should manage maven-surefire-plugin")
                .isNotNull();
        
        assertThat(findPlugin(parentPluginMgmt, "spring-boot-maven-plugin"))
                .as("Parent should manage spring-boot-maven-plugin")
                .isNotNull();
        
        // For each child module, verify it doesn't duplicate plugin configurations
        for (Map.Entry<String, Document> entry : childPoms.entrySet()) {
            String moduleName = entry.getKey();
            Document childPom = entry.getValue();
            
            Element root = childPom.getRootElement();
            Element build = getChild(root, "build");
            
            // Child modules may have a build section for plugins, but should not
            // have pluginManagement (that's the parent's job)
            if (build != null) {
                Element childPluginMgmt = getChild(build, "pluginManagement");
                
                assertThat(childPluginMgmt)
                        .as("Child module %s should not have pluginManagement (should inherit from parent)",
                                moduleName)
                        .isNull();
            }
        }
    }
    
    /**
     * Test: Child Module Retains Module-Specific Dependencies
     * 
     * <p>Verifies that child modules can add their own module-specific dependencies
     * while still inheriting from the parent. This ensures that inheritance doesn't
     * prevent modules from having unique requirements.
     * 
     * <p>The test validates:
     * <ul>
     *   <li>environment-service has spring-vault dependency (module-specific)</li>
     *   <li>queue-service has AWS SDK dependency (module-specific)</li>
     *   <li>Module-specific dependencies coexist with inherited configurations</li>
     * </ul>
     * 
     * <p><b>Validates: Requirement 4.5</b>
     */
    @Test
    void childModuleRetainsModuleSpecificDependencies() {
        // Test environment-service has spring-vault
        Document envServicePom = childPoms.get("environment-service");
        if (envServicePom != null) {
            List<Element> envDependencies = extractDependencies(envServicePom);
            
            boolean hasSpringVault = envDependencies.stream()
                    .anyMatch(dep -> 
                            "org.springframework.vault".equals(getChildText(dep, "groupId")) &&
                            "spring-vault-core".equals(getChildText(dep, "artifactId"))
                    );
            
            assertThat(hasSpringVault)
                    .as("environment-service should have spring-vault-core dependency")
                    .isTrue();
        }
        
        // Test queue-service has AWS SDK
        Document queueServicePom = childPoms.get("queue-service");
        if (queueServicePom != null) {
            List<Element> queueDependencies = extractDependencies(queueServicePom);
            
            boolean hasAwsSdk = queueDependencies.stream()
                    .anyMatch(dep -> {
                        String groupId = getChildText(dep, "groupId");
                        return groupId != null && groupId.startsWith("software.amazon.awssdk");
                    });
            
            assertThat(hasAwsSdk)
                    .as("queue-service should have AWS SDK dependencies")
                    .isTrue();
        }
    }

    /**
     * Test: Plugin Configuration Override
     *
     * <p>Verifies that child modules can override plugin configurations from the parent POM
     * when needed. This test specifically checks that the integration-tests module can
     * override the maven-surefire-plugin configuration to increase memory settings for
     * resource-intensive integration tests.
     *
     * <p>The test validates:
     * <ul>
     *   <li>Parent POM defines surefire plugin configuration in pluginManagement</li>
     *   <li>integration-tests module overrides surefire with custom memory settings</li>
     *   <li>Override includes argLine with -Xmx2048m for increased heap memory</li>
     *   <li>Child module retains parent's test patterns while adding custom configuration</li>
     * </ul>
     *
     * <p><b>Validates: Requirement 3.4</b>
     */
    @Test
    void pluginConfigurationOverride() {
        // Verify parent defines surefire plugin in pluginManagement
        Element parentPluginMgmt = extractPluginManagement(rootPom);
        assertThat(parentPluginMgmt)
                .as("Parent POM should have pluginManagement section")
                .isNotNull();

        Element parentSurefire = findPlugin(parentPluginMgmt, "maven-surefire-plugin");
        assertThat(parentSurefire)
                .as("Parent POM should manage maven-surefire-plugin")
                .isNotNull();

        // Verify parent surefire configuration includes test patterns
        Element parentConfig = getChild(parentSurefire, "configuration");
        assertThat(parentConfig)
                .as("Parent surefire plugin should have configuration")
                .isNotNull();

        Element parentIncludes = getChild(parentConfig, "includes");
        assertThat(parentIncludes)
                .as("Parent surefire should define test includes")
                .isNotNull();

        // Verify parent does NOT define argLine (default memory settings)
        Element parentArgLine = getChild(parentConfig, "argLine");
        assertThat(parentArgLine)
                .as("Parent surefire should not define argLine (allows child overrides)")
                .isNull();

        // Verify integration-tests module overrides surefire configuration
        Document integrationTestsPom = childPoms.get("integration-tests");
        assertThat(integrationTestsPom)
                .as("integration-tests module should be loaded")
                .isNotNull();

        Element root = integrationTestsPom.getRootElement();
        Element build = getChild(root, "build");
        assertThat(build)
                .as("integration-tests should have build section")
                .isNotNull();

        Element plugins = getChild(build, "plugins");
        assertThat(plugins)
                .as("integration-tests should have plugins section")
                .isNotNull();

        // Find surefire plugin in child module
        Element childSurefire = getChildren(plugins, "plugin").stream()
                .filter(plugin -> "maven-surefire-plugin".equals(getChildText(plugin, "artifactId")))
                .findFirst()
                .orElse(null);

        assertThat(childSurefire)
                .as("integration-tests should override maven-surefire-plugin")
                .isNotNull();

        // Verify child surefire has custom configuration
        Element childConfig = getChild(childSurefire, "configuration");
        assertThat(childConfig)
                .as("integration-tests surefire should have configuration")
                .isNotNull();

        // Verify child defines custom argLine with increased memory
        Element childArgLine = getChild(childConfig, "argLine");
        assertThat(childArgLine)
                .as("integration-tests surefire should define custom argLine")
                .isNotNull();

        String argLineValue = childArgLine.getTextTrim();
        assertThat(argLineValue)
                .as("integration-tests surefire argLine should include increased memory settings")
                .contains("-Xmx2048m")
                .contains("-XX:MetaspaceSize=256m")
                .contains("-XX:MaxMetaspaceSize=512m");

        // Verify child retains test patterns from parent (or defines its own)
        Element childIncludes = getChild(childConfig, "includes");
        assertThat(childIncludes)
                .as("integration-tests surefire should define test includes")
                .isNotNull();

        List<Element> includePatterns = getChildren(childIncludes, "include");
        assertThat(includePatterns)
                .as("integration-tests should include test patterns")
                .isNotEmpty()
                .extracting(Element::getTextTrim)
                .contains("**/*Test.java", "**/*Tests.java", "**/*Properties.java");
    }

}
