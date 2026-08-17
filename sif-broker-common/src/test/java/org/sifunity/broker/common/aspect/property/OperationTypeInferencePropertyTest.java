// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.aspect.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.aspect.RepositorySpanAspect;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1: Operation type inference correctness.
 *
 * <p>For any method name string, {@code inferOperationType} SHALL return the correct
 * operation type: SELECT for find/get/exists/count/query/read/search/lookup prefixes,
 * DELETE for delete/remove prefixes, INSERT for save/insert/create/persist prefixes,
 * UPDATE for update/merge/modify prefixes, and UNKNOWN otherwise.</p>
 *
 * <p><strong>Validates: Requirements 1.3</strong></p>
 */
class OperationTypeInferencePropertyTest {

    private static final List<String> SELECT_PREFIXES =
            List.of("find", "get", "exists", "count", "query", "read", "search", "lookup");

    private static final List<String> DELETE_PREFIXES =
            List.of("delete", "remove");

    private static final List<String> INSERT_PREFIXES =
            List.of("save", "insert", "create", "persist");

    private static final List<String> UPDATE_PREFIXES =
            List.of("update", "merge", "modify");

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> methodSuffixes() {
        return Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> selectMethodNames() {
        return Arbitraries.of(SELECT_PREFIXES)
                .flatMap(prefix -> methodSuffixes().map(suffix -> prefix + suffix));
    }

    @Provide
    Arbitrary<String> deleteMethodNames() {
        return Arbitraries.of(DELETE_PREFIXES)
                .flatMap(prefix -> methodSuffixes().map(suffix -> prefix + suffix));
    }

    @Provide
    Arbitrary<String> insertMethodNames() {
        return Arbitraries.of(INSERT_PREFIXES)
                .flatMap(prefix -> methodSuffixes().map(suffix -> prefix + suffix));
    }

    @Provide
    Arbitrary<String> updateMethodNames() {
        return Arbitraries.of(UPDATE_PREFIXES)
                .flatMap(prefix -> methodSuffixes().map(suffix -> prefix + suffix));
    }

    @Provide
    Arbitrary<String> unknownMethodNames() {
        // Generate strings that do NOT start with any known prefix
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30)
                .filter(name -> {
                    String lower = name.toLowerCase();
                    return SELECT_PREFIXES.stream().noneMatch(lower::startsWith)
                            && DELETE_PREFIXES.stream().noneMatch(lower::startsWith)
                            && INSERT_PREFIXES.stream().noneMatch(lower::startsWith)
                            && UPDATE_PREFIXES.stream().noneMatch(lower::startsWith);
                });
    }

    // ========== Properties ==========

    /**
     * Methods with SELECT prefixes return "SELECT".
     *
     * <p><strong>Validates: Requirements 1.3</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 1: Operation type inference correctness")
    @Property(tries = 20)
    void selectPrefixesReturnSelect(@ForAll("selectMethodNames") String methodName) {
        assertThat(RepositorySpanAspect.inferOperationType(methodName)).isEqualTo("SELECT");
    }

    /**
     * Methods with DELETE prefixes return "DELETE".
     *
     * <p><strong>Validates: Requirements 1.3</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 1: Operation type inference correctness")
    @Property(tries = 20)
    void deletePrefixesReturnDelete(@ForAll("deleteMethodNames") String methodName) {
        assertThat(RepositorySpanAspect.inferOperationType(methodName)).isEqualTo("DELETE");
    }

    /**
     * Methods with INSERT prefixes return "INSERT".
     *
     * <p><strong>Validates: Requirements 1.3</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 1: Operation type inference correctness")
    @Property(tries = 20)
    void insertPrefixesReturnInsert(@ForAll("insertMethodNames") String methodName) {
        assertThat(RepositorySpanAspect.inferOperationType(methodName)).isEqualTo("INSERT");
    }

    /**
     * Methods with UPDATE prefixes return "UPDATE".
     *
     * <p><strong>Validates: Requirements 1.3</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 1: Operation type inference correctness")
    @Property(tries = 20)
    void updatePrefixesReturnUpdate(@ForAll("updateMethodNames") String methodName) {
        assertThat(RepositorySpanAspect.inferOperationType(methodName)).isEqualTo("UPDATE");
    }

    /**
     * Methods without any known prefix return "UNKNOWN".
     *
     * <p><strong>Validates: Requirements 1.3</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 1: Operation type inference correctness")
    @Property(tries = 20)
    void unknownPrefixesReturnUnknown(@ForAll("unknownMethodNames") String methodName) {
        assertThat(RepositorySpanAspect.inferOperationType(methodName)).isEqualTo("UNKNOWN");
    }
}
