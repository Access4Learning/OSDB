// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.util;

import org.sifunity.requestsconnector.exception.MissingHeaderException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Utility class for extracting environment context from HTTP headers.
 * 
 * <p>This class extracts environment information that was injected by the
 * Gateway during authentication. The environment context is required for
 * routing requests to the correct providers.</p>
 * 
 * <p>Requirements: 8.1, 8.2, 8.3</p>
 */
@Component
public class HeaderExtractor {
    
    /**
     * Extracts the environment ID from the X-Environment-Id header.
     * 
     * <p>The environment ID uniquely identifies the environment context
     * for the request. This header is injected by the Gateway after
     * authentication.</p>
     * 
     * @return the environment ID as a UUID
     * @throws MissingHeaderException if the X-Environment-Id header is missing
     * @throws IllegalArgumentException if the header value is not a valid UUID
     */
    public UUID extractEnvironmentId() {
        HttpServletRequest request = getCurrentRequest();
        String envId = request.getHeader("X-Environment-Id");
        if (envId == null || envId.trim().isEmpty()) {
            throw new MissingHeaderException("X-Environment-Id header is required");
        }
        try {
            return UUID.fromString(envId);
        } catch (IllegalArgumentException e) {
            throw new MissingHeaderException("X-Environment-Id header must be a valid UUID");
        }
    }
    
    /**
     * Extracts the environment name from the X-Environment-Name header.
     * 
     * <p>The environment name provides a human-readable identifier for
     * the environment context. This header is injected by the Gateway
     * after authentication.</p>
     * 
     * @return the environment name as a String
     * @throws MissingHeaderException if the X-Environment-Name header is missing
     */
    public String extractEnvironmentName() {
        HttpServletRequest request = getCurrentRequest();
        String envName = request.getHeader("X-Environment-Name");
        if (envName == null || envName.trim().isEmpty()) {
            throw new MissingHeaderException("X-Environment-Name header is required");
        }
        return envName;
    }
    
    /**
     * Gets the current HTTP servlet request from the request context.
     * 
     * @return the current HTTP servlet request
     * @throws IllegalStateException if called outside of a request context
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = 
            (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getRequest();
    }
}
