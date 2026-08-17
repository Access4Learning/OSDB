// Feature: message-sequence-extension, Property 2: URL path parsing extracts correct segments
package org.sifunity.broker.extensions.property;

import net.jqwik.api.*;
import org.sifunity.broker.extensions.sequencing.MessageSequenceFilter;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 2: URL path parsing extracts correct segments.
 *
 * <p>For any URL path of the form {@code /{domainId}/{solutionId}/...} where domainId
 * and solutionId are non-empty, non-slash-containing strings, the
 * {@link MessageSequenceFilter#extractPathSegments} method extracts the correct domainId
 * as the first path segment and solutionId as the second path segment, regardless of
 * what follows (events, responses, requests/{objectName}, etc.).</p>
 *
 * <p><strong>Validates: Requirements 4.2, 5.2, 6.2, 6.5</strong></p>
 */
@PropertyDefaults(tries = 10)
class UrlPathParsingPropertyTest {

    // ========== Arbitraries ==========

    /**
     * Generates non-empty alphanumeric strings (no slashes) suitable for
     * domainId and solutionId path segments.
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
                "requests/SchoolInfo",
                "requests/StaffPersonal",
                "events/queue",
                "responses/delayed",
                "requests/StudentContactRelationship/abc-123"
        );
    }

    // ========== Properties ==========

    /**
     * For any non-empty domainId and solutionId with a trailing path,
     * extractPathSegments SHALL return the correct domainId and solutionId.
     */
    @Property
    @Tag("Feature: message-sequence-extension, Property 2: URL path parsing extracts correct segments")
    void extractsCorrectDomainIdAndSolutionId(
            @ForAll("pathSegments") String domainId,
            @ForAll("pathSegments") String solutionId,
            @ForAll("trailingPaths") String trailing) {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/" + domainId + "/" + solutionId + "/" + trailing);

        String[] result = MessageSequenceFilter.extractPathSegments(request);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result[0]).isEqualTo(domainId);
        assertThat(result[1]).isEqualTo(solutionId);
    }

    /**
     * For any non-empty domainId and solutionId without trailing path,
     * extractPathSegments SHALL still return the correct segments.
     */
    @Property
    @Tag("Feature: message-sequence-extension, Property 2: URL path parsing extracts correct segments")
    void extractsCorrectSegmentsWithoutTrailingPath(
            @ForAll("pathSegments") String domainId,
            @ForAll("pathSegments") String solutionId) {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/" + domainId + "/" + solutionId);

        String[] result = MessageSequenceFilter.extractPathSegments(request);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result[0]).isEqualTo(domainId);
        assertThat(result[1]).isEqualTo(solutionId);
    }

    /**
     * With a context path present, extractPathSegments SHALL still extract
     * the correct domainId and solutionId after stripping the context path.
     */
    @Property
    @Tag("Feature: message-sequence-extension, Property 2: URL path parsing extracts correct segments")
    void extractsCorrectSegmentsWithContextPath(
            @ForAll("pathSegments") String domainId,
            @ForAll("pathSegments") String solutionId,
            @ForAll("trailingPaths") String trailing) {

        MockHttpServletRequest request = new MockHttpServletRequest();
        String contextPath = "/broker";
        request.setContextPath(contextPath);
        request.setRequestURI(contextPath + "/" + domainId + "/" + solutionId + "/" + trailing);

        String[] result = MessageSequenceFilter.extractPathSegments(request);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result[0]).isEqualTo(domainId);
        assertThat(result[1]).isEqualTo(solutionId);
    }

    // ========== Edge Cases ==========

    /**
     * Empty path SHALL return null.
     */
    @Example
    void emptyPathReturnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/");

        String[] result = MessageSequenceFilter.extractPathSegments(request);

        assertThat(result).isNull();
    }

    /**
     * Single segment path SHALL return null (need at least 2 segments).
     */
    @Example
    void singleSegmentReturnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/onlyone");

        String[] result = MessageSequenceFilter.extractPathSegments(request);

        assertThat(result).isNull();
    }

    /**
     * Path with empty first segment SHALL return null.
     */
    @Example
    void emptyFirstSegmentReturnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("//solutionId/events");

        String[] result = MessageSequenceFilter.extractPathSegments(request);

        assertThat(result).isNull();
    }
}
