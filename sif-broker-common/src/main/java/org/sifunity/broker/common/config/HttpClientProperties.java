// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Shared configuration for outbound HTTP (WebClient) behavior across all
 * SIF Unity Broker services.
 *
 * <p>Binds configuration from application.yml under the prefix
 * {@code sif.http.client}.</p>
 *
 * <p>Configuration structure:</p>
 * <pre>
 * sif:
 *   http:
 *     client:
 *       max-response-size: 5MB
 * </pre>
 *
 * @see HttpClientCodecAutoConfiguration
 */
@ConfigurationProperties(prefix = "sif.http.client")
public class HttpClientProperties {

    /**
     * Maximum size of a provider/service response body that a WebClient will
     * buffer in memory while decoding.
     *
     * <p>Spring's default is 256KB, which is too small for broker use cases that
     * fetch large result sets (e.g. bulk provider queries) or large entities
     * (e.g. an environment with many zones and a large ACL). Responses larger
     * than this fail cleanly with a {@code PROVIDER_RESPONSE_TOO_LARGE} 502
     * rather than a confusing decode error.</p>
     *
     * <p>This is a per-request in-memory buffer, so the effective memory ceiling
     * is roughly {@code maxResponseSize * concurrent in-flight requests}. Raise
     * service task memory before increasing this substantially.</p>
     */
    private DataSize maxResponseSize = DataSize.ofMegabytes(5);

    public DataSize getMaxResponseSize() {
        return maxResponseSize;
    }

    public void setMaxResponseSize(DataSize maxResponseSize) {
        this.maxResponseSize = maxResponseSize;
    }
}
