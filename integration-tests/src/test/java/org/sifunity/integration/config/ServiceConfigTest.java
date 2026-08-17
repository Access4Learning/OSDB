// SPDX-License-Identifier: MIT
package org.sifunity.integration.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ServiceConfig} property propagation.
 * Validates Requirements 3.3 (preservation of toSpringProperties structure).
 */
class ServiceConfigTest {

    /**
     * 4.1 - Verifies toSpringProperties() contains all expected keys when all fields are populated.
     */
    @Test
    void toSpringProperties_allFieldsPopulated_containsAllExpectedKeys() {
        ServiceConfig config = ServiceConfig.builder()
                .serviceName("environment-service")
                .port(18081)
                .postgresUrl("jdbc:postgresql://localhost:5432/testdb")
                .postgresUsername("testuser")
                .postgresPassword("testpass")
                .redisHost("localhost")
                .redisPort(6379)
                .s3Endpoint("http://localhost:4566")
                .s3Bucket("test-bucket")
                .s3Region("us-east-1")
                .s3AccessKey("accesskey")
                .s3SecretKey("secretkey")
                .build();

        Map<String, Object> props = config.toSpringProperties();

        assertThat(props).containsKeys(
                "server.port",
                "spring.application.name",
                "spring.datasource.url",
                "spring.datasource.username",
                "spring.datasource.password",
                "spring.data.redis.host",
                "spring.data.redis.port",
                "sif.broker.storage.s3.endpoint",
                "message.storage.s3.bucket",
                "message.storage.s3.region",
                "sif.broker.storage.s3.access-key",
                "sif.broker.storage.s3.secret-key",
                "spring.jpa.properties.hibernate.dialect",
                "spring.datasource.driver-class-name"
        );
    }

    /**
     * 4.2 - Verifies toSpringProperties() preserves non-localhost host values exactly.
     */
    @Test
    void toSpringProperties_nonLocalhostHosts_preservedExactly() {
        ServiceConfig config = ServiceConfig.builder()
                .serviceName("queue-service")
                .port(18082)
                .postgresUrl("jdbc:postgresql://172.17.0.1:32789/testdb")
                .postgresUsername("user")
                .postgresPassword("pass")
                .redisHost("192.168.99.100")
                .redisPort(32790)
                .s3Endpoint("http://172.17.0.1:4566")
                .s3Bucket("bucket")
                .build();

        Map<String, Object> props = config.toSpringProperties();

        assertThat(props)
                .containsEntry("spring.datasource.url", "jdbc:postgresql://172.17.0.1:32789/testdb")
                .containsEntry("spring.data.redis.host", "192.168.99.100")
                .containsEntry("spring.data.redis.port", "32790")
                .containsEntry("sif.broker.storage.s3.endpoint", "http://172.17.0.1:4566");
    }

    /**
     * 4.3 - Verifies createDefault("gateway", ...) assigns port 18080 (not 8080).
     */
    @Test
    void createDefault_gateway_assignsPort18080() {
        ServiceConfig config = ServiceConfig.createDefault(
                "gateway",
                "jdbc:postgresql://localhost:5432/testdb",
                "user",
                "pass",
                "localhost",
                6379,
                "http://localhost:4566",
                "bucket"
        );

        Map<String, Object> props = config.toSpringProperties();

        assertThat(config.getPort()).isEqualTo(18080);
        assertThat(props).containsEntry("server.port", "18080");
    }

    /**
     * 4.4 - Verifies additional properties are included in toSpringProperties() output.
     */
    @Test
    void toSpringProperties_additionalProperties_includedInOutput() {
        ServiceConfig config = ServiceConfig.builder()
                .serviceName("environment-service")
                .port(18081)
                .postgresUrl("jdbc:postgresql://localhost:5432/testdb")
                .postgresUsername("user")
                .postgresPassword("pass")
                .redisHost("localhost")
                .redisPort(6379)
                .s3Endpoint("http://localhost:4566")
                .s3Bucket("bucket")
                .build();

        config.addProperty("custom.property.one", "value1");
        config.addProperty("custom.property.two", "value2");

        Map<String, Object> props = config.toSpringProperties();

        assertThat(props)
                .containsEntry("custom.property.one", "value1")
                .containsEntry("custom.property.two", "value2");
    }
}
