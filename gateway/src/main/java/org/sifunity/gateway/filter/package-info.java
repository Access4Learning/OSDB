// SPDX-License-Identifier: MIT
/**
 * Gateway filters for request processing.
 * 
 * This package contains the filter implementations that process requests in the Gateway:
 * - CorrelationIdFilter: Generates or preserves correlation IDs for request tracing
 * - AuthenticationFilter: Validates sessionToken and HMAC signatures via Environment Service
 * - AuthorizationFilter: Checks ACL permissions via Environment Service
 * - EnvironmentContextFilter: Injects environment context headers for backend services
 * - RateLimitFilter: Enforces request rate limits per environment
 * 
 * Filters are executed in order based on their @Order annotation.
 */
package org.sifunity.gateway.filter;
