// SPDX-License-Identifier: MIT
package org.sifunity.broker.build;

import net.jqwik.api.*;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for Maven POM structure validation.
 * 
 * <p>This test class validates the Maven aggregator/parent POM structure
 * using property-based testing with jqwik. It verifies that all child modules
 * follow consistent patterns for parent references, dependency management,
 * and Maven coordinates.
 * 
 * <p>XML parsing is performed using JDOM2 for robust POM file analysis.
 * 
 * @since 1.0.0
 */
public class PomStructureProperties {
    
    // ========================================
    // Constants
    // ========================================
    
    private static final String ROOT_POM_PATH = "pom.xml";
    private static final String EXPECTED_GROUP_ID = "org.sifunity.broker";
    private static final String EXPECTED_PARENT_ARTIFACT_ID = "sif-unity-broker";
    private static final String EXPECTED_RELATIVE_PATH = "../pom.xml";
    
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
    private Document parsePom(File pomFile) {
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
    private String getChildText(Element parent, String childName) {
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
    private Element getChild(Element parent, String childName) {
        return parent.getChild(childName, parent.getNamespace());
    }
    
    /**
     * Gets all child elements with a specific name.
     * 
     * @param parent the parent element
     * @param childName the name of the child elements
     * @return list of child elements
     */
    private List<Element> getChildren(Element parent, String childName) {
        return parent.getChildren(childName, parent.getNamespace());
    }
    
    /**
     * Extracts the list of modules from the root POM.
     * 
     * @param rootPom the root POM document
     * @return list of module names
     */
    private List<String> extractModules(Document rootPom) {
        Element root = rootPom.getRootElement();
        Element modulesElement = getChild(root, "modules");
        
        if (modulesElement == null) {
            return List.of();
        }
        
        return getChildren(modulesElement, "module").stream()
                .map(Element::getTextTrim)
                .collect(Collectors.toList());
    }
    
    /**
     * Extracts managed dependencies from the root POM.
     * 
     * @param rootPom the root POM document
     * @return list of managed dependencies
     */
    private List<ManagedDependency> extractManagedDependencies(Document rootPom) {
        Element root = rootPom.getRootElement();
        Element depMgmt = getChild(root, "dependencyManagement");
        
        if (depMgmt == null) {
            return List.of();
        }
        
        Element dependencies = getChild(depMgmt, "dependencies");
        if (dependencies == null) {
            return List.of();
        }
        
        return getChildren(dependencies, "dependency").stream()
                .map(dep -> new ManagedDependency(
                        getChildText(dep, "groupId"),
                        getChildText(dep, "artifactId")
                ))
                .collect(Collectors.toList());
    }
    
    /**
     * Extracts dependencies from a child POM.
     * 
     * @param childPom the child POM document
     * @return list of dependencies
     */
    private List<Element> extractDependencies(Document childPom) {
        Element root = childPom.getRootElement();
        Element dependencies = getChild(root, "dependencies");
        
        if (dependencies == null) {
            return List.of();
        }
        
        return getChildren(dependencies, "dependency");
    }
    
    /**
     * Checks if a dependency element matches a managed dependency.
     * 
     * @param depElement the dependency element
     * @param managedDep the managed dependency
     * @return true if they match
     */
    private boolean matches(Element depElement, ManagedDependency managedDep) {
        String groupId = getChildText(depElement, "groupId");
        String artifactId = getChildText(depElement, "artifactId");
        
        return managedDep.groupId().equals(groupId) && 
               managedDep.artifactId().equals(artifactId);
    }
    
    // ========================================
    // Data Models
    // ========================================
    
    /**
     * Represents a managed dependency with groupId and artifactId.
     */
    private record ManagedDependency(String groupId, String artifactId) {}
    
    // ========================================
    // Arbitraries (Test Data Generators)
    // ========================================
    
    /**
     * Provides child module POM files for property-based testing.
     * 
     * @return arbitrary of child module POM files
     */
    @Provide
    Arbitrary<File> childModulePoms() {
        Document rootPom = parsePom(new File(ROOT_POM_PATH));
        List<String> modules = extractModules(rootPom);
        
        List<File> childPoms = modules.stream()
                .map(module -> new File(module + "/pom.xml"))
                .filter(File::exists)
                .collect(Collectors.toList());
        
        return Arbitraries.of(childPoms);
    }
    
    /**
     * Provides all module POM files (including root) for property-based testing.
     * 
     * @return arbitrary of all module POM files
     */
    @Provide
    Arbitrary<File> allModulePoms() {
        Document rootPom = parsePom(new File(ROOT_POM_PATH));
        List<String> modules = extractModules(rootPom);
        
        List<File> allPoms = modules.stream()
                .map(module -> new File(module + "/pom.xml"))
                .filter(File::exists)
                .collect(Collectors.toList());
        
        // Add root POM
        allPoms.add(0, new File(ROOT_POM_PATH));
        
        return Arbitraries.of(allPoms);
    }
    
    /**
     * Provides managed dependencies from the root POM.
     * 
     * @return arbitrary of managed dependencies
     */
    @Provide
    Arbitrary<ManagedDependency> managedDependencies() {
        Document rootPom = parsePom(new File(ROOT_POM_PATH));
        List<ManagedDependency> managed = extractManagedDependencies(rootPom);
        
        return Arbitraries.of(managed);
    }
    
    // ========================================
    // Property-Based Tests
    // ========================================
    
    /**
     * Property 1: Parent Reference Consistency
     * 
     * <p>For all child module POMs in the project, each module SHALL declare 
     * the root POM as its parent with groupId "org.sifunity.broker", 
     * artifactId "sif-unity-broker", and relativePath "../pom.xml".
     * 
     * <p><b>Validates: Requirements 7.1, 7.2</b>
     */
    @Property(tries = 100)
    @Tag("maven-aggregator-pom")
    @Tag("property-1")
    void allChildModulesHaveCorrectParentReference(@ForAll("childModulePoms") File pomFile) {
        Document pom = parsePom(pomFile);
        Element root = pom.getRootElement();
        Element parent = getChild(root, "parent");
        
        assertThat(parent)
                .as("Child module %s must have a parent element", pomFile.getPath())
                .isNotNull();
        
        String groupId = getChildText(parent, "groupId");
        String artifactId = getChildText(parent, "artifactId");
        String relativePath = getChildText(parent, "relativePath");
        
        assertThat(groupId)
                .as("Parent groupId in %s", pomFile.getPath())
                .isEqualTo(EXPECTED_GROUP_ID);
        
        assertThat(artifactId)
                .as("Parent artifactId in %s", pomFile.getPath())
                .isEqualTo(EXPECTED_PARENT_ARTIFACT_ID);
        
        assertThat(relativePath)
                .as("Parent relativePath in %s", pomFile.getPath())
                .isEqualTo(EXPECTED_RELATIVE_PATH);
    }
    
    /**
     * Property 2: Managed Dependency Version Omission
     * 
     * <p>For all child module POMs and for all dependencies declared in the 
     * parent's dependencyManagement section, if a child module declares that 
     * dependency, it SHALL NOT specify a version tag.
     * 
     * <p><b>Validates: Requirements 7.1, 7.2</b>
     */
    @Property(tries = 100)
    @Tag("maven-aggregator-pom")
    @Tag("property-2")
    void childModulesOmitVersionsForManagedDependencies(
            @ForAll("childModulePoms") File pomFile,
            @ForAll("managedDependencies") ManagedDependency managedDep) {
        
        Document childPom = parsePom(pomFile);
        List<Element> dependencies = extractDependencies(childPom);
        
        Optional<Element> matchingDep = dependencies.stream()
                .filter(dep -> matches(dep, managedDep))
                .findFirst();
        
        if (matchingDep.isPresent()) {
            Element version = getChild(matchingDep.get(), "version");
            
            assertThat(version)
                    .as("Dependency %s:%s in %s should not have a version tag (managed by parent)",
                            managedDep.groupId(), managedDep.artifactId(), pomFile.getPath())
                    .isNull();
        }
    }
    
    /**
     * Property 3: Maven Coordinate Consistency
     * 
     * <p>For all modules in the project, each module SHALL use the groupId 
     * "org.sifunity.broker" and SHALL either inherit the parent version or 
     * explicitly use "${project.parent.version}".
     * 
     * <p><b>Validates: Requirements 7.1, 7.2</b>
     */
    @Property(tries = 100)
    @Tag("maven-aggregator-pom")
    @Tag("property-3")
    void allModulesUseConsistentMavenCoordinates(@ForAll("allModulePoms") File pomFile) {
        Document pom = parsePom(pomFile);
        Element root = pom.getRootElement();
        
        // Check groupId
        String groupId = getChildText(root, "groupId");
        if (groupId == null) {
            // Inherited from parent
            Element parent = getChild(root, "parent");
            if (parent != null) {
                groupId = getChildText(parent, "groupId");
            }
        }
        
        assertThat(groupId)
                .as("GroupId in %s", pomFile.getPath())
                .isEqualTo(EXPECTED_GROUP_ID);
        
        // Check version consistency
        String version = getChildText(root, "version");
        if (version != null && !version.equals("${project.parent.version}")) {
            // If version is explicitly set, it should match parent
            Document parentPom = parsePom(new File(ROOT_POM_PATH));
            String parentVersion = getChildText(parentPom.getRootElement(), "version");
            
            assertThat(version)
                    .as("Version in %s should match parent version or use ${project.parent.version}", 
                            pomFile.getPath())
                    .isEqualTo(parentVersion);
        }
    }
}
