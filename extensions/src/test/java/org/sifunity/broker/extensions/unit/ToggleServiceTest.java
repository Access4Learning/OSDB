package org.sifunity.broker.extensions.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.extensions.config.SequencingProperties;
import org.sifunity.broker.extensions.sequencing.ToggleService;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToggleService Unit Tests")
class ToggleServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private SequencingProperties properties;
    private ToggleService toggleService;

    private static final String DOMAIN_ID = "dom1";
    private static final String SOLUTION_ID = "sol1";
    private static final String EXPECTED_URL =
            "http://localhost:8081/dom1/sol1/tags?entityType=SOLUTION&tagKey=SEQUENCING_ENABLED";

    @BeforeEach
    void setUp() {
        properties = new SequencingProperties();
        toggleService = new ToggleService(restTemplate, properties);
    }

    @Test
    @DisplayName("isSequencingEnabled returns true when tag value is 'true'")
    void isSequencingEnabled_returnsTrueWhenTagValueIsTrue() {
        // The list endpoint returns an array of tag objects
        when(restTemplate.getForObject(eq(EXPECTED_URL), eq(ToggleService.EntityTagResult[].class)))
                .thenReturn(new ToggleService.EntityTagResult[]{new ToggleService.EntityTagResult("true")});

        boolean result = toggleService.isSequencingEnabled(DOMAIN_ID, SOLUTION_ID);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isSequencingEnabled returns false when tag value is 'false'")
    void isSequencingEnabled_returnsFalseWhenTagValueIsFalse() {
        when(restTemplate.getForObject(eq(EXPECTED_URL), eq(ToggleService.EntityTagResult[].class)))
                .thenReturn(new ToggleService.EntityTagResult[]{new ToggleService.EntityTagResult("false")});

        boolean result = toggleService.isSequencingEnabled(DOMAIN_ID, SOLUTION_ID);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isSequencingEnabled returns false when no tags found (empty array)")
    void isSequencingEnabled_returnsFalseWhenNoTagsFound() {
        when(restTemplate.getForObject(eq(EXPECTED_URL), eq(ToggleService.EntityTagResult[].class)))
                .thenReturn(new ToggleService.EntityTagResult[]{});

        boolean result = toggleService.isSequencingEnabled(DOMAIN_ID, SOLUTION_ID);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isSequencingEnabled returns false when tag not found (404)")
    void isSequencingEnabled_returnsFalseWhenTagNotFound() {
        when(restTemplate.getForObject(eq(EXPECTED_URL), eq(ToggleService.EntityTagResult[].class)))
                .thenThrow(HttpClientErrorException.NotFound.create(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Not Found", org.springframework.http.HttpHeaders.EMPTY,
                        new byte[0], null));

        boolean result = toggleService.isSequencingEnabled(DOMAIN_ID, SOLUTION_ID);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isSequencingEnabled returns false on timeout (ResourceAccessException)")
    void isSequencingEnabled_returnsFalseOnTimeout() {
        when(restTemplate.getForObject(eq(EXPECTED_URL), eq(ToggleService.EntityTagResult[].class)))
                .thenThrow(new ResourceAccessException("Read timed out"));

        boolean result = toggleService.isSequencingEnabled(DOMAIN_ID, SOLUTION_ID);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isSequencingEnabled caches result — RestTemplate called only once for repeated calls")
    void isSequencingEnabled_cachesResult() {
        when(restTemplate.getForObject(eq(EXPECTED_URL), eq(ToggleService.EntityTagResult[].class)))
                .thenReturn(new ToggleService.EntityTagResult[]{new ToggleService.EntityTagResult("true")});

        boolean first = toggleService.isSequencingEnabled(DOMAIN_ID, SOLUTION_ID);
        boolean second = toggleService.isSequencingEnabled(DOMAIN_ID, SOLUTION_ID);

        assertThat(first).isTrue();
        assertThat(second).isTrue();
        verify(restTemplate, times(1)).getForObject(eq(EXPECTED_URL), eq(ToggleService.EntityTagResult[].class));
    }

    @Test
    @DisplayName("isSequencingEnabled returns false on server error (5xx)")
    void isSequencingEnabled_returnsFalseOnServerError() {
        when(restTemplate.getForObject(eq(EXPECTED_URL), eq(ToggleService.EntityTagResult[].class)))
                .thenThrow(HttpServerErrorException.create(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error", org.springframework.http.HttpHeaders.EMPTY,
                        new byte[0], null));

        boolean result = toggleService.isSequencingEnabled(DOMAIN_ID, SOLUTION_ID);

        assertThat(result).isFalse();
    }
}
