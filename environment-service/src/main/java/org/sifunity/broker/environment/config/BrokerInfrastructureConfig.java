// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for broker infrastructure service base URLs.
 *
 * <p>Binds configuration from application.yml under the prefix "sif.broker.infrastructure"
 * to provide type-safe access to the base URLs of all eight SIF broker infrastructure services.</p>
 *
 * <p>Configuration structure:</p>
 * <pre>
 * sif:
 *   broker:
 *     infrastructure:
 *       environment: http://localhost:8081
 *       provision-requests: http://localhost:8081
 *       requests-connector: http://localhost:8083
 *       events-connector: http://localhost:8084
 *       queues: http://localhost:8085
 *       subscriptions: http://localhost:8086
 *       services-connector: http://localhost:8087
 *       responses-connector: http://localhost:8088
 * </pre>
 *
 * @see org.sifunity.broker.environment.service.EnvironmentResponseMapper
 */
@ConfigurationProperties(prefix = "sif.broker.infrastructure")
@Validated
public record BrokerInfrastructureConfig(
        @NotBlank String gatewayBaseUrl,
        @NotBlank String environment,
        @NotBlank String provisionRequests,
        @NotBlank String requestsConnector,
        @NotBlank String eventsConnector,
        @NotBlank String queues,
        @NotBlank String subscriptions,
        @NotBlank String servicesConnector,
        @NotBlank String responsesConnector
) {}
