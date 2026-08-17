// Bugfix: delayed-response-sequencing-fix, Property 1: Bug Condition - Internal Response Path Missing Sequence Header
package org.sifunity.broker.extensions.property;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import net.jqwik.api.*;
import org.sifunity.broker.extensions.sequencing.MessageSequenceFilter;
import org.sifunity.broker.extensions.sequencing.SequenceService;
import org.sifunity.broker.extensions.sequencing.ToggleService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug Condition Exploration Test: Internal Response Path Missing Sequence Header.
 *
 * <p>This test encodes the EXPECTED (correct) behavior: when a request arrives on
 * {@code /internal/responses} or {@code /internal/responses/error} with valid
 * {@code domainId} and {@code solutionId} headers, and sequencing is enabled for
 * that domain/solution pair, the filter SHALL inject an {@code x-sif-sequenceId}
 * header with a valid positive integer value.</p>
 *
 * <p>On UNFIXED code, this test is expected to FAIL because {@code extractPathSegments()}
 * returns {@code ["internal", "responses"]} (not the real domain/solution from headers),
 * so the toggle check uses wrong identifiers and returns false — no sequence header
 * is injected.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2</strong></p>
 */
class DelayedResponseSequencingBugConditionExplorationTest {

    // ========== Arbitraries ==========

    /**
     * Generates valid domainId strings that are non-null, not "_", not "internal",
     * and not "responses" — representing real tenant domain identifiers.
     */
    @Provide
    Arbitrary<String> validDomainIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(30)
                .filter(s -> !s.equalsIgnoreCase("internal"))
                .filter(s -> !s.equalsIgnoreCase("responses"))
                .filter(s -> !s.equals("_"))
                .map(s -> "domain-" + s);
    }

    /**
     * Generates valid solutionId strings that are non-null, not "_", not "internal",
     * and not "responses" — representing real tenant solution identifiers.
     */
    @Provide
    Arbitrary<String> validSolutionIds() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(30)
                .filter(s -> !s.equalsIgnoreCase("internal"))
                .filter(s -> !s.equalsIgnoreCase("responses"))
                .filter(s -> !s.equals("_"))
                .map(s -> "solution-" + s);
    }

    /**
     * Generates internal response paths where the bug manifests.
     */
    @Provide
    Arbitrary<String> internalPaths() {
        return Arbitraries.of("/internal/responses", "/internal/responses/error");
    }

    // ========== Properties ==========

    /**
     * Bug Condition Property: For any request on internal response paths with valid
     * domainId/solutionId headers and sequencing enabled for that pair, the filter
     * SHALL inject an x-sif-sequenceId header with a valid positive integer.
     *
     * <p>On unfixed code, this FAILS because the filter uses path segments
     * ["internal", "responses"] for the toggle check instead of the header values,
     * so the toggle returns false and no sequence header is injected.</p>
     */
    @Property(tries = 20)
    void internalResponsePathGetsSequenceHeaderWhenEnabled(
            @ForAll("validDomainIds") String domainId,
            @ForAll("validSolutionIds") String solutionId,
            @ForAll("internalPaths") String path) throws Exception {

        // Mock ToggleService: return true for the HEADER values (the real domain/solution)
        ToggleService toggleService = mock(ToggleService.class);
        when(toggleService.isSequencingEnabled(domainId, solutionId)).thenReturn(true);

        // Mock SequenceService: return a positive sequence number for the header values
        SequenceService sequenceService = mock(SequenceService.class);
        when(sequenceService.nextSequence(domainId, solutionId)).thenReturn(42L);

        // Build filter
        MessageSequenceFilter filter = new MessageSequenceFilter(sequenceService, toggleService);

        // Build request simulating internal response routing with headers
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.addHeader("domainId", domainId);
        request.addHeader("solutionId", solutionId);

        MockHttpServletResponse response = new MockHttpServletResponse();

        // Capture the request passed to the filter chain
        AtomicReference<HttpServletRequest> capturedRequest = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> capturedRequest.set((HttpServletRequest) req);

        // Execute filter
        filter.doFilter(request, response, filterChain);

        // Assert: x-sif-sequenceId header should be present with valid positive integer
        // This is the EXPECTED behavior — on unfixed code this assertion FAILS
        assertThat(capturedRequest.get()).isNotNull();
        String sequenceHeader = capturedRequest.get().getHeader("x-sif-sequenceId");
        assertThat(sequenceHeader)
                .as("Request on %s with domainId=%s, solutionId=%s should get x-sif-sequenceId header",
                        path, domainId, solutionId)
                .isNotNull();

        long sequenceValue = Long.parseLong(sequenceHeader);
        assertThat(sequenceValue)
                .as("x-sif-sequenceId should be a valid positive integer")
                .isPositive();
    }
}
