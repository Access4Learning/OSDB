// SPDX-License-Identifier: MIT
/**
 * Rate limiting components for the API Gateway.
 * 
 * <p>This package contains the Redis-based rate limiter implementation that enforces
 * request rate limits per environment using a sliding window algorithm.</p>
 * 
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.sifunity.gateway.ratelimit.RedisRateLimiter} - Main rate limiter using Redis sorted sets</li>
 *   <li>{@link org.sifunity.gateway.ratelimit.RateLimitResponse} - Response object indicating allow/deny status</li>
 * </ul>
 * 
 * <p>The rate limiter uses Redis sorted sets to track requests within a sliding time window,
 * ensuring accurate rate limiting without memory leaks. Old entries are automatically cleaned up.</p>
 * 
 * @see org.sifunity.gateway.config.RateLimitConfiguration
 */
package org.sifunity.gateway.ratelimit;
