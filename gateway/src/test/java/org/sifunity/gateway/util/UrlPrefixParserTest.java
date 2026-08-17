// SPDX-License-Identifier: MIT
package org.sifunity.gateway.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.gateway.util.UrlPrefixParser.ParsedPath;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UrlPrefixParser}.
 */
class UrlPrefixParserTest {

    // ── Domain management paths (/domains/**) ──────────────────────────

    @Nested
    class DomainManagementPaths {

        @Test
        void parseDomains() {
            ParsedPath result = UrlPrefixParser.parse("/domains");
            assertNull(result.domainId());
            assertNull(result.solutionId());
            assertEquals("/domains", result.resourcePath());
            assertTrue(result.isDomainManagementPath());
            assertFalse(result.isSolutionManagementPath());
            assertFalse(result.isResourcePath());
        }

        @Test
        void parseDomainsWithId() {
            ParsedPath result = UrlPrefixParser.parse("/domains/iowa");
            assertNull(result.domainId());
            assertNull(result.solutionId());
            assertEquals("/domains/iowa", result.resourcePath());
            assertTrue(result.isDomainManagementPath());
        }

        @Test
        void parseDomainsTrailingSlash() {
            ParsedPath result = UrlPrefixParser.parse("/domains/");
            assertNull(result.domainId());
            assertNull(result.solutionId());
            assertEquals("/domains/", result.resourcePath());
            assertTrue(result.isDomainManagementPath());
        }
    }

    // ── Solution management paths (/{domainId}/solutions/**) ───────────

    @Nested
    class SolutionManagementPaths {

        @Test
        void parseSolutions() {
            ParsedPath result = UrlPrefixParser.parse("/iowa/solutions");
            assertEquals("iowa", result.domainId());
            assertNull(result.solutionId());
            assertEquals("/solutions", result.resourcePath());
            assertFalse(result.isDomainManagementPath());
            assertTrue(result.isSolutionManagementPath());
            assertFalse(result.isResourcePath());
        }

        @Test
        void parseSolutionsWithId() {
            ParsedPath result = UrlPrefixParser.parse("/iowa/solutions/production");
            assertEquals("iowa", result.domainId());
            assertNull(result.solutionId());
            assertEquals("/solutions/production", result.resourcePath());
            assertTrue(result.isSolutionManagementPath());
        }

        @Test
        void parseSolutionsTrailingSlash() {
            ParsedPath result = UrlPrefixParser.parse("/iowa/solutions/");
            assertEquals("iowa", result.domainId());
            assertNull(result.solutionId());
            assertEquals("/solutions/", result.resourcePath());
            assertTrue(result.isSolutionManagementPath());
        }
    }

    // ── Resource paths (/{domainId}/{solutionId}/**) ───────────────────

    @Nested
    class ResourcePaths {

        @Test
        void parseEnvironments() {
            ParsedPath result = UrlPrefixParser.parse("/iowa/production/environments");
            assertEquals("iowa", result.domainId());
            assertEquals("production", result.solutionId());
            assertEquals("/environments", result.resourcePath());
            assertFalse(result.isDomainManagementPath());
            assertFalse(result.isSolutionManagementPath());
            assertTrue(result.isResourcePath());
        }

        @Test
        void parseQueuesWithSubpath() {
            ParsedPath result = UrlPrefixParser.parse("/iowa/production/queues/123/messages");
            assertEquals("iowa", result.domainId());
            assertEquals("production", result.solutionId());
            assertEquals("/queues/123/messages", result.resourcePath());
            assertTrue(result.isResourcePath());
        }

        @Test
        void parseEventsPath() {
            ParsedPath result = UrlPrefixParser.parse("/maine/staging/events");
            assertEquals("maine", result.domainId());
            assertEquals("staging", result.solutionId());
            assertEquals("/events", result.resourcePath());
            assertTrue(result.isResourcePath());
        }

        @Test
        void parseDomainSolutionOnly() {
            ParsedPath result = UrlPrefixParser.parse("/iowa/production");
            assertEquals("iowa", result.domainId());
            assertEquals("production", result.solutionId());
            assertEquals("/", result.resourcePath());
            assertTrue(result.isResourcePath());
        }

        @Test
        void parseDomainSolutionTrailingSlash() {
            ParsedPath result = UrlPrefixParser.parse("/iowa/production/");
            assertEquals("iowa", result.domainId());
            assertEquals("production", result.solutionId());
            assertEquals("/", result.resourcePath());
            assertTrue(result.isResourcePath());
        }
    }

    // ── Edge cases ─────────────────────────────────────────────────────

    @Nested
    class EdgeCases {

        @Test
        void parseNull() {
            ParsedPath result = UrlPrefixParser.parse(null);
            assertNull(result.domainId());
            assertNull(result.solutionId());
            assertEquals("/", result.resourcePath());
            assertTrue(result.isDomainManagementPath());
        }

        @Test
        void parseEmpty() {
            ParsedPath result = UrlPrefixParser.parse("");
            assertNull(result.domainId());
            assertNull(result.solutionId());
            assertEquals("/", result.resourcePath());
        }

        @Test
        void parseBlank() {
            ParsedPath result = UrlPrefixParser.parse("   ");
            assertNull(result.domainId());
            assertNull(result.solutionId());
            assertEquals("/", result.resourcePath());
        }

        @Test
        void parseSlashOnly() {
            ParsedPath result = UrlPrefixParser.parse("/");
            assertNull(result.domainId());
            assertNull(result.solutionId());
            assertEquals("/", result.resourcePath());
        }

        @Test
        void parseActuatorHealth() {
            ParsedPath result = UrlPrefixParser.parse("/actuator/health");
            assertNull(result.domainId());
            assertNull(result.solutionId());
            assertEquals("/actuator/health", result.resourcePath());
            assertTrue(result.isDomainManagementPath());
        }

        @Test
        void parseSingleSegment() {
            ParsedPath result = UrlPrefixParser.parse("/something");
            assertNull(result.domainId());
            assertNull(result.solutionId());
            assertEquals("/something", result.resourcePath());
            assertTrue(result.isDomainManagementPath());
        }
    }
}
