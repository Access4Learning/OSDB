// Bugfix: delayed-response-sequencing-fix, Property 2: Preservation — Path-Based Sequencing Behavior Unchanged
package org.sifunity.broker.extensions.property;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.sifunity.broker.extensions.sequencing.MessageSequenceFilter;
import org.sifunity.broker.extensions.sequencing.SequenceService;
import org.sifunity.broker.extensions.sequencing.ToggleService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property 2: Preservation — Path-Based Sequencing Behavior Unchanged.
 *
 * <p>Verifies that existing path-based extraction, toggle checking, sequence injection,
 * and fail-open behaviors are preserved on unfixed code. These properties must continue
 * to pass after the fix is applied.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4</strong></p>
 */
@PropertyDefaults(tries = 20)
class DelayedResponseSequencingPreservationTest {

    // ========== Arbitraries ==========

    /**
     * Generates non-empty alphanumeric strings for domainId and solutionId path segments.
     * Excludes "internal" to avoid collision with internal paths.
     */
    @Provide
    Arbitrary<String> validPathSegments() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(30)
                .filter(s -> !s.equalsIgnoreCase("internal")
                        && !s.equalsIgnoreCase("actuator"));
    }

    /**
     * Generates trailing path suffixes representing standard connector URL patterns.
     */
    @Provide
    Arbitrary<String> trailingPaths() {
        return Arbitraries.of(
                "responses",
                "events",
                "requests/StudentPersonal",
                "requests/SchoolInfo"
        );
    }

    /**
     * Generates single-segment paths where extractPathSegments() returns null.
     * These represent actuator/health-type endpoints with insufficient path depth.
     */
    @Provide
    Arbitrary<String> singleSegmentPaths() {
        return Arbitraries.of(
                "/health",
                "/",
                "",
                "/metrics"
        );
    }

    // ========== Property A: Path-based sequencing enabled → header injected ==========

    /**
     * Property A: For all requests with valid path segments and sequencing enabled,
     * the filter SHALL inject an {@code x-sif-sequenceId} header with the sequence value.
     *
     * <p><strong>Validates: Requirements 3.1, 3.2</strong></p>
     */
    @Property
    void pathBasedSequencingEnabled_headerInjected(
            @ForAll("validPathSegments") String domainId,
            @ForAll("validPathSegments") String solutionId,
            @ForAll @LongRange(min = 1, max = Long.MAX_VALUE) long sequenceNumber,
            @ForAll("trailingPaths") String trailing) throws Exception {

        // Mock services: sequencing enabled, returns sequence number
        ToggleService toggleService = mock(ToggleService.class);
        when(toggleService.isSequencingEnabled(domainId, solutionId)).thenReturn(true);

        SequenceService sequenceService = mock(SequenceService.class);
        when(sequenceService.nextSequence(domainId, solutionId)).thenReturn(sequenceNumber);

        MessageSequenceFilter filter = new MessageSequenceFilter(sequenceService, toggleService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/" + domainId + "/" + solutionId + "/" + trailing);

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Capture request passed to filter chain
        AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> capturedRequest.set((HttpServletRequest) req);

        filter.doFilter(request, response, filterChain);

        // Assert: x-sif-sequenceId header injected with correct value
        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().getHeader("x-sif-sequenceId"))
                .as("Path-based request with sequencing enabled must have x-sif-sequenceId header")
                .isEqualTo(String.valueOf(sequenceNumber));
    }

    // ========== Property B: Path-based sequencing disabled → no header ==========

    /**
     * Property B: For all requests with valid path segments and sequencing disabled,
     * the filter SHALL NOT inject an {@code x-sif-sequenceId} header. The request
     * passes through unchanged.
     *
     * <p><strong>Validates: Requirement 3.4</strong></p>
     */
    @Property
    void pathBasedSequencingDisabled_noHeaderInjected(
            @ForAll("validPathSegments") String domainId,
            @ForAll("validPathSegments") String solutionId,
            @ForAll("trailingPaths") String trailing) throws Exception {

        // Mock services: sequencing disabled
        ToggleService toggleService = mock(ToggleService.class);
        when(toggleService.isSequencingEnabled(domainId, solutionId)).thenReturn(false);

        SequenceService sequenceService = mock(SequenceService.class);

        MessageSequenceFilter filter = new MessageSequenceFilter(sequenceService, toggleService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/" + domainId + "/" + solutionId + "/" + trailing);

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Capture request passed to filter chain
        AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> capturedRequest.set((HttpServletRequest) req);

        filter.doFilter(request, response, filterChain);

        // Assert: no x-sif-sequenceId header
        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().getHeader("x-sif-sequenceId"))
                .as("Path-based request with sequencing disabled must NOT have x-sif-sequenceId header")
                .isNull();
    }

    // ========== Property C: No identifiers in path or headers → no header ==========

    /**
     * Property C: For all requests with no domain/solution identifiers in the path
     * (extractPathSegments returns null) and no headers, the filter SHALL NOT inject
     * an {@code x-sif-sequenceId} header. Request passes through unchanged (fail-open).
     *
     * <p><strong>Validates: Requirement 3.3</strong></p>
     */
    @Property
    void noIdentifiersInPathOrHeaders_noHeaderInjected(
            @ForAll("singleSegmentPaths") String path) throws Exception {

        // No mocking needed — filter should bail out before calling any services
        ToggleService toggleService = mock(ToggleService.class);
        SequenceService sequenceService = mock(SequenceService.class);

        MessageSequenceFilter filter = new MessageSequenceFilter(sequenceService, toggleService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        // No domainId/solutionId headers set

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Capture request passed to filter chain
        AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> capturedRequest.set((HttpServletRequest) req);

        filter.doFilter(request, response, filterChain);

        // Assert: filter chain called, no sequence header
        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().getHeader("x-sif-sequenceId"))
                .as("Request with no path identifiers must NOT have x-sif-sequenceId header")
                .isNull();
    }

    /**
     * Property C extension: Requests with placeholder headers ({@code domainId: _} or
     * {@code solutionId: _}) on paths where extractPathSegments returns null still
     * pass through without sequence header on unfixed code (fail-open behavior).
     *
     * <p><strong>Validates: Requirement 3.3</strong></p>
     */
    @Property
    void placeholderHeadersOnShortPaths_noHeaderInjected(
            @ForAll("singleSegmentPaths") String path) throws Exception {

        ToggleService toggleService = mock(ToggleService.class);
        SequenceService sequenceService = mock(SequenceService.class);

        MessageSequenceFilter filter = new MessageSequenceFilter(sequenceService, toggleService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        // Add placeholder headers — these should NOT trigger sequencing
        request.addHeader("domainId", "_");
        request.addHeader("solutionId", "_");

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Capture request passed to filter chain
        AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> capturedRequest.set((HttpServletRequest) req);

        filter.doFilter(request, response, filterChain);

        // Assert: filter chain called, no sequence header
        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().getHeader("x-sif-sequenceId"))
                .as("Request with placeholder headers on short path must NOT have x-sif-sequenceId header")
                .isNull();
    }
}
