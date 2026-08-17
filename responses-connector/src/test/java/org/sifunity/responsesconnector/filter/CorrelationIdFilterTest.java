// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CorrelationIdFilter.
 * 
 * Requirements: 13.1, 13.6 (Observability and Logging)
 */
@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {
    
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private FilterChain filterChain;
    
    private CorrelationIdFilter filter;
    
    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }
    
    @AfterEach
    void tearDown() {
        MDC.clear();
    }
    
    @Test
    void shouldExtractCorrelationIdFromHeader() throws ServletException, IOException {
        // Given
        String expectedCorrelationId = UUID.randomUUID().toString();
        when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn(expectedCorrelationId);
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(response).setHeader(CORRELATION_ID_HEADER, expectedCorrelationId);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void shouldGenerateCorrelationIdWhenHeaderMissing() throws ServletException, IOException {
        // Given
        when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn(null);
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(response).setHeader(eq(CORRELATION_ID_HEADER), anyString());
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void shouldGenerateCorrelationIdWhenHeaderBlank() throws ServletException, IOException {
        // Given
        when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn("   ");
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(response).setHeader(eq(CORRELATION_ID_HEADER), anyString());
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void shouldAddCorrelationIdToMDC() throws ServletException, IOException {
        // Given
        String expectedCorrelationId = UUID.randomUUID().toString();
        when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn(expectedCorrelationId);
        
        doAnswer(invocation -> {
            // Verify MDC contains correlation ID during filter chain execution
            String mdcValue = MDC.get("correlationId");
            assertThat(mdcValue).isEqualTo(expectedCorrelationId);
            return null;
        }).when(filterChain).doFilter(request, response);
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void shouldClearMDCAfterFilterChain() throws ServletException, IOException {
        // Given
        String correlationId = UUID.randomUUID().toString();
        when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn(correlationId);
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        assertThat(MDC.get("correlationId")).isNull();
    }
    
    @Test
    void shouldClearMDCEvenWhenExceptionOccurs() throws ServletException, IOException {
        // Given
        String correlationId = UUID.randomUUID().toString();
        when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn(correlationId);
        doThrow(new ServletException("Test exception")).when(filterChain).doFilter(request, response);
        
        // When/Then
        try {
            filter.doFilter(request, response, filterChain);
        } catch (ServletException e) {
            // Expected
        }
        
        // Verify MDC is cleared even after exception
        assertThat(MDC.get("correlationId")).isNull();
    }
    
    @Test
    void shouldSetCorrelationIdInResponseHeader() throws ServletException, IOException {
        // Given
        String correlationId = UUID.randomUUID().toString();
        when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn(correlationId);
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(response).setHeader(CORRELATION_ID_HEADER, correlationId);
    }
}
