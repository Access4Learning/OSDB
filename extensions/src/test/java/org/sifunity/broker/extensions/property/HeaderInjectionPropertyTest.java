// Feature: message-sequence-extension, Property 4: Header injection with correct value when enabled
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
 * Property 4: Header injection with correct value when enabled.
 *
 * <p>For any domain/solution pair where sequencing is enabled and Redis returns
 * sequence number {@code n}, the filter adds header {@code x-sif-sequenceId}
 * with value equal to the decimal string representation of {@code n}.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 4.3, 5.3, 6.3, 6.6</strong></p>
 */
@PropertyDefaults(tries = 10)
class HeaderInjectionPropertyTest {

    // ========== Arbitraries ==========

    /**
     * Generates non-empty alphanumeric strings for domainId and solutionId path segments.
     */
    @Provide
    Arbitrary<String> pathSegments() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(40);
    }

    /**
     * Generates trailing path suffixes representing the various connector URL patterns.
     */
    @Provide
    Arbitrary<String> trailingPaths() {
        return Arbitraries.of(
                "events",
                "responses",
                "requests/StudentPersonal",
                "requests/SchoolInfo"
        );
    }

    // ========== Properties ==========

    /**
     * When sequencing is enabled and Redis returns sequence number {@code n},
     * the filter SHALL add header {@code x-sif-sequenceId} with value
     * {@code String.valueOf(n)} to the request passed to the filter chain.
     */
    @Property
    void headerValueEqualsDecimalStringOfSequenceNumber(
            @ForAll("pathSegments") String domainId,
            @ForAll("pathSegments") String solutionId,
            @ForAll @LongRange(min = 1, max = Long.MAX_VALUE) long sequenceNumber,
            @ForAll("trailingPaths") String trailing) throws Exception {

        // Mock services
        ToggleService toggleService = mock(ToggleService.class);
        when(toggleService.isSequencingEnabled(anyString(), anyString())).thenReturn(true);

        SequenceService sequenceService = mock(SequenceService.class);
        when(sequenceService.nextSequence(anyString(), anyString())).thenReturn(sequenceNumber);

        // Build filter and request
        MessageSequenceFilter filter = new MessageSequenceFilter(sequenceService, toggleService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/" + domainId + "/" + solutionId + "/" + trailing);

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Capture the request passed to the filter chain
        AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> capturedRequest.set((HttpServletRequest) req);

        // Execute filter (use public doFilter — doFilterInternal is protected)
        filter.doFilter(request, response, filterChain);

        // Verify header is present with correct decimal string value
        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().getHeader("x-sif-sequenceId"))
                .as("Header x-sif-sequenceId should equal decimal string of sequence number %d", sequenceNumber)
                .isEqualTo(String.valueOf(sequenceNumber));
    }

    /**
     * The header name SHALL be exactly {@code x-sif-sequenceId} (case-sensitive).
     */
    @Property
    void headerNameIsExactlyXSifSequenceId(
            @ForAll("pathSegments") String domainId,
            @ForAll("pathSegments") String solutionId,
            @ForAll @LongRange(min = 1, max = Long.MAX_VALUE) long sequenceNumber) throws Exception {

        // Mock services
        ToggleService toggleService = mock(ToggleService.class);
        when(toggleService.isSequencingEnabled(anyString(), anyString())).thenReturn(true);

        SequenceService sequenceService = mock(SequenceService.class);
        when(sequenceService.nextSequence(anyString(), anyString())).thenReturn(sequenceNumber);

        // Build filter and request
        MessageSequenceFilter filter = new MessageSequenceFilter(sequenceService, toggleService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/" + domainId + "/" + solutionId + "/events");

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Capture the request
        AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> capturedRequest.set((HttpServletRequest) req);

        filter.doFilter(request, response, filterChain);

        // Verify the exact header name is present
        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().getHeader("x-sif-sequenceId"))
                .isNotNull();
    }
}
