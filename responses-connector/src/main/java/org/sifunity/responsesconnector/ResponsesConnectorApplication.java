// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main application class for the Responses Connector service.
 * 
 * <p>The Responses Connector is a connector service within the SIF Unity Broker system
 * responsible for receiving Provider responses for DELAYED requests and routing them
 * to Consumer queues.</p>
 * 
 * <p><strong>Key Responsibilities:</strong></p>
 * <ul>
 *   <li>Accept responses from Providers via REST API</li>
 *   <li>Accept responses from Requests Connector (DELAYED_to_IMMEDIATE pattern)</li>
 *   <li>Route responses to consumer queues via Queue Service</li>
 *   <li>Handle response correlation with original requests</li>
 *   <li>Process error responses and route them to consumers</li>
 * </ul>
 * 
 * <p><strong>Service Characteristics:</strong></p>
 * <ul>
 *   <li>Connector Service (no direct database access)</li>
 *   <li>Calls Queue Service via REST APIs</li>
 *   <li>Stateless service for horizontal scaling</li>
 *   <li>Runs on port 8084</li>
 * </ul>
 * 
 * @see org.sifunity.responsesconnector.controller.ResponseController
 * @see org.sifunity.responsesconnector.service.ResponseRoutingService
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "org.sifunity.responsesconnector",
    "org.sifunity.broker.common"
})
public class ResponsesConnectorApplication {

    /**
     * Main entry point for the Responses Connector application.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ResponsesConnectorApplication.class, args);
    }
}
