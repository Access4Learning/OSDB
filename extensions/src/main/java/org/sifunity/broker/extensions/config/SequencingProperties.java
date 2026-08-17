package org.sifunity.broker.extensions.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Externalized configuration for the message sequencing extension.
 */
@Data
@ConfigurationProperties(prefix = "sif.extensions.sequencing")
public class SequencingProperties {

    /** Global kill switch. Default: true (enabled). */
    private boolean enabled = true;

    /** Base URL for the entity-tags API (environment-service, bypasses gateway auth). */
    private String entityTagsBaseUrl = "http://localhost:8081";

    /** Cache TTL for feature toggle lookups. Default: 30 seconds. */
    private Duration toggleCacheTtl = Duration.ofSeconds(30);
}
