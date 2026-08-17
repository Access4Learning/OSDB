// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.exception;

/**
 * Exception thrown when no provider is registered for a given service, zone, and context.
 * 
 * <p>This exception is thrown during job creation when the provider registry lookup
 * returns no matching provider. It should be mapped to HTTP 404 Not Found by the
 * exception handler.</p>
 * 
 * <p><strong>Requirements:</strong></p>
 * <ul>
 *   <li>1.5: Return 404 when no provider registered</li>
 *   <li>7.3: Return 404 with descriptive message when provider registry returns no provider</li>
 * </ul>
 */
public class NoProviderException extends RuntimeException {
    
    private final String serviceName;
    private final String zoneId;
    private final String contextId;
    
    /**
     * Constructs a NoProviderException with service, zone, and context information.
     * 
     * @param serviceName the service name
     * @param zoneId the zone ID
     * @param contextId the context ID (may be null)
     */
    public NoProviderException(String serviceName, String zoneId, String contextId) {
        super(String.format("No provider registered for service '%s' in zone '%s' context '%s'",
            serviceName, zoneId, contextId != null ? contextId : "default"));
        this.serviceName = serviceName;
        this.zoneId = zoneId;
        this.contextId = contextId;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public String getZoneId() {
        return zoneId;
    }
    
    public String getContextId() {
        return contextId;
    }
}
