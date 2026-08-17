// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main application class for the Events Connector service.
 * 
 * The Events Connector is a stateless connector service responsible for receiving event 
 * publications from Providers, querying the Subscription Service for matching subscriptions, 
 * and distributing events to all subscriber queues via the Queue Service.
 * 
 * Key responsibilities:
 * - Accept event publications from Providers via REST API
 * - Query Subscription Service for subscriptions matching event's service name, zone, and context
 * - Distribute events to all matching subscriber queues via Queue Service
 * - Handle fan-out processing efficiently using reactive programming
 * - Handle partial delivery scenarios (some queues succeed, others fail)
 * - Provide clear error responses and comprehensive logging
 * 
 * Architecture principles:
 * - Stateless: No local state, enabling horizontal scaling
 * - No Database Access: Calls Core Services (Subscription Service, Queue Service) via REST APIs
 * - No Authentication Logic: Assumes requests are pre-authenticated by Gateway
 * - Concurrent Processing: Uses reactive programming for efficient fan-out
 * - Resilient: Implements retries, timeouts, and circuit breakers for service calls
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "org.sifunity.eventsconnector",
    "org.sifunity.broker.common"
})
public class EventsConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventsConnectorApplication.class, args);
    }
}
