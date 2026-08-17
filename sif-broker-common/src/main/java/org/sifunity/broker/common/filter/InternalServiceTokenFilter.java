// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Servlet filter that validates the {@code X-Internal-Service-Token} header on all
 * {@code /internal/**} requests. Uses constant-time comparison via
 * {@link MessageDigest#isEqual(byte[], byte[])} to prevent timing attacks.
 *
 * <p>When the header is missing or does not match the configured token, the filter
 * short-circuits with HTTP 401 and a JSON {@link SifErrorResponse} body. When the
 * header matches, the request proceeds through the filter chain.</p>
 *
 * <p>Registration and URL mapping are handled by
 * {@code InternalServiceTokenAutoConfiguration} — this filter is not a
 * {@code @Component} itself so that its URL pattern can be controlled externally.</p>
 *
 * @see SifErrorResponse
 */
@Slf4j
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Internal-Service-Token";
    private static final String ERROR_CODE = "AUTH_010";
    private static final String ERROR_MESSAGE = "Missing or invalid internal service token";

    private final byte[] expectedTokenBytes;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new filter with the configured internal service token.
     *
     * @param configuredToken the expected token value that clients must present
     */
    public InternalServiceTokenFilter(String configuredToken) {
        this.expectedTokenBytes = configuredToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        log.info("InternalServiceTokenFilter initialized for /internal/** endpoint protection");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String presentedToken = request.getHeader(HEADER_NAME);

        if (presentedToken == null || presentedToken.isEmpty()) {
            log.warn("Request to {} rejected — missing {} header",
                    request.getRequestURI(), HEADER_NAME);
            writeUnauthorizedResponse(response);
            return;
        }

        byte[] presentedBytes = presentedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedTokenBytes, presentedBytes)) {
            log.warn("Request to {} rejected — invalid {} header",
                    request.getRequestURI(), HEADER_NAME);
            writeUnauthorizedResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        SifErrorResponse errorResponse = new SifErrorResponse(
                java.util.UUID.randomUUID().toString(),
                HttpServletResponse.SC_UNAUTHORIZED,
                ERROR_CODE,
                ERROR_MESSAGE,
                null,
                "BROKER",
                Instant.now(),
                null,
                null
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(SifErrorResponseEnvelope.of(errorResponse)));
    }
}
