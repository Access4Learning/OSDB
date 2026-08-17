package org.sifunity.broker.extensions.sequencing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Jakarta Servlet filter that intercepts incoming HTTP requests, extracts
 * domain/solution from the URL path, checks the feature toggle, generates
 * a sequence ID if enabled, and injects the {@code x-sif-sequenceId} header.
 *
 * <p>Follows a fail-open strategy: any failure in toggle evaluation or sequence
 * generation results in the request passing through unchanged, as if the
 * extension were not present.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class MessageSequenceFilter extends OncePerRequestFilter {

    private final SequenceService sequenceService;
    private final ToggleService toggleService;

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String PLACEHOLDER_VALUE = "_";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            String[] segments = extractPathSegments(request);

            String domainId;
            String solutionId;

            if (isInternalPath(request)) {
                // Internal paths: extract domain/solution from request headers
                domainId = request.getHeader("domainId");
                solutionId = request.getHeader("solutionId");
                if (!isValidHeader(domainId) || !isValidHeader(solutionId)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                log.debug("Extracted domain={}, solution={} from request headers for internal path",
                          domainId, solutionId);
            } else if (segments != null) {
                // Non-internal paths: use path-based extraction as before
                domainId = segments[0];
                solutionId = segments[1];
            } else {
                // No identifiers available from path or headers — pass through
                filterChain.doFilter(request, response);
                return;
            }

            boolean enabled = false;
            try {
                enabled = toggleService.isSequencingEnabled(domainId, solutionId);
            } catch (Exception ex) {
                log.warn("Toggle evaluation failed for solution={}, treating as disabled: {}",
                         solutionId, ex.getMessage());
            }

            if (!enabled) {
                log.trace("Sequencing disabled for domain={}, solution={}", domainId, solutionId);
                filterChain.doFilter(request, response);
                return;
            }

            try {
                long sequenceId = sequenceService.nextSequence(domainId, solutionId);
                MutableHttpServletRequest wrappedRequest = new MutableHttpServletRequest(request);
                wrappedRequest.addHeader("x-sif-sequenceId", String.valueOf(sequenceId));
                // Also set on the HTTP response so synchronous (IMMEDIATE-IMMEDIATE) consumers see it
                response.setHeader("x-sif-sequenceId", String.valueOf(sequenceId));
                log.debug("Injected sequenceId={} for domain={}, solution={}", sequenceId, domainId, solutionId);
                filterChain.doFilter(wrappedRequest, response);
            } catch (Exception ex) {
                log.warn("Sequence generation failed for domain={}, solution={}: {}",
                         domainId, solutionId, ex.getMessage());
                filterChain.doFilter(request, response);
            }
        } catch (Exception ex) {
            log.error("Unexpected error in MessageSequenceFilter: {}", ex.getMessage(), ex);
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Checks whether the request path starts with the internal path prefix.
     */
    private static boolean isInternalPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.startsWith(INTERNAL_PATH_PREFIX);
    }

    /**
     * Validates that a header value is non-null and not the placeholder value.
     */
    private static boolean isValidHeader(String value) {
        return value != null && !PLACEHOLDER_VALUE.equals(value);
    }

    /**
     * Extracts domainId (1st segment) and solutionId (2nd segment) from the URL path.
     * Returns {@code null} if the path doesn't have at least 2 non-empty segments.
     *
     * <p>Public and static so property tests can invoke it directly.</p>
     *
     * @param request the HTTP servlet request
     * @return a 2-element array {@code [domainId, solutionId]}, or {@code null}
     */
    public static String[] extractPathSegments(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Strip context path if present
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        // Remove leading slash and split
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isEmpty()) {
            return null;
        }
        String[] segments = path.split("/", 3); // limit to 3 parts: domainId, solutionId, rest
        if (segments.length < 2 || segments[0].isEmpty() || segments[1].isEmpty()) {
            return null;
        }
        return new String[] { segments[0], segments[1] };
    }
}
