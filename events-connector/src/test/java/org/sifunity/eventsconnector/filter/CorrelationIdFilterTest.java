// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.filter;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CorrelationIdFilter.
 * Tests correlation ID generation, extraction, and MDC management.
 * 
 * Requirements: 14 (Observability and Logging)
 */
@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {
    
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
    void shouldGenerateCorrelationIdWhenNotProvided() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(response).setHeader(eq("X-Correlation-Id"), anyString());
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void shouldUseProvidedCorrelationId() throws ServletException, IOException {
        // Given
        String providedCorrelationId = "test-correlation-id-123";
        when(request.getHeader("X-Correlation-Id")).thenReturn(providedCorrelationId);
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(response).setHeader("X-Correlation-Id", providedCorrelationId);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void shouldAddCorrelationIdToMDC() throws ServletException, IOException {
        // Given
        String correlationId = "test-correlation-id-456";
        when(request.getHeader("X-Correlation-Id")).thenReturn(correlationId);
        
        // When
        doAnswer(invocation -> {
            // Verify MDC contains correlation ID during filter chain execution
            String mdcValue = MDC.get("correlationId");
            assertThat(mdcValue).isEqualTo(correlationId);
            return null;
        }).when(filterChain).doFilter(request, response);
        
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void shouldClearMDCAfterFilterChain() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-Id")).thenReturn("test-id");
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        assertThat(MDC.get("correlationId")).isNull();
    }
    
    @Test
    void shouldClearMDCEvenWhenExceptionOccurs() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-Id")).thenReturn("test-id");
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
    void shouldGenerateNewCorrelationIdWhenBlank() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-Id")).thenReturn("   ");
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(response).setHeader(eq("X-Correlation-Id"), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldGenerateNewCorrelationIdWhenEmpty() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-Id")).thenReturn("");
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(response).setHeader(eq("X-Correlation-Id"), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSetResponseHeaderWithGeneratedId() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        
        // Capture the generated correlation ID
        doAnswer(invocation -> {
            String mdcValue = MDC.get("correlationId");
            assertThat(mdcValue).isNotNull().isNotBlank();
            // Verify it looks like a UUID
            assertThat(mdcValue).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            return null;
        }).when(filterChain).doFilter(request, response);
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(response).setHeader(eq("X-Correlation-Id"), argThat(id ->
            id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        ));
    }
}
