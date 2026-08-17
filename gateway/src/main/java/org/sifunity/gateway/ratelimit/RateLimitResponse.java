// SPDX-License-Identifier: MIT
package org.sifunity.gateway.ratelimit;

/**
 * Response from rate limit check indicating whether a request is allowed.
 * 
 * <p>This immutable value object contains:</p>
 * <ul>
 *   <li>isAllowed: boolean indicating if the request should be processed</li>
 *   <li>retryAfterSeconds: time in seconds to wait before retrying (0 if allowed)</li>
 * </ul>
 * 
 * <p>Factory methods are provided for creating allowed and denied responses:</p>
 * <pre>
 * RateLimitResponse.allowed()           // Request is allowed
 * RateLimitResponse.denied(retryAfter)  // Request is denied, retry after N seconds
 * </pre>
 */
public class RateLimitResponse {
    
    /**
     * Indicates whether the request is allowed to proceed.
     * 
     * <p>true if the request is within the rate limit, false if exceeded.</p>
     */
    private final boolean allowed;
    
    /**
     * Number of seconds to wait before retrying the request.
     * 
     * <p>This value is 0 when the request is allowed, and a positive integer
     * when the request is denied due to rate limiting.</p>
     */
    private final long retryAfterSeconds;
    
    /**
     * Constructs a RateLimitResponse with the specified values.
     * 
     * @param allowed whether the request is allowed
     * @param retryAfterSeconds seconds to wait before retrying (0 if allowed)
     */
    public RateLimitResponse(boolean allowed, long retryAfterSeconds) {
        this.allowed = allowed;
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    /**
     * Gets whether the request is allowed.
     * 
     * @return true if allowed, false if denied
     */
    public boolean isAllowed() {
        return allowed;
    }
    
    /**
     * Gets the retry-after time in seconds.
     * 
     * @return seconds to wait before retrying
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
    
    /**
     * Creates a response indicating the request is allowed.
     * 
     * @return a RateLimitResponse with isAllowed=true and retryAfterSeconds=0
     */
    public static RateLimitResponse allowed() {
        return new RateLimitResponse(true, 0);
    }
    
    /**
     * Creates a response indicating the request is denied.
     * 
     * @param retryAfterSeconds the number of seconds to wait before retrying
     * @return a RateLimitResponse with isAllowed=false and the specified retry time
     */
    public static RateLimitResponse denied(long retryAfterSeconds) {
        return new RateLimitResponse(false, retryAfterSeconds);
    }
}
