// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CascadeCleanupClient}.
 *
 * <p>Validates: disabled behavior (no-op when base-url not configured),
 * successful POST calls, and exception swallowing (fire-and-forget).</p>
 */
@ExtendWith(MockitoExtension.class)
class CascadeCleanupClientTest {

    private RestTemplate restTemplate;

    private CascadeCleanupClient createClient(String baseUrl) {
        restTemplate = mock(RestTemplate.class);
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.setReadTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);
        return new CascadeCleanupClient(baseUrl, builder);
    }

    // ========== Disabled (base-url not configured) ==========

    @Test
    void triggerDomainCleanup_noOp_whenBaseUrlIsEmpty() {
        CascadeCleanupClient client = createClient("");

        client.triggerDomainCleanup("domain-1");

        verifyNoInteractions(restTemplate);
    }

    @Test
    void triggerDomainCleanup_noOp_whenBaseUrlIsNull() {
        CascadeCleanupClient client = createClient(null);

        client.triggerDomainCleanup("domain-1");

        verifyNoInteractions(restTemplate);
    }

    @Test
    void triggerDomainCleanup_noOp_whenBaseUrlIsBlank() {
        CascadeCleanupClient client = createClient("   ");

        client.triggerDomainCleanup("domain-1");

        verifyNoInteractions(restTemplate);
    }

    @Test
    void triggerSolutionCleanup_noOp_whenBaseUrlIsEmpty() {
        CascadeCleanupClient client = createClient("");

        client.triggerSolutionCleanup("domain-1", "solution-1");

        verifyNoInteractions(restTemplate);
    }

    // ========== Successful POST ==========

    @Test
    void triggerDomainCleanup_postsToCorrectUrl() {
        CascadeCleanupClient client = createClient("http://queue-service:8080");

        client.triggerDomainCleanup("domain-1");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).postForEntity(urlCaptor.capture(), any(), eq(Void.class));
        assertThat(urlCaptor.getValue()).isEqualTo("http://queue-service:8080/internal/cascade-cleanup");
    }

    @Test
    void triggerSolutionCleanup_postsToCorrectUrl() {
        CascadeCleanupClient client = createClient("http://queue-service:8080");

        client.triggerSolutionCleanup("domain-1", "solution-1");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).postForEntity(urlCaptor.capture(), any(), eq(Void.class));
        assertThat(urlCaptor.getValue()).isEqualTo("http://queue-service:8080/internal/cascade-cleanup");
    }

    // ========== Exception handling (fire-and-forget) ==========

    @Test
    void triggerDomainCleanup_doesNotThrow_whenRestTemplateThrows() {
        CascadeCleanupClient client = createClient("http://queue-service:8080");
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThatCode(() -> client.triggerDomainCleanup("domain-1"))
                .doesNotThrowAnyException();
    }

    @Test
    void triggerSolutionCleanup_doesNotThrow_whenRestTemplateThrows() {
        CascadeCleanupClient client = createClient("http://queue-service:8080");
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThatCode(() -> client.triggerSolutionCleanup("domain-1", "solution-1"))
                .doesNotThrowAnyException();
    }

    @Test
    void triggerDomainCleanup_doesNotThrow_onRuntimeException() {
        CascadeCleanupClient client = createClient("http://queue-service:8080");
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertThatCode(() -> client.triggerDomainCleanup("domain-1"))
                .doesNotThrowAnyException();
    }
}
