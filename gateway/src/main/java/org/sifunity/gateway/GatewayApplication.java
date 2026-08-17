// SPDX-License-Identifier: MIT
package org.sifunity.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * API Gateway Application
 * 
 * The API Gateway is the single entry point for all external requests to the SIF Unity Broker.
 * It provides centralized authentication, authorization, and routing to backend microservices.
 * 
 * Key Responsibilities:
 * - Authentication: Validate sessionToken and HMAC signatures via Environment Service
 * - Authorization: Check ACL permissions via Environment Service
 * - Context Injection: Add environment context headers (X-Environment-Id, X-Environment-Name, X-Correlation-Id)
 * - Routing: Route requests to appropriate backend services based on path
 * - Rate Limiting: Enforce request rate limits per environment
 * - Error Handling: Provide consistent error responses
 * - Observability: Log all auth/authz decisions and track metrics
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {
        "org.sifunity.gateway",
        "org.sifunity.broker.common"
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = {
            "org\\.sifunity\\.broker\\.common\\.filter\\..*",
            "org\\.sifunity\\.broker\\.common\\.util\\.HeaderSanitizer"
        }
    )
)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
