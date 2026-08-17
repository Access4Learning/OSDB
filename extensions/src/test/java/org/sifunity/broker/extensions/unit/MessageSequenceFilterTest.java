package org.sifunity.broker.extensions.unit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.extensions.sequencing.MessageSequenceFilter;
import org.sifunity.broker.extensions.sequencing.SequenceService;
import org.sifunity.broker.extensions.sequencing.ToggleService;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageSequenceFilter Unit Tests")
class MessageSequenceFilterTest {

    @Mock
    private SequenceService sequenceService;

    @Mock
    private ToggleService toggleService;

    private MessageSequenceFilter filter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new MessageSequenceFilter(sequenceService, toggleService);
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("doFilter injects x-sif-sequenceId header when toggle is enabled")
    void doFilter_injectsHeaderWhenEnabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/domainA/solutionB/events");
        request.setRequestURI("/domainA/solutionB/events");

        when(toggleService.isSequencingEnabled("domainA", "solutionB")).thenReturn(true);
        when(sequenceService.nextSequence("domainA", "solutionB")).thenReturn(42L);

        AtomicReference<HttpServletRequest> captured = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> captured.set((HttpServletRequest) req);

        filter.doFilter(request, response, filterChain);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getHeader("x-sif-sequenceId")).isEqualTo("42");
    }

    @Test
    @DisplayName("doFilter passes through without header when toggle is disabled")
    void doFilter_passesThrough_whenToggleDisabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/domainA/solutionB/events");
        request.setRequestURI("/domainA/solutionB/events");

        when(toggleService.isSequencingEnabled("domainA", "solutionB")).thenReturn(false);

        AtomicReference<HttpServletRequest> captured = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> captured.set((HttpServletRequest) req);

        filter.doFilter(request, response, filterChain);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getHeader("x-sif-sequenceId")).isNull();
        assertThat(captured.get()).isSameAs(request);
        verifyNoInteractions(sequenceService);
    }

    @Test
    @DisplayName("doFilter passes through without header when Redis throws exception")
    void doFilter_passesThrough_whenRedisError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/domainA/solutionB/events");
        request.setRequestURI("/domainA/solutionB/events");

        when(toggleService.isSequencingEnabled("domainA", "solutionB")).thenReturn(true);
        when(sequenceService.nextSequence("domainA", "solutionB"))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        AtomicReference<HttpServletRequest> captured = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> captured.set((HttpServletRequest) req);

        filter.doFilter(request, response, filterChain);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getHeader("x-sif-sequenceId")).isNull();
        assertThat(captured.get()).isSameAs(request);
    }

    @Test
    @DisplayName("doFilter passes through without header when toggle throws exception")
    void doFilter_passesThrough_whenToggleError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/domainA/solutionB/events");
        request.setRequestURI("/domainA/solutionB/events");

        when(toggleService.isSequencingEnabled("domainA", "solutionB"))
                .thenThrow(new RuntimeException("Toggle service unavailable"));

        AtomicReference<HttpServletRequest> captured = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> captured.set((HttpServletRequest) req);

        filter.doFilter(request, response, filterChain);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getHeader("x-sif-sequenceId")).isNull();
        assertThat(captured.get()).isSameAs(request);
        verifyNoInteractions(sequenceService);
    }

    @Test
    @DisplayName("doFilter passes through without header when path is unparseable")
    void doFilter_passesThrough_whenPathUnparseable() throws Exception {
        MockHttpServletRequest rootRequest = new MockHttpServletRequest("GET", "/");
        rootRequest.setRequestURI("/");

        AtomicReference<HttpServletRequest> captured = new AtomicReference<>();
        FilterChain filterChain = (req, res) -> captured.set((HttpServletRequest) req);

        filter.doFilter(rootRequest, response, filterChain);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getHeader("x-sif-sequenceId")).isNull();
        assertThat(captured.get()).isSameAs(rootRequest);

        MockHttpServletRequest singleSegment = new MockHttpServletRequest("GET", "/single");
        singleSegment.setRequestURI("/single");

        captured.set(null);
        filter.doFilter(singleSegment, response, filterChain);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getHeader("x-sif-sequenceId")).isNull();
        assertThat(captured.get()).isSameAs(singleSegment);

        verifyNoInteractions(toggleService);
        verifyNoInteractions(sequenceService);
    }

    @Test
    @DisplayName("doFilter extracts correct domainId and solutionId from URL path")
    void doFilter_extractsCorrectDomainAndSolution() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/myDomain/mySolution/requests/students");
        request.setRequestURI("/myDomain/mySolution/requests/students");

        when(toggleService.isSequencingEnabled("myDomain", "mySolution")).thenReturn(true);
        when(sequenceService.nextSequence("myDomain", "mySolution")).thenReturn(99L);

        FilterChain filterChain = (req, res) -> {};

        filter.doFilter(request, response, filterChain);

        verify(toggleService).isSequencingEnabled("myDomain", "mySolution");
        verify(sequenceService).nextSequence("myDomain", "mySolution");
    }
}
