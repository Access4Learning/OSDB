// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.config;

import org.sifunity.broker.common.filter.SwaggerBasicAuthAdapter;
import org.sifunity.broker.environment.filter.AdminSessionFilter;
import org.sifunity.broker.environment.filter.AuthorizationFilter;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.UserAuthenticationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the Environment Service.
 * 
 * <p>This configuration provides security settings for the Environment Service including
 * role-based authorization enforcement via the AuthorizationFilter and admin session
 * validation via the AdminSessionFilter.</p>
 * 
 * <p>Security rules:</p>
 * <ul>
 *   <li>POST /environments - No authentication required (registration endpoint)</li>
 *   <li>/internal/** - No authentication required (internal APIs for Gateway)</li>
 *   <li>/environments/** - Authentication and authorization required</li>
 *   <li>/acl-entries/** - Authentication and authorization required</li>
 *   <li>/admin-users/sessions/** - Admin session token required (X-Admin-Session-Token header)</li>
 *   <li>/admin/tag-definitions/** - Admin session token required (X-Admin-Session-Token header)</li>
 *   <li>/admin-users/login - No authentication required</li>
 *   <li>/admin-users/** (CRUD, environments) - HMAC authentication required</li>
 *   <li>Actuator health endpoints - No authentication required</li>
 * </ul>
 * 
 * <p><strong>Requirements:</strong> 3.1, 5.7, 7.4</p>
 */
@Configuration
@EnableWebSecurity
@org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(name = "testSecurityFilterChain")
public class SecurityConfiguration {
    
    private final AuthorizationService authorizationService;
    private final EnvironmentRepository environmentRepository;
    private final AclEntryRepository aclEntryRepository;
    private final SolutionRepository solutionRepository;
    private final org.sifunity.broker.environment.repository.DomainRepository domainRepository;
    private final Environment environment;
    private final UserAuthenticationService userAuthenticationService;

    @org.springframework.beans.factory.annotation.Value("${broker.internal-service-token:}")
    private String internalServiceToken;
    
    public SecurityConfiguration(
            AuthorizationService authorizationService,
            EnvironmentRepository environmentRepository,
            AclEntryRepository aclEntryRepository,
            SolutionRepository solutionRepository,
            org.sifunity.broker.environment.repository.DomainRepository domainRepository,
            Environment environment,
            UserAuthenticationService userAuthenticationService) {
        this.authorizationService = authorizationService;
        this.environmentRepository = environmentRepository;
        this.aclEntryRepository = aclEntryRepository;
        this.solutionRepository = solutionRepository;
        this.domainRepository = domainRepository;
        this.environment = environment;
        this.userAuthenticationService = userAuthenticationService;
    }

    /**
     * Configures the security filter chain for HTTP requests.
     * 
     * <p>Filter order (earliest to latest):
     * <ol>
     *   <li>SwaggerBasicAuthAdapter (non-prod only) — handles Swagger UI basic auth</li>
     *   <li>AdminSessionFilter — validates X-Admin-Session-Token for /admin-users/sessions/** endpoints</li>
     *   <li>AuthorizationFilter — validates HMAC Authorization header for all other protected endpoints</li>
     * </ol>
     * 
     * <p>The AdminSessionFilter only applies to session-protected endpoints
     * ({@code /admin-users/sessions/**}). The login endpoint ({@code POST /admin-users/login})
     * and HMAC-authenticated admin user CRUD endpoints are handled by the AuthorizationFilter.</p>
     * 
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        AuthorizationFilter authorizationFilter = new AuthorizationFilter(
                authorizationService, environmentRepository, aclEntryRepository,
                solutionRepository, domainRepository, internalServiceToken);
        
        AdminSessionFilter adminSessionFilter = new AdminSessionFilter(userAuthenticationService);
        
        http
            // Disable CSRF for API service
            .csrf(csrf -> csrf.disable())
            
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // All authorization is handled by our custom filters
                .anyRequest().permitAll()
            )
            
            // Add AdminSessionFilter before AuthorizationFilter — it handles /admin-users/sessions/** only
            .addFilterBefore(adminSessionFilter, UsernamePasswordAuthenticationFilter.class)
            
            // Add AuthorizationFilter after AdminSessionFilter
            .addFilterAfter(authorizationFilter, AdminSessionFilter.class);
        
        // In non-prod profiles, add Swagger Basic Auth adapter before both custom filters
        if (!java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            http.addFilterBefore(new SwaggerBasicAuthAdapter(), AdminSessionFilter.class);
        }
        
        return http.build();
    }
}