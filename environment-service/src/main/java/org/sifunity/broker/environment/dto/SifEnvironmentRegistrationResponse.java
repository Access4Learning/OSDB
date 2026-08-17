// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * DTO for the environment registration response, combining the full SIF 3.7
 * environment object with the sharedSecret (only returned during registration).
 *
 * <p>Wraps the response under an "environment" key to match SIF 3.7 singular
 * resource response format: {@code {"environment": {"id":..., "sharedSecret":...}}}</p>
 *
 * @param environment the nested environment content (environment fields + sharedSecret)
 */
public record SifEnvironmentRegistrationResponse(
    SifEnvironmentRegistrationContent environment
) {

    /**
     * The inner content of the registration response, combining the SIF environment
     * fields with the sharedSecret. The environment fields are unwrapped so they
     * appear at the same level as sharedSecret inside the "environment" wrapper.
     */
    public record SifEnvironmentRegistrationContent(
        @JsonUnwrapped
        SifEnvironmentResponse env,
        String sharedSecret
    ) {
        // Convenience delegators so callers can use .environment().id() etc.
        public java.util.UUID id() { return env.id(); }
        public org.sifunity.broker.common.constant.EnvironmentType type() { return env.type(); }
        public String fingerprint() { return env.fingerprint(); }
        public String sessionToken() { return env.sessionToken(); }
        public String solutionId() { return env.solutionId(); }
        public DefaultZoneDTO defaultZone() { return env.defaultZone(); }
        public String authenticationMethod() { return env.authenticationMethod(); }
        public String instanceId() { return env.instanceId(); }
        public String userToken() { return env.userToken(); }
        public String consumerName() { return env.consumerName(); }
        public ApplicationInfoDTO applicationInfo() { return env.applicationInfo(); }
        public InfrastructureServicesDTO infrastructureServices() { return env.infrastructureServices(); }
        public ProvisionedZonesDTO provisionedZones() { return env.provisionedZones(); }
    }

    /**
     * Convenience accessor for the sharedSecret without going through .environment().
     */
    public String sharedSecret() {
        return environment.sharedSecret();
    }

    /**
     * Factory method for clean construction.
     */
    public static SifEnvironmentRegistrationResponse of(SifEnvironmentResponse environment, String sharedSecret) {
        return new SifEnvironmentRegistrationResponse(
            new SifEnvironmentRegistrationContent(environment, sharedSecret));
    }
}
