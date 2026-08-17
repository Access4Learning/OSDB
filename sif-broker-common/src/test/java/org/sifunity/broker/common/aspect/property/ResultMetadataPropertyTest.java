// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.aspect.property;

import io.opentelemetry.api.trace.Span;
import net.jqwik.api.*;
import org.sifunity.broker.common.aspect.RepositorySpanAspect;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Slice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.mockito.Mockito.*;

/**
 * Property 4: Result metadata correctness.
 *
 * <p>For any method return value that is a {@link Collection} of size N, the span SHALL
 * have attribute {@code db.result_count} equal to N. For any method return value that is
 * a {@link Page} or {@link Slice}, the span SHALL have attribute {@code db.result_count}
 * equal to {@code getNumberOfElements()}. For any method return value that is an
 * {@link Optional}, the span SHALL have attribute {@code db.result_found} equal to the
 * Optional's {@code isPresent()} value.</p>
 *
 * <p><strong>Validates: Requirements 1.5, 1.6</strong></p>
 */
class ResultMetadataPropertyTest {

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<Integer> collectionSizes() {
        return Arbitraries.integers().between(0, 500);
    }

    @Provide
    Arbitrary<Boolean> optionalPresence() {
        return Arbitraries.of(true, false);
    }

    @Provide
    Arbitrary<Integer> pageSizes() {
        return Arbitraries.integers().between(0, 200);
    }

    // ========== Properties ==========

    /**
     * For Collection results of size N, span has {@code db.result_count} = N.
     *
     * <p><strong>Validates: Requirements 1.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 4: Result metadata correctness")
    @Property(tries = 20)
    void collectionResultSetsResultCount(@ForAll("collectionSizes") int size) {
        Span span = mock(Span.class);
        when(span.setAttribute(anyString(), anyLong())).thenReturn(span);

        List<String> collection = new ArrayList<>(
                IntStream.range(0, size).mapToObj(i -> "item" + i).toList());

        RepositorySpanAspect.addResultMetadata(span, collection);

        verify(span).setAttribute("db.result_count", (long) size);
        verify(span, never()).setAttribute(eq("db.result_found"), anyBoolean());
    }

    /**
     * For Page results with N elements, span has {@code db.result_count} = N.
     *
     * <p><strong>Validates: Requirements 1.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 4: Result metadata correctness")
    @Property(tries = 20)
    void pageResultSetsResultCount(@ForAll("pageSizes") int numberOfElements) {
        Span span = mock(Span.class);
        when(span.setAttribute(anyString(), anyLong())).thenReturn(span);

        List<String> content = IntStream.range(0, numberOfElements)
                .mapToObj(i -> "item" + i).toList();
        // Total elements >= page content; page size at least as large as content
        int pageSize = Math.max(numberOfElements, 1);
        Page<String> page = new PageImpl<>(content, PageRequest.of(0, pageSize), numberOfElements + 10);

        RepositorySpanAspect.addResultMetadata(span, page);

        verify(span).setAttribute("db.result_count", (long) numberOfElements);
        verify(span, never()).setAttribute(eq("db.result_found"), anyBoolean());
    }

    /**
     * For Slice results with N elements, span has {@code db.result_count} = N.
     *
     * <p><strong>Validates: Requirements 1.5</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 4: Result metadata correctness")
    @Property(tries = 20)
    void sliceResultSetsResultCount(@ForAll("pageSizes") int numberOfElements) {
        Span span = mock(Span.class);
        when(span.setAttribute(anyString(), anyLong())).thenReturn(span);

        List<String> content = IntStream.range(0, numberOfElements)
                .mapToObj(i -> "item" + i).toList();
        int pageSize = Math.max(numberOfElements, 1);
        Slice<String> slice = new SliceImpl<>(content, PageRequest.of(0, pageSize), true);

        RepositorySpanAspect.addResultMetadata(span, slice);

        verify(span).setAttribute("db.result_count", (long) numberOfElements);
        verify(span, never()).setAttribute(eq("db.result_found"), anyBoolean());
    }

    /**
     * For Optional.of(x), span has {@code db.result_found} = true.
     * For Optional.empty(), span has {@code db.result_found} = false.
     *
     * <p><strong>Validates: Requirements 1.6</strong></p>
     */
    @Tag("Feature: data-access-span-instrumentation, Property 4: Result metadata correctness")
    @Property(tries = 20)
    void optionalResultSetsResultFound(@ForAll("optionalPresence") boolean present) {
        Span span = mock(Span.class);
        when(span.setAttribute(anyString(), anyBoolean())).thenReturn(span);

        Optional<String> optional = present ? Optional.of("value") : Optional.empty();

        RepositorySpanAspect.addResultMetadata(span, optional);

        verify(span).setAttribute("db.result_found", present);
        verify(span, never()).setAttribute(eq("db.result_count"), anyLong());
    }
}
