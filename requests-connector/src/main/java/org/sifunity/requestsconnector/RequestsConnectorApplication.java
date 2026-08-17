// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Requests Connector service.
 * 
 * The Requests Connector is a connector service within the SIF Unity Broker system
 * responsible for routing consumer requests to providers. It implements both IMMEDIATE
 * (synchronous) and DELAYED (asynchronous) message exchange patterns, with intelligent
 * routing based on provider capabilities.
 * 
 * Key Features:
 * - IMMEDIATE routing: Forward synchronous requests to providers
 * - DELAYED to DELAYED: Route async requests to provider's request queue
 * - DELAYED to IMMEDIATE: Act as proxy for IMMEDIATE-only providers
 * - Provider registry caching for performance
 * - Background worker for proxy pattern processing
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
@ComponentScan(basePackages = {
    "org.sifunity.requestsconnector",
    "org.sifunity.broker.common"
})
public class RequestsConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(RequestsConnectorApplication.class, args);
    }
}
