// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for StorageKeyGenerator.
 * Tests hierarchical storage key generation with various inputs and edge cases.
 */
class StorageKeyGeneratorTest {

    // ========== New primary generateKey() with prefix/domain/solution Tests ==========

    @Test
    void generateKey_newPrimary_withAllParameters_returnsCorrectFormat() {
        String result = StorageKeyGenerator.generateKey(
                "sif-messages", "state-a-doe", "production",
                "3ab1749c-1234-5678-9abc-def012345678", "district", "DEFAULT",
                "event", "StudentPersonal",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        assertThat(result).isEqualTo(
                "sif-messages/state-a-doe/production/3ab1749c-1234-5678-9abc-def012345678/district/DEFAULT/event/StudentPersonal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_newPrimary_withEmptyPrefix_omitsPrefix() {
        String result = StorageKeyGenerator.generateKey(
                "", "state-a-doe", "production",
                "3ab1749c-1234-5678-9abc-def012345678", "district", "DEFAULT",
                "event", "StudentPersonal",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        assertThat(result).startsWith("state-a-doe/");
        assertThat(result).doesNotStartWith("/");
    }

    @Test
    void generateKey_newPrimary_withNullPrefix_omitsPrefix() {
        String result = StorageKeyGenerator.generateKey(
                null, "state-a-doe", "production",
                "3ab1749c-1234-5678-9abc-def012345678", "district", "DEFAULT",
                "event", "StudentPersonal",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        assertThat(result).startsWith("state-a-doe/");
    }

    @Test
    void generateKey_newPrimary_withNullContext_defaultsToDefault() {
        String result = StorageKeyGenerator.generateKey(
                "prefix", "domain", "solution",
                "env-id", "zone", null,
                "event", "StudentPersonal",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        assertThat(result).contains("/default/event/");
    }

    @Test
    void generateKey_newPrimary_withEmptyContext_defaultsToDefault() {
        String result = StorageKeyGenerator.generateKey(
                "prefix", "domain", "solution",
                "env-id", "zone", "",
                "request", "StudentPersonal",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        assertThat(result).contains("/default/request/");
    }

    @Test
    void generateKey_newPrimary_requestMessageType_accepted() {
        String result = StorageKeyGenerator.generateKey(
                "", "domain", "solution", "env-id", "zone", "ctx",
                "request", "StudentPersonal",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        assertThat(result).contains("/request/");
    }

    @Test
    void generateKey_newPrimary_responseMessageType_accepted() {
        String result = StorageKeyGenerator.generateKey(
                "", "domain", "solution", "env-id", "zone", "ctx",
                "response", "StudentPersonal",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        assertThat(result).contains("/response/");
    }

    @Test
    void generateKey_newPrimary_invalidMessageType_throwsException() {
        assertThatThrownBy(() -> StorageKeyGenerator.generateKey(
                "", "domain", "solution", "env-id", "zone", "ctx",
                "invalid", "StudentPersonal",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageType must be one of");
    }

    @Test
    void generateKey_newPrimary_nullMessageType_throwsException() {
        assertThatThrownBy(() -> StorageKeyGenerator.generateKey(
                "", "domain", "solution", "env-id", "zone", "ctx",
                null, "StudentPersonal",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateKey_newPrimary_sanitizesAllComponents() {
        String result = StorageKeyGenerator.generateKey(
                "pre@fix", "dom.ain", "sol/ution",
                "env:id", "zo ne", "con#text",
                "event", "Stu:dent",
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));

        assertThat(result).isEqualTo(
                "pre_fix/dom_ain/sol_ution/env_id/zo_ne/con_text/event/Stu_dent/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_newPrimary_withPrefix_hasCorrectSlashCount() {
        String result = StorageKeyGenerator.generateKey(
                "prefix", "domain", "solution", "env-id", "zone", "context",
                "event", "StudentPersonal", UUID.randomUUID());

        // prefix/domain/solution/envId/zone/context/messageType/service/messageId.json = 8 slashes
        long slashCount = result.chars().filter(ch -> ch == '/').count();
        assertThat(slashCount).isEqualTo(8);
    }

    @Test
    void generateKey_newPrimary_withoutPrefix_hasCorrectSlashCount() {
        String result = StorageKeyGenerator.generateKey(
                "", "domain", "solution", "env-id", "zone", "context",
                "event", "StudentPersonal", UUID.randomUUID());

        // domain/solution/envId/zone/context/messageType/service/messageId.json = 7 slashes
        long slashCount = result.chars().filter(ch -> ch == '/').count();
        assertThat(slashCount).isEqualTo(7);
    }

    @Test
    void generateKey_newPrimary_alwaysEndsWithJson() {
        String result = StorageKeyGenerator.generateKey(
                "prefix", "domain", "solution", "env-id", "zone", "context",
                "event", "StudentPersonal", UUID.randomUUID());

        assertThat(result).endsWith(".json");
    }

    // ========== Legacy generateKey() with all parameters Tests ==========

    @Test
    void generateKey_withAllParameters_returnsCorrectFormat() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("env-123/zone-default/context-school/StudentPersonal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withNullContext_usesDefault() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = null;
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("env-123/zone-default/default/StudentPersonal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withSpecialCharactersInEnvironment_sanitizes() {
        String environment = "env@123";
        String zone = "zone-default";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("env_123/zone-default/context-school/StudentPersonal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withSpecialCharactersInZone_sanitizes() {
        String environment = "env-123";
        String zone = "zone/default";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("env-123/zone_default/context-school/StudentPersonal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withSpecialCharactersInContext_sanitizes() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = "context.school";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("env-123/zone-default/context_school/StudentPersonal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withSpecialCharactersInService_sanitizes() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = "context-school";
        String service = "Student:Personal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("env-123/zone-default/context-school/Student_Personal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withMultipleSpecialCharacters_sanitizesAll() {
        String environment = "env@123!";
        String zone = "zone/default#";
        String context = "context.school$";
        String service = "Student:Personal%";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("env_123_/zone_default_/context_school_/Student_Personal_/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withSlashesInParameters_sanitizes() {
        String environment = "env/123";
        String zone = "zone/default";
        String context = "context/school";
        String service = "Student/Personal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("env_123/zone_default/context_school/Student_Personal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withDotsInParameters_sanitizes() {
        String environment = "env.123";
        String zone = "zone.default";
        String context = "context.school";
        String service = "Student.Personal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("env_123/zone_default/context_school/Student_Personal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withSpacesInParameters_sanitizes() {
        String environment = "env 123";
        String zone = "zone default";
        String context = "context school";
        String service = "Student Personal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("env_123/zone_default/context_school/Student_Personal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withHyphensAndUnderscores_preservesThem() {
        String environment = "env-123_test";
        String zone = "zone-default_prod";
        String context = "context-school_district";
        String service = "Student-Personal_Info";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("env-123_test/zone-default_prod/context-school_district/Student-Personal_Info/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withAlphanumericOnly_noSanitization() {
        String environment = "env123";
        String zone = "zonedefault";
        String context = "contextschool";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("env123/zonedefault/contextschool/StudentPersonal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withMixedCaseParameters_preservesCase() {
        String environment = "Env-123";
        String zone = "Zone-Default";
        String context = "Context-School";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("Env-123/Zone-Default/Context-School/StudentPersonal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withDifferentUUIDs_generatesDifferentKeys() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID messageId2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

        String result1 = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId1);
        String result2 = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId2);

        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    void generateKey_withRandomUUID_includesUUIDInKey() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId = UUID.randomUUID();

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).contains(messageId.toString());
        assertThat(result).endsWith(".json");
    }

    @Test
    void generateKey_alwaysEndsWithJsonExtension() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId = UUID.randomUUID();

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).endsWith(".json");
    }

    @Test
    void generateKey_hasCorrectNumberOfSlashes() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId = UUID.randomUUID();

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        // Should have exactly 4 slashes: env/zone/context/service/messageId.json
        long slashCount = result.chars().filter(ch -> ch == '/').count();
        assertThat(slashCount).isEqualTo(4);
    }

    @Test
    void generateKey_withEmptyStrings_generatesKeyWithEmptyComponents() {
        String environment = "";
        String zone = "";
        String context = "";
        String service = "";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isEqualTo("////550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withUnicodeCharacters_sanitizes() {
        String environment = "env-日本語";
        String zone = "zone-中文";
        String context = "context-한국어";
        String service = "Student-العربية";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        // Unicode characters should be replaced with underscores
        assertThat(result).matches("^[a-zA-Z0-9-_/]+\\.json$");
    }

    @Test
    void generateKey_withLongParameters_generatesValidKey() {
        String environment = "a".repeat(100);
        String zone = "b".repeat(100);
        String context = "c".repeat(100);
        String service = "d".repeat(100);
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).isNotNull();
        assertThat(result).endsWith(".json");
        assertThat(result).contains(messageId.toString());
    }

    // ========== generateKey() without context Tests ==========

    @Test
    void generateKey_withoutContext_usesDefault() {
        String environment = "env-123";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        assertThat(result).isEqualTo("env-123/zone-default/default/StudentPersonal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withoutContext_matchesNullContext() {
        String environment = "env-123";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String resultWithoutContext = StorageKeyGenerator.generateKey(environment, zone, service, messageId);
        String resultWithNullContext = StorageKeyGenerator.generateKey(environment, zone, null, service, messageId);

        assertThat(resultWithoutContext).isEqualTo(resultWithNullContext);
    }

    @Test
    void generateKey_withoutContext_sanitizesOtherParameters() {
        String environment = "env@123";
        String zone = "zone/default";
        String service = "Student:Personal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        assertThat(result).isEqualTo("env_123/zone_default/default/Student_Personal/550e8400-e29b-41d4-a716-446655440000.json");
    }

    @Test
    void generateKey_withoutContext_alwaysEndsWithJson() {
        String environment = "env-123";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.randomUUID();

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        assertThat(result).endsWith(".json");
    }

    @Test
    void generateKey_withoutContext_hasCorrectNumberOfSlashes() {
        String environment = "env-123";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.randomUUID();

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        // Should have exactly 4 slashes: env/zone/default/service/messageId.json
        long slashCount = result.chars().filter(ch -> ch == '/').count();
        assertThat(slashCount).isEqualTo(4);
    }

    @Test
    void generateKey_withoutContext_includesDefaultInPath() {
        String environment = "env-123";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.randomUUID();

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        assertThat(result).contains("/default/");
    }

    // ========== Sanitization Tests ==========

    @Test
    void sanitize_preservesAlphanumeric() {
        String environment = "abc123XYZ";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        assertThat(result).startsWith("abc123XYZ/");
    }

    @Test
    void sanitize_preservesHyphens() {
        String environment = "env-123-test";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        assertThat(result).startsWith("env-123-test/");
    }

    @Test
    void sanitize_preservesUnderscores() {
        String environment = "env_123_test";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        assertThat(result).startsWith("env_123_test/");
    }

    @Test
    void sanitize_replacesAtSign() {
        String environment = "env@123";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        assertThat(result).startsWith("env_123/");
        assertThat(result).doesNotContain("@");
    }

    @Test
    void sanitize_replacesSlash() {
        String environment = "env/123";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        // The slash in "env/123" should be replaced, but path separators should remain
        assertThat(result).startsWith("env_123/");
    }

    @Test
    void sanitize_replacesDot() {
        String environment = "env.123";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        // The dot in "env.123" should be replaced, but the .json extension should remain
        assertThat(result).startsWith("env_123/");
        assertThat(result).endsWith(".json");
    }

    @Test
    void sanitize_replacesColon() {
        String environment = "env:123";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        assertThat(result).startsWith("env_123/");
        assertThat(result).doesNotContain(":");
    }

    @Test
    void sanitize_replacesSpace() {
        String environment = "env 123";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        assertThat(result).startsWith("env_123/");
        assertThat(result).doesNotContain(" ");
    }

    @Test
    void sanitize_replacesMultipleConsecutiveSpecialCharacters() {
        String environment = "env@#$123";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        assertThat(result).startsWith("env___123/");
    }

    @Test
    void sanitize_replacesAllCommonSpecialCharacters() {
        String environment = "env!@#$%^&*()+=[]{}|;:',.<>?/\\`~";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        // All special characters should be replaced with underscores
        assertThat(result).matches("^[a-zA-Z0-9-_/]+\\.json$");
    }

    // ========== Format Validation Tests ==========

    @Test
    void generateKey_matchesExpectedPattern() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        // Pattern: {sanitized}/{sanitized}/{sanitized}/{sanitized}/{uuid}.json
        assertThat(result).matches("^[^/]+/[^/]+/[^/]+/[^/]+/[a-f0-9-]+\\.json$");
    }

    @Test
    void generateKey_withoutContext_matchesExpectedPattern() {
        String environment = "env-123";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result = StorageKeyGenerator.generateKey(environment, zone, service, messageId);

        // Pattern: {sanitized}/{sanitized}/default/{sanitized}/{uuid}.json
        assertThat(result).matches("^[^/]+/[^/]+/default/[^/]+/[a-f0-9-]+\\.json$");
    }

    @Test
    void generateKey_doesNotContainDoubleSlashes() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId = UUID.randomUUID();

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).doesNotContain("//");
    }

    @Test
    void generateKey_doesNotStartWithSlash() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId = UUID.randomUUID();

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).doesNotStartWith("/");
    }

    @Test
    void generateKey_doesNotEndWithSlash() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId = UUID.randomUUID();

        String result = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result).doesNotEndWith("/");
    }

    // ========== Consistency Tests ==========

    @Test
    void generateKey_withSameInputs_returnsSameKey() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result1 = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);
        String result2 = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);

        assertThat(result1).isEqualTo(result2);
    }

    @Test
    void generateKey_withDifferentEnvironments_returnsDifferentKeys() {
        String zone = "zone-default";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result1 = StorageKeyGenerator.generateKey("env-1", zone, context, service, messageId);
        String result2 = StorageKeyGenerator.generateKey("env-2", zone, context, service, messageId);

        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    void generateKey_withDifferentZones_returnsDifferentKeys() {
        String environment = "env-123";
        String context = "context-school";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result1 = StorageKeyGenerator.generateKey(environment, "zone-1", context, service, messageId);
        String result2 = StorageKeyGenerator.generateKey(environment, "zone-2", context, service, messageId);

        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    void generateKey_withDifferentContexts_returnsDifferentKeys() {
        String environment = "env-123";
        String zone = "zone-default";
        String service = "StudentPersonal";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result1 = StorageKeyGenerator.generateKey(environment, zone, "context-1", service, messageId);
        String result2 = StorageKeyGenerator.generateKey(environment, zone, "context-2", service, messageId);

        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    void generateKey_withDifferentServices_returnsDifferentKeys() {
        String environment = "env-123";
        String zone = "zone-default";
        String context = "context-school";
        UUID messageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        String result1 = StorageKeyGenerator.generateKey(environment, zone, context, "StudentPersonal", messageId);
        String result2 = StorageKeyGenerator.generateKey(environment, zone, context, "SchoolInfo", messageId);

        assertThat(result1).isNotEqualTo(result2);
    }

    // ========== Utility Class Tests ==========

    @Test
    void testUtilityClass_cannotBeInstantiated() {
        assertThatThrownBy(() -> {
            // Use reflection to try to instantiate the utility class
            var constructor = StorageKeyGenerator.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
        .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
        .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
