// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main application class for the Services Connector service.
 * 
 * The Services Connector is a stateless connector service that routes job operations
 * between consumers, providers, and the Job Service. It handles job creation, state updates,
 * phase management, and job queries by coordinating between consumers, providers, and the Job Service.
 * 
 * Key responsibilities:
 * - Route job creation requests from consumers to Job Service and providers
 * - Route job state updates from providers to Job Service
 * - Route job phase operations to Job Service
 * - Query provider registry for routing decisions
 * - Cache provider registry lookups for performance
 * - Handle errors gracefully and provide clear error responses
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "org.sifunity.servicesconnector",
    "org.sifunity.broker.common"
})
public class ServicesConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicesConnectorApplication.class, args);
    }
}
