// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.TokenValidationRequest;
import org.sifunity.broker.environment.dto.TokenValidationResponse;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService.
 * 
 * <p>Tests the business logic for token validation using mocked dependencies
 * to isolate the service layer from database and external dependencies.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationService Unit Tests")
class AuthenticationServiceUnitTest {
    
    @Mock
    private EnvironmentService environmentService;
    
    @Mock
    private SolutionService solutionService;
    
    @Mock
    private DomainService domainService;
    
    private AuthenticationService authenticationService;
    
    private Environment testEnvironment;
    private String sessionToken;
    private String sharedSecret;
    private String timestamp;
    
    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(environmentService, solutionService, domainService);
        
        // Create test data
        sessionToken = UUID.randomUUID().toString();
        sharedSecret = "test-shared-secret-256-bits-long-for-hmac-validation";
        timestamp = Instant.now().toString();
        
        testEnvironment = new Environment();
        testEnvironment.setId(UUID.randomUUID());
        testEnvironment.setApplicationName("test-app");
        testEnvironment.setSessionToken(sessionToken);
        testEnvironment.setSharedSecret(sharedSecret);
        testEnvironment.setType(EnvironmentType.BROKERED);
        testEnvironment.setDefaultZoneId("test-zone");
        testEnvironment.setCreatedAt(Instant.now());
    }
    
    @Nested
    @DisplayName("Valid Token Validation")
    class ValidTokenValidationTests {
        
        @Test
        @DisplayName("Should validate token successfully with correct HMAC")
        void shouldValidateTokenSuccessfully() {
            // Given
            String validHash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
            TokenValidationRequest request = new TokenValidationRequest(sessionToken, timestamp, validHash);
            
            when(environmentService.findBySessionToken(sessionToken)).thenReturn(testEnvironment);
            when(environmentService.getSharedSecret(testEnvironment.getId())).thenReturn(sharedSecret);
            
            // When
            TokenValidationResponse response = authenticationService.validateToken(request);
            
            // Then
            assertThat(response).isNotNull();
            assertThat(response.environmentId()).isEqualTo(testEnvironment.getId());
            assertThat(response.applicationName()).isEqualTo(testEnvironment.getApplicationName());
            assertThat(response.type()).isEqualTo(testEnvironment.getType());
            assertThat(response.defaultZoneId()).isEqualTo(testEnvironment.getDefaultZoneId());
            
            verify(environmentService).findBySessionToken(sessionToken);
            verify(environmentService).getSharedSecret(testEnvironment.getId());
        }
        
        @Test
        @DisplayName("Should handle different environment types correctly")
        void shouldHandleDifferentEnvironmentTypes() {
            // Given
            testEnvironment.setType(EnvironmentType.BROKERED);
            String validHash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
            TokenValidationRequest request = new TokenValidationRequest(sessionToken, timestamp, validHash);
            
            when(environmentService.findBySessionToken(sessionToken)).thenReturn(testEnvironment);
            when(environmentService.getSharedSecret(testEnvironment.getId())).thenReturn(sharedSecret);
            
            // When
            TokenValidationResponse response = authenticationService.validateToken(request);
            
            // Then
            assertThat(response.type()).isEqualTo(EnvironmentType.BROKERED);
        }
        
        @Test
        @DisplayName("Should handle null default context gracefully")
        void shouldHandleNullDefaultContextGracefully() {
            // Given
            String validHash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
            TokenValidationRequest request = new TokenValidationRequest(sessionToken, timestamp, validHash);
            
            when(environmentService.findBySessionToken(sessionToken)).thenReturn(testEnvironment);
            when(environmentService.getSharedSecret(testEnvironment.getId())).thenReturn(sharedSecret);
            
            // When
            TokenValidationResponse response = authenticationService.validateToken(request);
            
            // Then - response is valid even without defaultContextId field
            assertThat(response).isNotNull();
            assertThat(response.environmentId()).isEqualTo(testEnvironment.getId());
        }
        
        @Test
        @DisplayName("Should validate token with timestamp within tolerance")
        void shouldValidateTokenWithTimestampWithinTolerance() {
            // Given - timestamp from 2 minutes ago (within default 5-minute tolerance)
            String recentTimestamp = Instant.now().minusSeconds(120).toString();
            String validHash = SifAuthenticationUtils.computeHmac(sessionToken, recentTimestamp, sharedSecret);
            TokenValidationRequest request = new TokenValidationRequest(sessionToken, recentTimestamp, validHash);
            
            when(environmentService.findBySessionToken(sessionToken)).thenReturn(testEnvironment);
            when(environmentService.getSharedSecret(testEnvironment.getId())).thenReturn(sharedSecret);
            
            // When
            TokenValidationResponse response = authenticationService.validateToken(request);
            
            // Then
            assertThat(response).isNotNull();
            assertThat(response.environmentId()).isEqualTo(testEnvironment.getId());
        }
    }
    
    @Nested
    @DisplayName("Invalid Token Validation")
    class InvalidTokenValidationTests {
        
        @Test
        @DisplayName("Should throw InvalidTokenException for non-existent session token")
        void shouldThrowExceptionForNonExistentSessionToken() {
            // Given
            String nonExistentToken = "non-existent-token";
            String validHash = SifAuthenticationUtils.computeHmac(nonExistentToken, timestamp, sharedSecret);
            TokenValidationRequest request = new TokenValidationRequest(nonExistentToken, timestamp, validHash);
            
            when(environmentService.findBySessionToken(nonExistentToken)).thenReturn(null);
            
            // When & Then
            assertThatThrownBy(() -> authenticationService.validateToken(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("SessionToken does not exist");
            
            verify(environmentService).findBySessionToken(nonExistentToken);
        }
        
        @Test
        @DisplayName("Should throw InvalidTokenException for invalid HMAC")
        void shouldThrowExceptionForInvalidHmac() {
            // Given
            String invalidHash = "invalid-hash";
            TokenValidationRequest request = new TokenValidationRequest(sessionToken, timestamp, invalidHash);
            
            when(environmentService.findBySessionToken(sessionToken)).thenReturn(testEnvironment);
            when(environmentService.getSharedSecret(testEnvironment.getId())).thenReturn(sharedSecret);
            
            // When & Then
            assertThatThrownBy(() -> authenticationService.validateToken(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("HMAC signature validation failed");
            
            verify(environmentService).findBySessionToken(sessionToken);
            verify(environmentService).getSharedSecret(testEnvironment.getId());
        }
        
        @Test
        @DisplayName("Should throw InvalidTokenException for old timestamp")
        void shouldThrowExceptionForOldTimestamp() {
            // Given - timestamp from 10 minutes ago (outside default 5-minute tolerance)
            String oldTimestamp = Instant.now().minusSeconds(600).toString();
            String validHash = SifAuthenticationUtils.computeHmac(sessionToken, oldTimestamp, sharedSecret);
            TokenValidationRequest request = new TokenValidationRequest(sessionToken, oldTimestamp, validHash);
            
            when(environmentService.findBySessionToken(sessionToken)).thenReturn(testEnvironment);
            
            // When & Then
            assertThatThrownBy(() -> authenticationService.validateToken(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Timestamp is outside tolerance window");
            
            verify(environmentService).findBySessionToken(sessionToken);
        }
        
        @Test
        @DisplayName("Should throw InvalidTokenException for future timestamp")
        void shouldThrowExceptionForFutureTimestamp() {
            // Given - timestamp from 10 minutes in the future (outside default 5-minute tolerance)
            String futureTimestamp = Instant.now().plusSeconds(600).toString();
            String validHash = SifAuthenticationUtils.computeHmac(sessionToken, futureTimestamp, sharedSecret);
            TokenValidationRequest request = new TokenValidationRequest(sessionToken, futureTimestamp, validHash);
            
            when(environmentService.findBySessionToken(sessionToken)).thenReturn(testEnvironment);
            
            // When & Then
            assertThatThrownBy(() -> authenticationService.validateToken(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Timestamp is outside tolerance window");
            
            verify(environmentService).findBySessionToken(sessionToken);
        }
        
        @Test
        @DisplayName("Should throw InvalidTokenException for invalid timestamp format")
        void shouldThrowExceptionForInvalidTimestampFormat() {
            // Given
            String invalidTimestamp = "not-a-valid-timestamp";
            String validHash = SifAuthenticationUtils.computeHmac(sessionToken, invalidTimestamp, sharedSecret);
            TokenValidationRequest request = new TokenValidationRequest(sessionToken, invalidTimestamp, validHash);
            
            when(environmentService.findBySessionToken(sessionToken)).thenReturn(testEnvironment);
            
            // When & Then
            assertThatThrownBy(() -> authenticationService.validateToken(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Timestamp is outside tolerance window");
            
            verify(environmentService).findBySessionToken(sessionToken);
        }
    }
}