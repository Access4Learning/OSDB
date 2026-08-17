// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.test.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test security configuration that disables security for unit tests.
 * 
 * <p>This configuration is used in controller tests to bypass authentication
 * and authorization checks, allowing tests to focus on business logic
 * rather than security concerns.</p>
 * 
 * <p>Note: This is NOT a @TestConfiguration to avoid automatic detection.
 * It must be explicitly imported using @Import(TestSecurityConfiguration.class)
 * in test classes that need it.</p>
 * 
 * <p>This configuration is only active when the "disable-security" profile is active.</p>
 */
@Configuration
@EnableWebSecurity
@org.springframework.context.annotation.Profile("disable-security")
public class TestSecurityConfiguration {
    
    /**
     * Creates a security filter chain that permits all requests.
     * This overrides the main security configuration for tests.
     * 
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        
        return http.build();
    }
}