// SPDX-License-Identifier: MIT
package org.sifunity.testprovider;

import org.sifunity.testprovider.config.ProviderConfig;
import org.sifunity.testprovider.util.SecretMasker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@Slf4j
public class TestProviderApplication {

    private final ProviderConfig config;

    public TestProviderApplication(ProviderConfig config) {
        this.config = config;
    }

    public static void main(String[] args) {
        SpringApplication.run(TestProviderApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("╔══════════════════════════════════════════════════════╗");
        log.info("║           SIF Test Provider — Ready                 ║");
        log.info("╠══════════════════════════════════════════════════════╣");
        log.info("║  Gateway URL  : {}",  padRight(config.getGatewayUrl()));
        log.info("║  Provider Port: {}",  padRight(String.valueOf(config.getPort())));
        log.info("║  Domain ID    : {}",  padRight(config.getDomainId()));
        log.info("║  Solution ID  : {}",  padRight(config.getSolutionId()));
        log.info("║  Queue ID     : {}",  padRight(config.getQueueId() != null ? config.getQueueId() : "(none — delayed polling disabled)"));
        log.info("║  Session Token: {}",  padRight(SecretMasker.mask(config.getSessionToken())));
        log.info("╚══════════════════════════════════════════════════════╝");
    }

    private String padRight(String value) {
        return value != null ? value : "(not set)";
    }
}
