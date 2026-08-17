// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment;

import org.sifunity.broker.environment.config.BrokerInfrastructureConfig;
import org.sifunity.broker.environment.config.ProvisionRequestConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main application class for the Environment Service.
 * 
 * The Environment Service is a foundational Core Service in the SIF Unity Broker
 * that manages application registration, access control, provider service discovery,
 * and authentication/authorization validation.
 */
@SpringBootApplication
@EnableConfigurationProperties({BrokerInfrastructureConfig.class, ProvisionRequestConfig.class})
@ComponentScan(basePackages = {
    "org.sifunity.broker.environment",
    "org.sifunity.broker.common"
})
public class EnvironmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnvironmentServiceApplication.class, args);
    }

}