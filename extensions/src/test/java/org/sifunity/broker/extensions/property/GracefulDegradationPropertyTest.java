// Feature: message-sequence-extension, Property 6: Graceful degradation on any failure
package org.sifunity.broker.extensions.property;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import net.jqwik.api.*;
import org.sifunity.broker.extensions.sequencing.MessageSequenceFilter;
import org.sifunity.broker.extensions.sequencing.SequenceService;
import org.sifunity.broker.extensions.sequencing.ToggleService;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property 6: Graceful degradation on any failure.
 *
 * <p>For any exception thrown during toggle evaluation or sequence generation,
 * the filter catches it, passes the original request through without the
 * {@code x-sif-sequenceId} header, and does not throw.</p>
 *
 * <p><strong>Validates: Requirements 11.1, 11.2, 11.3</strong></p>
 */
@PropertyDefaults(tries = 10)
class GracefulDegradationPropertyTest {

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

    /**
     * Generates random RuntimeException types that could occur during toggle
     * evaluation or sequence generation.
     */
    @Provide
    Arbitrary<RuntimeException> randomExceptions() {
        return Arbitraries.of(
                new RuntimeException("random failure"),
                new RedisConnectionFailureException("connection refused"),
                new NullPointerException("null"),
                new IllegalStateException("bad state"),
                new ResourceAccessException("timeout")
        );
    }

    // ========== Properties ==========

    /**
     * When {@code toggleService.isSequencingEnabled()} throws any exception,
     * the filter SHALL pass the original request through without the
     * {@code x-sif-sequenceId} header and SHALL NOT throw.
     */
    @Property
    void toggleServiceExceptionDoesNotPropagate(
            @ForAll("pathSegments") String domainId,
            @ForAll("pathSegments") String solutionId,
            @ForAll("trailingPaths") String trailing,
            @ForAll("randomExceptions") RuntimeException exception) throws Exception {

        // Mock toggle service to throw
        ToggleService toggleService = mock(ToggleService.class);
        when(toggleService.isSequencingEnabled(anyString(), anyString())).thenThrow(exception);

        SequenceService sequenceService = mock(SequenceService.class);

        MessageSequenceFilter filter = new MessageSequenceFilter(sequenceService, toggleService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/" + domainId + "/" + solutionId + "/" + trailing);

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Capture the request passed to the filter chain
        AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> capturedRequest.set((HttpServletRequest) req);

        // Execute filter — must not throw
        assertThatCode(() -> filter.doFilter(request, response, filterChain))
                .doesNotThrowAnyException();

        // Filter chain was called (request passed through)
        assertThat(capturedRequest.get())
                .as("Filter chain should have been called")
                .isNotNull();

        // Captured request does NOT have x-sif-sequenceId header
        assertThat(capturedRequest.get().getHeader("x-sif-sequenceId"))
                .as("Request should NOT have x-sif-sequenceId header when toggle throws")
                .isNull();
    }

    /**
     * When {@code sequenceService.nextSequence()} throws any exception (toggle
     * returns true), the filter SHALL pass the original request through without
     * the {@code x-sif-sequenceId} header and SHALL NOT throw.
     */
    @Property
    void sequenceServiceExceptionDoesNotPropagate(
            @ForAll("pathSegments") String domainId,
            @ForAll("pathSegments") String solutionId,
            @ForAll("trailingPaths") String trailing,
            @ForAll("randomExceptions") RuntimeException exception) throws Exception {

        // Mock toggle service to return true (enabled)
        ToggleService toggleService = mock(ToggleService.class);
        when(toggleService.isSequencingEnabled(anyString(), anyString())).thenReturn(true);

        // Mock sequence service to throw
        SequenceService sequenceService = mock(SequenceService.class);
        when(sequenceService.nextSequence(anyString(), anyString())).thenThrow(exception);

        MessageSequenceFilter filter = new MessageSequenceFilter(sequenceService, toggleService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/" + domainId + "/" + solutionId + "/" + trailing);

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Capture the request passed to the filter chain
        AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> capturedRequest.set((HttpServletRequest) req);

        // Execute filter — must not throw
        assertThatCode(() -> filter.doFilter(request, response, filterChain))
                .doesNotThrowAnyException();

        // Filter chain was called (request passed through)
        assertThat(capturedRequest.get())
                .as("Filter chain should have been called")
                .isNotNull();

        // Captured request does NOT have x-sif-sequenceId header
        assertThat(capturedRequest.get().getHeader("x-sif-sequenceId"))
                .as("Request should NOT have x-sif-sequenceId header when sequence service throws")
                .isNull();
    }
}
