// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.AuthorizationService;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for AuthorizationFilter.
 * 
 * <p>Tests credential extraction, authorization checks, and error responses.</p>
 * 
 * <p><strong>Requirements:</strong> 5.5</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationFilterTest {

    private static final String TEST_DOMAIN = "test-domain";
    private static final String TEST_SOLUTION = "test-solution";
    private static final String ENV_PREFIX = "/" + TEST_DOMAIN + "/" + TEST_SOLUTION;
    private static final String ACL_PREFIX = "/" + TEST_DOMAIN + "/" + TEST_SOLUTION;
    
    @Mock
    private AuthorizationService authorizationService;
    
    @Mock
    private EnvironmentRepository environmentRepository;
    
    @Mock
    private AclEntryRepository aclEntryRepository;
    
    @Mock
    private SolutionRepository solutionRepository;
    
    @Mock
    private org.sifunity.broker.environment.repository.DomainRepository domainRepository;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private FilterChain filterChain;
    
    private AuthorizationFilter filter;
    private StringWriter responseWriter;
    
    @BeforeEach
    void setUp() throws IOException {
        filter = new AuthorizationFilter(authorizationService, environmentRepository, aclEntryRepository, solutionRepository, domainRepository, "test-internal-token");
        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }
    
    /** Builds a properly Base64-encoded SIF auth header from a session token. */
    private static String buildAuthHeader(String sessionToken) {
        String credentials = sessionToken + ":signature";
        String base64 = java.util.Base64.getEncoder().encodeToString(
                credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "SIF_HMACSHA256 " + base64;
    }
    
    // ========== Tests for credential extraction ==========
    
    @Test
    void doFilterInternal_WithMissingAuthorizationHeader_Returns401() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(null);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseWriter.toString()).contains("Authentication required");
        verify(filterChain, never()).doFilter(any(), any());
    }
    
    @Test
    void doFilterInternal_WithEmptyAuthorizationHeader_Returns401() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("   ");
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseWriter.toString()).contains("Authentication required");
        verify(filterChain, never()).doFilter(any(), any());
    }
    
    @Test
    void doFilterInternal_WithInvalidAuthorizationFormat_Returns401() throws ServletException, IOException {
        // Given
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseWriter.toString()).contains("Invalid credentials");
        verify(filterChain, never()).doFilter(any(), any());
    }
    
    @Test
    void doFilterInternal_WithInvalidSessionToken_Returns401() throws ServletException, IOException {
        // Given
        String sessionToken = "invalid-session-token";
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.empty());
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseWriter.toString()).contains("Invalid credentials");
        verify(filterChain, never()).doFilter(any(), any());
    }
    
    @Test
    void doFilterInternal_WithValidCredentials_ExtractsSessionToken() throws ServletException, IOException {
        // Given
        String sessionToken = "valid-session-token";
        Environment admin = createEnvironment(EnvironmentType.BROKERED);
        
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(admin));
        when(authorizationService.canCreateEnvironment(admin)).thenReturn(true);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(environmentRepository).findBySessionToken(sessionToken);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_SingularCreatePath_AuthorizedAsCreate() throws ServletException, IOException {
        // Given: SIF 3.7 singular-subpath create POST /environments/environment
        String sessionToken = "admin-token";
        Environment admin = createEnvironment(EnvironmentType.BROKERED);
        
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments/environment");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(admin));
        when(authorizationService.canCreateEnvironment(admin)).thenReturn(true);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then: treated as a create (canCreateEnvironment), not an operation on id "environment"
        verify(authorizationService).canCreateEnvironment(admin);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_SingularCreatePath_UnauthorizedReturns403() throws ServletException, IOException {
        // Given: consumer attempts the singular-subpath create
        String sessionToken = "consumer-token";
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments/environment");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(consumer));
        when(authorizationService.canCreateEnvironment(consumer)).thenReturn(false);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertThat(responseWriter.toString()).contains("Insufficient permissions");
        verify(filterChain, never()).doFilter(any(), any());
    }
    
    @Test
    void doFilterInternal_WithUnauthorizedOperation_Returns403() throws ServletException, IOException {
        // Given
        String sessionToken = "consumer-token";
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(consumer));
        when(authorizationService.canCreateEnvironment(consumer)).thenReturn(false);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertThat(responseWriter.toString()).contains("Insufficient permissions");
        verify(filterChain, never()).doFilter(any(), any());
    }
    
    @Test
    void doFilterInternal_WithAuthorizedOperation_AllowsRequest() throws ServletException, IOException {
        // Given
        String sessionToken = "admin-token";
        Environment admin = createEnvironment(EnvironmentType.BROKERED);
        
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(admin));
        when(authorizationService.canCreateEnvironment(admin)).thenReturn(true);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_StoresRequesterInRequestAttribute() throws ServletException, IOException {
        // Given
        String sessionToken = "admin-token";
        Environment admin = createEnvironment(EnvironmentType.BROKERED);
        
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(admin));
        when(authorizationService.canCreateEnvironment(admin)).thenReturn(true);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(request).setAttribute("requester", admin);
        verify(filterChain).doFilter(request, response);
    }
    
    // ========== Tests for environment operations ==========
    
    @Test
    void doFilterInternal_CreateEnvironment_ChecksCanCreateEnvironment() throws ServletException, IOException {
        // Given
        String sessionToken = "admin-token";
        Environment admin = createEnvironment(EnvironmentType.BROKERED);
        
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(admin));
        when(authorizationService.canCreateEnvironment(admin)).thenReturn(true);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(authorizationService).canCreateEnvironment(admin);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_ReadEnvironment_ChecksCanReadEnvironment() throws ServletException, IOException {
        // Given
        String sessionToken = "consumer-token";
        UUID targetId = UUID.randomUUID();
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        Environment target = createEnvironment(EnvironmentType.BROKERED);
        target.setId(targetId);
        
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments/" + targetId);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(consumer));
        when(environmentRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(authorizationService.canReadEnvironment(consumer, target)).thenReturn(true);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(authorizationService).canReadEnvironment(consumer, target);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_UpdateEnvironment_ChecksCanUpdateEnvironment() throws ServletException, IOException {
        // Given
        String sessionToken = "consumer-token";
        UUID targetId = UUID.randomUUID();
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        Environment target = createEnvironment(EnvironmentType.BROKERED);
        target.setId(targetId);
        
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments/" + targetId);
        when(request.getMethod()).thenReturn("PUT");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(consumer));
        when(environmentRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(authorizationService.canUpdateEnvironment(consumer, target)).thenReturn(true);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(authorizationService).canUpdateEnvironment(consumer, target);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_DeleteEnvironment_ChecksCanDeleteEnvironment() throws ServletException, IOException {
        // Given
        String sessionToken = "admin-token";
        UUID targetId = UUID.randomUUID();
        Environment admin = createEnvironment(EnvironmentType.BROKERED);
        Environment target = createEnvironment(EnvironmentType.BROKERED);
        target.setId(targetId);
        
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments/" + targetId);
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(admin));
        when(environmentRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(authorizationService.canDeleteEnvironment(admin, target)).thenReturn(true);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(authorizationService).canDeleteEnvironment(admin, target);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_ListEnvironments_AllowsAdminRequest() throws ServletException, IOException {
        // Given - listing environments requires admin
        String sessionToken = "admin-token";
        UUID solutionUuid = UUID.randomUUID();
        UUID domainUuid = UUID.randomUUID();
        Environment admin = createEnvironment(EnvironmentType.BROKERED);
        admin.setSolutionUuid(solutionUuid);
        
        org.sifunity.broker.environment.domain.Solution solution = new org.sifunity.broker.environment.domain.Solution();
        solution.setId(solutionUuid);
        solution.setDomainUuid(domainUuid);
        solution.setSolutionId(org.sifunity.broker.common.constant.GlobalAdminConstants.GLOBAL_SOLUTION);
        
        org.sifunity.broker.environment.domain.Domain domain = new org.sifunity.broker.environment.domain.Domain();
        domain.setId(domainUuid);
        domain.setDomainId(org.sifunity.broker.common.constant.GlobalAdminConstants.GLOBAL_DOMAIN);
        
        when(request.getRequestURI()).thenReturn(ENV_PREFIX + "/environments");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(admin));
        when(solutionRepository.findById(solutionUuid)).thenReturn(Optional.of(solution));
        when(domainRepository.findById(domainUuid)).thenReturn(Optional.of(domain));
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(filterChain).doFilter(request, response);
    }
    
    // ========== Tests for ACL operations ==========
    
    @Test
    void doFilterInternal_CreateAcl_ChecksCanCreateAcl() throws ServletException, IOException {
        // Given
        String sessionToken = "admin-token";
        Environment admin = createEnvironment(EnvironmentType.BROKERED);
        
        when(request.getRequestURI()).thenReturn(ACL_PREFIX + "/acl-entries");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(admin));
        when(authorizationService.canCreateAcl(admin)).thenReturn(true);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(authorizationService).canCreateAcl(admin);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_ReadAcl_ChecksCanReadAcl() throws ServletException, IOException {
        // Given
        String sessionToken = "consumer-token";
        UUID aclId = UUID.randomUUID();
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        AclEntry acl = createAclEntry(consumer);
        
        when(request.getRequestURI()).thenReturn(ACL_PREFIX + "/acl-entries/" + aclId);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(consumer));
        when(aclEntryRepository.findById(aclId)).thenReturn(Optional.of(acl));
        when(authorizationService.canReadAcl(consumer, acl)).thenReturn(true);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(authorizationService).canReadAcl(consumer, acl);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_UpdateAcl_ChecksCanUpdateAcl() throws ServletException, IOException {
        // Given
        String sessionToken = "admin-token";
        UUID aclId = UUID.randomUUID();
        Environment admin = createEnvironment(EnvironmentType.BROKERED);
        AclEntry acl = createAclEntry(admin);
        
        when(request.getRequestURI()).thenReturn(ACL_PREFIX + "/acl-entries/" + aclId);
        when(request.getMethod()).thenReturn("PUT");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(admin));
        when(aclEntryRepository.findById(aclId)).thenReturn(Optional.of(acl));
        when(authorizationService.canUpdateAcl(admin, acl)).thenReturn(true);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(authorizationService).canUpdateAcl(admin, acl);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_DeleteAcl_ChecksCanDeleteAcl() throws ServletException, IOException {
        // Given
        String sessionToken = "admin-token";
        UUID aclId = UUID.randomUUID();
        Environment admin = createEnvironment(EnvironmentType.BROKERED);
        AclEntry acl = createAclEntry(admin);
        
        when(request.getRequestURI()).thenReturn(ACL_PREFIX + "/acl-entries/" + aclId);
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(admin));
        when(aclEntryRepository.findById(aclId)).thenReturn(Optional.of(acl));
        when(authorizationService.canDeleteAcl(admin, acl)).thenReturn(true);
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(authorizationService).canDeleteAcl(admin, acl);
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    void doFilterInternal_ListAcls_AllowsRequest() throws ServletException, IOException {
        // Given
        String sessionToken = "consumer-token";
        Environment consumer = createEnvironment(EnvironmentType.BROKERED);
        
        when(request.getRequestURI()).thenReturn(ACL_PREFIX + "/acl-entries");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(buildAuthHeader(sessionToken));
        when(environmentRepository.findBySessionToken(sessionToken)).thenReturn(Optional.of(consumer));
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then
        verify(filterChain).doFilter(request, response);
    }
    
    // ========== Tests for internal operations ==========
    
    @Test
    void doFilterInternal_InternalEndpoint_AllowsWithValidToken() throws ServletException, IOException {
        // Given - internal paths now go through doFilterInternal and require a valid token
        when(request.getRequestURI()).thenReturn("/internal/check-permission");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Internal-Service-Token")).thenReturn("test-internal-token");
        
        // When
        filter.doFilterInternal(request, response, filterChain);
        
        // Then - valid token allows the request through without further authz checks
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(authorizationService);
    }
    
    // ========== Helper methods ==========
    
    private Environment createEnvironment(EnvironmentType type) {
        Environment env = new Environment();
        env.setId(UUID.randomUUID());
        env.setType(type);
        env.setApplicationName("Test App");
        env.setSessionToken(UUID.randomUUID().toString());
        env.setSharedSecret("secret");
        env.setDefaultZoneId("default-zone");
        return env;
    }
    
    private AclEntry createAclEntry(Environment owner) {
        AclEntry acl = new AclEntry();
        acl.setId(UUID.randomUUID());
        acl.setEnvironment(owner);
        return acl;
    }
}
