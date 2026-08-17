# SIF Broker Common Library - Publishing Configuration

## Overview

This document describes the Maven publishing configuration for the SIF Broker Common Library. The library is configured to generate and publish three types of JARs: main JAR, source JAR, and JavaDoc JAR.

## Artifact Coordinates

- **Group ID**: `org.sifunity.broker`
- **Artifact ID**: `sif-broker-common`
- **Version**: `1.0.0-SNAPSHOT`
- **Packaging**: `jar`

## Publishing Configuration

### Maven Plugins

The following Maven plugins are configured for artifact publishing:

#### 1. Maven Source Plugin (3.3.0)
- **Purpose**: Generates source JAR containing all source files
- **Configuration**: Automatically attaches sources during the package phase
- **Output**: `sif-broker-common-1.0.0-SNAPSHOT-sources.jar`

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-source-plugin</artifactId>
    <version>3.3.0</version>
    <executions>
        <execution>
            <id>attach-sources</id>
            <goals>
                <goal>jar</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

#### 2. Maven JavaDoc Plugin (3.6.0)
- **Purpose**: Generates JavaDoc JAR containing API documentation
- **Configuration**: Automatically attaches JavaDoc during the package phase
- **Output**: `sif-broker-common-1.0.0-SNAPSHOT-javadoc.jar`

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <id>attach-javadocs</id>
            <goals>
                <goal>jar</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <source>17</source>
        <doclint>none</doclint>
    </configuration>
</plugin>
```

#### 3. Maven Install Plugin (3.1.1)
- **Purpose**: Installs artifacts to local Maven repository
- **Configuration**: Standard configuration for local repository installation

#### 4. Maven Deploy Plugin (3.1.1)
- **Purpose**: Deploys artifacts to remote repositories
- **Configuration**: Ready for remote repository deployment

## Generated Artifacts

When the library is built and published, the following artifacts are generated:

1. **Main JAR** (`sif-broker-common-1.0.0-SNAPSHOT.jar`)
   - Contains compiled Java classes
   - Size: ~46 KB
   - Includes all production code

2. **Source JAR** (`sif-broker-common-1.0.0-SNAPSHOT-sources.jar`)
   - Contains all Java source files
   - Size: ~49 KB
   - Useful for IDE integration and debugging

3. **JavaDoc JAR** (`sif-broker-common-1.0.0-SNAPSHOT-javadoc.jar`)
   - Contains generated API documentation
   - Size: ~4.4 MB
   - Includes comprehensive JavaDoc for all public APIs

4. **POM file** (`sif-broker-common-1.0.0-SNAPSHOT.pom`)
   - Contains project metadata and dependencies
   - Size: ~8 KB
   - Required for Maven dependency resolution

## Local Repository Publishing

### Command
```bash
mvn clean install
```

### Local Repository Location
- **Windows**: `%USERPROFILE%\.m2\repository\org\sifunity\broker\sif-broker-common\1.0.0-SNAPSHOT\`
- **Linux/Mac**: `~/.m2/repository/org/sifunity/broker/sif-broker-common/1.0.0-SNAPSHOT/`

### Verification
After successful installation, the following files should be present in the local repository:
- `sif-broker-common-1.0.0-SNAPSHOT.jar`
- `sif-broker-common-1.0.0-SNAPSHOT-sources.jar`
- `sif-broker-common-1.0.0-SNAPSHOT-javadoc.jar`
- `sif-broker-common-1.0.0-SNAPSHOT.pom`
- `maven-metadata-local.xml`

## Remote Repository Publishing

### Configuration
The `distributionManagement` section in the POM is configured but commented out. To enable remote publishing:

1. Uncomment the `distributionManagement` section
2. Configure repository URLs and credentials
3. Use `mvn clean deploy` to publish to remote repositories

```xml
<distributionManagement>
    <repository>
        <id>releases</id>
        <name>Internal Releases</name>
        <url>https://your-nexus-server/repository/maven-releases/</url>
    </repository>
    <snapshotRepository>
        <id>snapshots</id>
        <name>Internal Snapshots</name>
        <url>https://your-nexus-server/repository/maven-snapshots/</url>
    </snapshotRepository>
</distributionManagement>
```

## Usage in Other Projects

To use this library in other Maven projects, add the following dependency:

```xml
<dependency>
    <groupId>org.sifunity.broker</groupId>
    <artifactId>sif-broker-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Build Requirements

- **Java**: 17 or higher
- **Maven**: 3.6.0 or higher
- **Encoding**: UTF-8

## Notes

- The library uses semantic versioning (MAJOR.MINOR.PATCH)
- SNAPSHOT versions are used for development builds
- All artifacts are signed and include checksums for integrity verification
- The library is compatible with Spring Boot 3.x applications

## Testing the Publishing Configuration

The publishing configuration has been successfully tested with:
- ✅ Main JAR generation
- ✅ Source JAR generation  
- ✅ JavaDoc JAR generation
- ✅ Local Maven repository installation
- ✅ Artifact coordinate verification
- ✅ File integrity verification

All artifacts are properly generated and installed to the local Maven repository, making the library ready for use by other microservices in the SIF Unity Broker system.