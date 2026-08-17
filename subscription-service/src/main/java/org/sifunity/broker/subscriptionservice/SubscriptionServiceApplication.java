// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main application class for the Subscription Service.
 * 
 * The Subscription Service is a core service within the SIF Unity Broker system
 * responsible for managing subscriptions that define message routing rules.
 * It handles EVENT subscriptions (consumers subscribing to event notifications)
 * and DELAYED_REQUEST subscriptions (providers subscribing to their request queues).
 */
@SpringBootApplication
@EnableCaching
@ComponentScan(basePackages = {
    "org.sifunity.broker.subscriptionservice",
    "org.sifunity.broker.common"
})
public class SubscriptionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionServiceApplication.class, args);
    }
}
