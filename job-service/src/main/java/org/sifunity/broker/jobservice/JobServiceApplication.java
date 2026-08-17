// SPDX-License-Identifier: MIT
package org.sifunity.broker.jobservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main application class for the Job Service.
 * 
 * The Job Service is a core microservice within the SIF Unity Broker system
 * that manages Job state objects for Functional Services. It provides comprehensive
 * job lifecycle management including creation, state transitions, phase tracking,
 * and querying capabilities.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "org.sifunity.broker.jobservice",
    "org.sifunity.broker.common"
})
public class JobServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobServiceApplication.class, args);
    }
}
