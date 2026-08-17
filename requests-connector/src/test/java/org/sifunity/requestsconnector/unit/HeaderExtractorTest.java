// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.requestsconnector.exception.MissingHeaderException;
import org.sifunity.requestsconnector.util.HeaderExtractor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for HeaderExtractor.
 * 
 * <p>Tests the extraction of environment context from HTTP headers,
 * including validation and error handling.</p>
 * 
 * <p>Requirements: 8.1, 8.2, 8.3</p>
 */
class HeaderExtractorTest {
    
    private HeaderExtractor headerExtractor;
    private MockHttpServletRequest request;
    
    @BeforeEach
    void setUp() {
        headerExtractor = new HeaderExtractor();
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    
    @Test
    void extractEnvironmentId_shouldReturnValidUUID_whenHeaderPresent() {
        // Given: A request with X-Environment-Id header
        UUID expectedId = UUID.randomUUID();
        request.addHeader("X-Environment-Id", expectedId.toString());
        
        // When: Extracting environment ID
        UUID actualId = headerExtractor.extractEnvironmentId();
        
        // Then: Should return the correct UUID
        assertThat(actualId).isEqualTo(expectedId);
    }
    
    @Test
    void extractEnvironmentId_shouldThrowException_whenHeaderMissing() {
        // Given: A request without X-Environment-Id header
        // (no header added)
        
        // When/Then: Should throw MissingHeaderException
        assertThatThrownBy(() -> headerExtractor.extractEnvironmentId())
            .isInstanceOf(MissingHeaderException.class)
            .hasMessageContaining("X-Environment-Id header is required");
    }
    
    @Test
    void extractEnvironmentId_shouldThrowException_whenHeaderEmpty() {
        // Given: A request with empty X-Environment-Id header
        request.addHeader("X-Environment-Id", "");
        
        // When/Then: Should throw MissingHeaderException
        assertThatThrownBy(() -> headerExtractor.extractEnvironmentId())
            .isInstanceOf(MissingHeaderException.class)
            .hasMessageContaining("X-Environment-Id header is required");
    }
    
    @Test
    void extractEnvironmentId_shouldThrowException_whenHeaderBlank() {
        // Given: A request with blank X-Environment-Id header
        request.addHeader("X-Environment-Id", "   ");
        
        // When/Then: Should throw MissingHeaderException
        assertThatThrownBy(() -> headerExtractor.extractEnvironmentId())
            .isInstanceOf(MissingHeaderException.class)
            .hasMessageContaining("X-Environment-Id header is required");
    }
    
    @Test
    void extractEnvironmentId_shouldThrowException_whenHeaderNotValidUUID() {
        // Given: A request with invalid UUID in X-Environment-Id header
        request.addHeader("X-Environment-Id", "not-a-valid-uuid");
        
        // When/Then: Should throw MissingHeaderException
        assertThatThrownBy(() -> headerExtractor.extractEnvironmentId())
            .isInstanceOf(MissingHeaderException.class)
            .hasMessageContaining("X-Environment-Id header must be a valid UUID");
    }
    
    @Test
    void extractEnvironmentName_shouldReturnValue_whenHeaderPresent() {
        // Given: A request with X-Environment-Name header
        String expectedName = "ProductionEnvironment";
        request.addHeader("X-Environment-Name", expectedName);
        
        // When: Extracting environment name
        String actualName = headerExtractor.extractEnvironmentName();
        
        // Then: Should return the correct name
        assertThat(actualName).isEqualTo(expectedName);
    }
    
    @Test
    void extractEnvironmentName_shouldThrowException_whenHeaderMissing() {
        // Given: A request without X-Environment-Name header
        // (no header added)
        
        // When/Then: Should throw MissingHeaderException
        assertThatThrownBy(() -> headerExtractor.extractEnvironmentName())
            .isInstanceOf(MissingHeaderException.class)
            .hasMessageContaining("X-Environment-Name header is required");
    }
    
    @Test
    void extractEnvironmentName_shouldThrowException_whenHeaderEmpty() {
        // Given: A request with empty X-Environment-Name header
        request.addHeader("X-Environment-Name", "");
        
        // When/Then: Should throw MissingHeaderException
        assertThatThrownBy(() -> headerExtractor.extractEnvironmentName())
            .isInstanceOf(MissingHeaderException.class)
            .hasMessageContaining("X-Environment-Name header is required");
    }
    
    @Test
    void extractEnvironmentName_shouldThrowException_whenHeaderBlank() {
        // Given: A request with blank X-Environment-Name header
        request.addHeader("X-Environment-Name", "   ");
        
        // When/Then: Should throw MissingHeaderException
        assertThatThrownBy(() -> headerExtractor.extractEnvironmentName())
            .isInstanceOf(MissingHeaderException.class)
            .hasMessageContaining("X-Environment-Name header is required");
    }
    
    @Test
    void extractEnvironmentName_shouldHandleSpecialCharacters() {
        // Given: A request with environment name containing special characters
        String expectedName = "Test-Environment_123";
        request.addHeader("X-Environment-Name", expectedName);
        
        // When: Extracting environment name
        String actualName = headerExtractor.extractEnvironmentName();
        
        // Then: Should return the name with special characters preserved
        assertThat(actualName).isEqualTo(expectedName);
    }
    
    @Test
    void extractEnvironmentName_shouldHandleSpaces() {
        // Given: A request with environment name containing spaces
        String expectedName = "Production Environment";
        request.addHeader("X-Environment-Name", expectedName);
        
        // When: Extracting environment name
        String actualName = headerExtractor.extractEnvironmentName();
        
        // Then: Should return the name with spaces preserved
        assertThat(actualName).isEqualTo(expectedName);
    }
    
    @Test
    void extractBothHeaders_shouldSucceed_whenBothPresent() {
        // Given: A request with both headers
        UUID expectedId = UUID.randomUUID();
        String expectedName = "TestEnvironment";
        request.addHeader("X-Environment-Id", expectedId.toString());
        request.addHeader("X-Environment-Name", expectedName);
        
        // When: Extracting both headers
        UUID actualId = headerExtractor.extractEnvironmentId();
        String actualName = headerExtractor.extractEnvironmentName();
        
        // Then: Both should be extracted correctly
        assertThat(actualId).isEqualTo(expectedId);
        assertThat(actualName).isEqualTo(expectedName);
    }
    
    @Test
    void extractEnvironmentId_shouldHandleUppercaseUUID() {
        // Given: A request with uppercase UUID
        UUID expectedId = UUID.randomUUID();
        request.addHeader("X-Environment-Id", expectedId.toString().toUpperCase());
        
        // When: Extracting environment ID
        UUID actualId = headerExtractor.extractEnvironmentId();
        
        // Then: Should parse correctly (UUID.fromString handles case-insensitive)
        assertThat(actualId).isEqualTo(expectedId);
    }
    
    @Test
    void extractEnvironmentId_shouldHandleMixedCaseUUID() {
        // Given: A request with mixed case UUID
        String uuidString = "A1B2C3D4-E5F6-7890-ABCD-EF1234567890";
        request.addHeader("X-Environment-Id", uuidString);
        
        // When: Extracting environment ID
        UUID actualId = headerExtractor.extractEnvironmentId();
        
        // Then: Should parse correctly
        assertThat(actualId.toString().toUpperCase()).isEqualTo(uuidString);
    }
}
