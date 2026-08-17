// SPDX-License-Identifier: MIT
package org.sifunity.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;

/**
 * Configuration for Gateway routes to backend microservices.
 * 
 * <p>This configuration defines routing rules for all external requests to the SIF Unity Broker.
 * Routes are organized by service type:
 * <ul>
 *   <li>Environment Service: /environments, /acl-entries, /providers, /zones</li>
 *   <li>Connector Services: /requests, /responses, /events, /services</li>
 *   <li>Core Services: /queues, /subscriptions, /jobs</li>
 * </ul>
 * 
 * <p>Internal APIs (/internal/*) are blocked by InternalApiBlockingFilter (Order 0) which runs
 * before all other filters across all profiles. This configuration also includes a defense-in-depth
 * route that blocks /internal/* paths, though it is redundant with the filter in non-test profiles.
 * 
 * <p>All routes use load balancer URIs (lb://service-name) for service discovery and load balancing.
 * 
 * <p><strong>Note:</strong> This configuration is disabled in test profile. In test mode, routes are
 * configured via application-test.yml with explicit URLs instead of service discovery.
 * 
 * @see org.springframework.cloud.gateway.route.RouteLocator
 * @see org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
 */
@Configuration
@Profile("!test & !local")  // Disable this configuration in test and local profiles
public class GatewayRouteConfiguration {
    
    /**
     * Defines custom routes for the Gateway.
     * 
     * <p>Routes are processed in order. The internal API blocking route is defined first
     * to ensure internal endpoints are never exposed externally.
     * 
     * <p>Requirements:
     * <ul>
     *   <li>5.1: Route /environments to Environment Service</li>
     *   <li>5.2: Route /acl-entries to Environment Service</li>
     *   <li>5.3: Route /providers to Environment Service</li>
     *   <li>5.4: Route /zones to Environment Service</li>
     *   <li>5.5: Route /requests to Requests Connector</li>
     *   <li>5.6: Route /responses to Responses Connector</li>
     *   <li>5.7: Route /events to Events Connector</li>
     *   <li>5.8: Route /services to Services Connector</li>
     *   <li>5.9: Route /queues to Queue Service</li>
     *   <li>5.10: Route /subscriptions to Subscription Service</li>
     *   <li>5.11: Route /jobs to Job Service</li>
     *   <li>6.1: Block internal APIs (/internal/*)</li>
     *   <li>6.2: Return 403 for internal API requests</li>
     * </ul>
     * 
     * @param builder the route locator builder
     * @return the configured route locator
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // Block internal APIs - defense-in-depth mechanism
            // NOTE: InternalApiBlockingFilter (Order 0) provides the primary blocking mechanism
            // for /internal/* paths across all profiles. This route serves as defense-in-depth
            // for non-test profiles where this configuration is active.
            // Requirements: 6.1, 6.2
            .route("block-internal", r -> r
                .path("/internal/**")
                .filters(f -> f.setStatus(HttpStatus.FORBIDDEN))
                .uri("no://op"))
            
            // Environment Service routes
            // Requirements: 5.1, 5.2, 5.3, 5.4
            .route("environment-service", r -> r
                .path("/environments/**", "/acl-entries/**", 
                      "/providers/**", "/zones/**")
                .uri("lb://environment-service"))
            
            // Connector Service routes
            // Requirement: 5.5
            .route("requests-connector", r -> r
                .path("/requests/**")
                .uri("lb://requests-connector"))
            
            // Requirement: 5.6
            .route("responses-connector", r -> r
                .path("/responses/**")
                .uri("lb://responses-connector"))
            
            // Requirement: 5.7
            .route("events-connector", r -> r
                .path("/events/**")
                .uri("lb://events-connector"))
            
            // Requirement: 5.8
            .route("services-connector", r -> r
                .path("/services/**")
                .uri("lb://services-connector"))
            
            // Core Service routes
            // Requirement: 5.9
            .route("queue-service", r -> r
                .path("/queues/**")
                .uri("lb://queue-service"))
            
            // Requirement: 5.10
            .route("subscription-service", r -> r
                .path("/subscriptions/**")
                .uri("lb://subscription-service"))
            
            // Requirement: 5.11
            .route("job-service", r -> r
                .path("/jobs/**")
                .uri("lb://job-service"))
            
            .build();
    }
}
