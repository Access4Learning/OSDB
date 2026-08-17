// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.aspect.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.aspect.RepositorySpanAspect;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 3: Repository span naming follows convention.
 *
 * <p>For any entity type name and method name, {@code buildSpanName} SHALL produce
 * a span name equal to {@code db.jpa.{entityType}.{methodName}}.</p>
 *
 * <p><strong>Validates: Requirements 1.2, 1.4</strong></p>
 */
class RepositorySpanNamingPropertyTest {

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> entityTypeNames() {
        // Entity types are PascalCase class names like Domain, Zone, Alert, ProviderRegistration
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30)
                .map(s -> Character.toUpperCase(s.charAt(0)) + (s.length() > 1 ? s.substring(1) : ""));
    }

    @Provide
    Arbitrary<String> methodNames() {
        // Method names are camelCase like findById, save, deleteAll, countByDomain
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(40)
                .map(s -> Character.toLowerCase(s.charAt(0)) + (s.length() > 1 ? s.substring(1) : ""));
    }

    // ========== Properties ==========

    /**
     * For any entity type and method name, span name equals {@code db.jpa.{entityType}.{methodName}}.
     *
     * <p><strong>Validates: Requirements 1.2, 1.4</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 3: Repository span naming follows convention")
    @Property(tries = 20)
    void spanNameFollowsDbJpaEntityMethodConvention(
            @ForAll("entityTypeNames") String entityType,
            @ForAll("methodNames") String methodName) {

        String spanName = RepositorySpanAspect.buildSpanName(entityType, methodName);

        assertThat(spanName).isEqualTo("db.jpa." + entityType + "." + methodName);
    }

    /**
     * Span name always starts with the {@code db.jpa.} prefix regardless of inputs.
     *
     * <p><strong>Validates: Requirements 1.2</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 3: Repository span naming follows convention")
    @Property(tries = 20)
    void spanNameAlwaysStartsWithDbJpaPrefix(
            @ForAll("entityTypeNames") String entityType,
            @ForAll("methodNames") String methodName) {

        String spanName = RepositorySpanAspect.buildSpanName(entityType, methodName);

        assertThat(spanName).startsWith("db.jpa.");
    }

    /**
     * Span name contains exactly four dot-separated segments: db, jpa, entityType, methodName.
     *
     * <p><strong>Validates: Requirements 1.2, 1.4</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 3: Repository span naming follows convention")
    @Property(tries = 20)
    void spanNameHasFourDotSeparatedSegments(
            @ForAll("entityTypeNames") String entityType,
            @ForAll("methodNames") String methodName) {

        String spanName = RepositorySpanAspect.buildSpanName(entityType, methodName);

        String[] segments = spanName.split("\\.");
        assertThat(segments).hasSize(4);
        assertThat(segments[0]).isEqualTo("db");
        assertThat(segments[1]).isEqualTo("jpa");
        assertThat(segments[2]).isEqualTo(entityType);
        assertThat(segments[3]).isEqualTo(methodName);
    }
}
