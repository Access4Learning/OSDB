// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.dto;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProviderRawResponse DTO.
 *
 * Tests verify:
 * - Builder pattern construction
 * - Field accessors and mutators
 * - Lombok-generated methods (equals, hashCode, toString)
 * - Default values and null handling
 */
class ProviderRawResponseTest {

    @Test
    void testBuilderCreatesInstanceWithAllFields() {
        // Arrange
        Object body = Map.of("students", "data");
        Map<String, String> headers = Map.of(
            "navigationPage", "1",
            "navigationLastPage", "5",
            "navigationId", "abc-123"
        );
        int statusCode = 200;

        // Act
        ProviderRawResponse response = ProviderRawResponse.builder()
            .body(body)
            .headers(headers)
            .statusCode(statusCode)
            .build();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isEqualTo(body);
        assertThat(response.getHeaders()).isEqualTo(headers);
        assertThat(response.getStatusCode()).isEqualTo(statusCode);
    }

    @Test
    void testNoArgsConstructorCreatesInstanceWithDefaults() {
        // Act
        ProviderRawResponse response = new ProviderRawResponse();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders()).isNull();
        assertThat(response.getStatusCode()).isEqualTo(0); // int defaults to 0
    }

    @Test
    void testAllArgsConstructorCreatesInstance() {
        // Arrange
        Object body = "response-body";
        Map<String, String> headers = Map.of("Content-Type", "application/json");
        int statusCode = 404;

        // Act
        ProviderRawResponse response = new ProviderRawResponse(body, headers, statusCode);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isEqualTo(body);
        assertThat(response.getHeaders()).isEqualTo(headers);
        assertThat(response.getStatusCode()).isEqualTo(statusCode);
    }

    @Test
    void testSettersModifyFields() {
        // Arrange
        ProviderRawResponse response = new ProviderRawResponse();
        Object body = Map.of("key", "value");
        Map<String, String> headers = Map.of("navigationPage", "3");
        int statusCode = 200;

        // Act
        response.setBody(body);
        response.setHeaders(headers);
        response.setStatusCode(statusCode);

        // Assert
        assertThat(response.getBody()).isEqualTo(body);
        assertThat(response.getHeaders()).isEqualTo(headers);
        assertThat(response.getStatusCode()).isEqualTo(statusCode);
    }

    @Test
    void testEqualsAndHashCode() {
        // Arrange
        Object body = "same-body";
        Map<String, String> headers = Map.of("navigationPage", "1");
        int statusCode = 200;

        ProviderRawResponse response1 = ProviderRawResponse.builder()
            .body(body)
            .headers(headers)
            .statusCode(statusCode)
            .build();

        ProviderRawResponse response2 = ProviderRawResponse.builder()
            .body(body)
            .headers(headers)
            .statusCode(statusCode)
            .build();

        // Assert
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    void testNotEqualsWithDifferentStatusCode() {
        // Arrange
        ProviderRawResponse response1 = ProviderRawResponse.builder()
            .body("body")
            .statusCode(200)
            .build();

        ProviderRawResponse response2 = ProviderRawResponse.builder()
            .body("body")
            .statusCode(500)
            .build();

        // Assert
        assertThat(response1).isNotEqualTo(response2);
    }

    @Test
    void testNotEqualsWithDifferentHeaders() {
        // Arrange
        ProviderRawResponse response1 = ProviderRawResponse.builder()
            .headers(Map.of("navigationPage", "1"))
            .statusCode(200)
            .build();

        ProviderRawResponse response2 = ProviderRawResponse.builder()
            .headers(Map.of("navigationPage", "2"))
            .statusCode(200)
            .build();

        // Assert
        assertThat(response1).isNotEqualTo(response2);
    }

    @Test
    void testToStringContainsAllFields() {
        // Arrange
        ProviderRawResponse response = ProviderRawResponse.builder()
            .body("test-body")
            .headers(Map.of("key", "val"))
            .statusCode(200)
            .build();

        // Act
        String toString = response.toString();

        // Assert
        assertThat(toString).contains("ProviderRawResponse");
        assertThat(toString).contains("body=test-body");
        assertThat(toString).contains("statusCode=200");
    }

    @Test
    void testBuilderWithPartialFields() {
        // Act - Build with only statusCode
        ProviderRawResponse response = ProviderRawResponse.builder()
            .statusCode(204)
            .build();

        // Assert
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders()).isNull();
        assertThat(response.getStatusCode()).isEqualTo(204);
    }

    @Test
    void testBuilderWithNullBody() {
        // Act
        ProviderRawResponse response = ProviderRawResponse.builder()
            .body(null)
            .headers(Map.of("navigationPage", "1"))
            .statusCode(200)
            .build();

        // Assert
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    @Test
    void testSettersOverwriteBuilderValues() {
        // Arrange
        ProviderRawResponse response = ProviderRawResponse.builder()
            .body("original")
            .statusCode(200)
            .build();

        // Act
        response.setBody("updated");
        response.setStatusCode(201);

        // Assert
        assertThat(response.getBody()).isEqualTo("updated");
        assertThat(response.getStatusCode()).isEqualTo(201);
    }
}
