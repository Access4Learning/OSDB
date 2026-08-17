// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1: No H2 References in Test Configuration.
 *
 * <p><em>For any</em> test configuration file (Java source with {@code @TestPropertySource},
 * or {@code application-test.yml}) in any module, the file shall not contain the strings
 * {@code org.h2.Driver}, {@code jdbc:h2:mem}, or {@code com.h2database}.</p>
 *
 * <p>This property is verified by scanning all {@code *.java} files under {@code src/test/}
 * and all {@code application-test.yml} files across every module, asserting zero matches
 * for H2-related patterns.</p>
 *
 * <p>Files under {@code .kiro/specs/} directories are excluded since those are documentation,
 * not code. Property test files for this feature are also excluded to avoid false positives
 * from test descriptions mentioning H2.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 2.1, 2.2, 3.1, 3.2</strong></p>
 */
class NoH2ReferencesInTestConfigurationPropertyTest {

    private static final List<String> H2_PATTERNS = List.of(
            "org.h2.Driver",
            "jdbc:h2:mem",
            "com.h2database"
    );

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
    Arbitrary<String> allModules() {
        return Arbitraries.of(
                "queue-service",
                "subscription-service",
                "job-service",
                "environment-service",
                "gateway",
                "events-connector",
                "requests-connector",
                "responses-connector",
                "services-connector",
                "integration-tests",
                "sif-broker-common"
        );
    }

    /**
     * For any module in the project, all {@code *.java} files under {@code src/test/}
     * and all {@code application-test.yml} files shall contain zero references to
     * H2-related patterns: {@code org.h2.Driver}, {@code jdbc:h2:mem}, {@code com.h2database}.
     */
    @Property(tries = 10)
    @Tag("Feature: h2-removal-testcontainers-standardization, Property 1: No H2 References in Test Configuration")
    void noH2ReferencesInTestFiles(
            @ForAll("allModules") String moduleName) throws Exception {

        Path moduleDir = PROJECT_ROOT.resolve(moduleName);
        if (!moduleDir.toFile().exists()) {
            // Module directory doesn't exist — nothing to check
            return;
        }

        List<Path> filesToScan = collectFilesToScan(moduleDir);
        List<String> violations = new ArrayList<>();

        for (Path file : filesToScan) {
            String content = Files.readString(file);
            for (String pattern : H2_PATTERNS) {
                if (content.contains(pattern)) {
                    String relativePath = PROJECT_ROOT.relativize(file).toString();
                    violations.add(relativePath + " contains '" + pattern + "'");
                }
            }
        }

        assertThat(violations)
                .as("Module '%s' must not contain any H2 references in test configuration files. "
                        + "Found %d violation(s)", moduleName, violations.size())
                .isEmpty();
    }

    // ========== Helpers ==========

    /**
     * Collects all files to scan for H2 references within a module:
     * <ul>
     *   <li>All {@code *.java} files under {@code src/test/}</li>
     *   <li>All {@code application-test.yml} files under {@code src/test/resources/}</li>
     * </ul>
     *
     * <p>Excludes files under {@code .kiro/specs/} directories and property test files
     * for this feature to avoid false positives from test descriptions mentioning H2.</p>
     */
    private List<Path> collectFilesToScan(Path moduleDir) throws IOException {
        List<Path> files = new ArrayList<>();

        Path testDir = moduleDir.resolve("src").resolve("test");
        if (!testDir.toFile().exists()) {
            return files;
        }

        Files.walkFileTree(testDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                String filePath = file.toString();

                // Skip files in .kiro/specs/ directories
                if (filePath.contains(".kiro" + File.separator + "specs")
                        || filePath.contains(".kiro/specs")) {
                    return FileVisitResult.CONTINUE;
                }

                // Skip this property test file itself to avoid false positives
                if (fileName.equals("NoH2ReferencesInTestConfigurationPropertyTest.java")) {
                    return FileVisitResult.CONTINUE;
                }

                boolean isJavaFile = fileName.endsWith(".java");
                boolean isTestYml = fileName.equals("application-test.yml");

                if (isJavaFile || isTestYml) {
                    files.add(file);
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirPath = dir.toString();
                // Skip .kiro directories entirely
                if (dirPath.contains(".kiro")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    /**
     * Convenience accessor for the platform-specific file separator,
     * used in path matching.
     */
    private static final class File {
        static final String separator = java.io.File.separator;
    }
}
