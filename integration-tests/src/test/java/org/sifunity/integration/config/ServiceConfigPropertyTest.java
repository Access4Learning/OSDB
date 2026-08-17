// SPDX-License-Identifier: MIT
package org.sifunity.integration.config;

import net.jqwik.api.*;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for {@link ServiceConfig#toSpringProperties()}.
 *
 * <p>Validates preservation of property map structure (Property 2) and
 * faithful reproduction of connection detail values (Property 1).
 */
class ServiceConfigPropertyTest {

    private static final Set<String> EXPECTED_KEYS = Set.of(
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

    // ── Arbitraries ──────────────────────────────────────────────────

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }

    @Provide
    Arbitrary<Integer> validPorts() {
        return Arbitraries.integers().between(1024, 65535);
    }

    @Provide
    Arbitrary<String> hosts() {
        // Mix of IP addresses and hostnames
        Arbitrary<String> ipAddresses = Arbitraries.integers().between(1, 254)
                .tuple4()
                .map(t -> t.get1() + "." + t.get2() + "." + t.get3() + "." + t.get4());
        Arbitrary<String> hostnames = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                .map(s -> s.toLowerCase() + ".example.com");
        return Arbitraries.oneOf(ipAddresses, hostnames);
    }

    @Provide
    Arbitrary<String> jdbcUrls() {
        return Combinators.combine(hosts(), validPorts(), Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(12))
                .as((host, port, db) -> "jdbc:postgresql://" + host + ":" + port + "/" + db);
    }

    @Provide
    Arbitrary<String> s3Endpoints() {
        return Combinators.combine(hosts(), validPorts())
                .as((host, port) -> "http://" + host + ":" + port);
    }

    @Provide
    Arbitrary<String> alphaStrings() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> bucketNames() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20)
                .map(String::toLowerCase);
    }

    @Provide
    Arbitrary<String> regions() {
        return Arbitraries.of("us-east-1", "us-west-2", "eu-west-1", "ap-southeast-1");
    }


    // ── Property 2: Property Map Key Preservation ────────────────────

    /**
     * Property 2: For any valid ServiceConfig with non-null postgres, redis, and S3 fields,
     * toSpringProperties() SHALL produce a map containing all expected Spring Boot property keys.
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Property(tries = 100)
    @Label("Property 2: ServiceConfig toSpringProperties contains all expected keys")
    void toSpringProperties_containsAllExpectedKeys(
            @ForAll("serviceNames") String serviceName,
            @ForAll("validPorts") int port,
            @ForAll("jdbcUrls") String postgresUrl,
            @ForAll("alphaStrings") String postgresUsername,
            @ForAll("alphaStrings") String postgresPassword,
            @ForAll("hosts") String redisHost,
            @ForAll("validPorts") int redisPort,
            @ForAll("s3Endpoints") String s3Endpoint,
            @ForAll("bucketNames") String s3Bucket,
            @ForAll("regions") String s3Region,
            @ForAll("alphaStrings") String s3AccessKey,
            @ForAll("alphaStrings") String s3SecretKey) {

        ServiceConfig config = ServiceConfig.builder()
                .serviceName(serviceName)
                .port(port)
                .postgresUrl(postgresUrl)
                .postgresUsername(postgresUsername)
                .postgresPassword(postgresPassword)
                .redisHost(redisHost)
                .redisPort(redisPort)
                .s3Endpoint(s3Endpoint)
                .s3Bucket(s3Bucket)
                .s3Region(s3Region)
                .s3AccessKey(s3AccessKey)
                .s3SecretKey(s3SecretKey)
                .build();

        Map<String, Object> props = config.toSpringProperties();

        assertThat(props.keySet()).containsAll(EXPECTED_KEYS);
    }

    // ── Property 1: Connection Detail Value Faithfulness ─────────────

    /**
     * Property 1: For any ServiceConfig created with random host/port combinations,
     * toSpringProperties() SHALL faithfully reproduce all connection detail values —
     * the JDBC URL, Redis host, Redis port, and S3 endpoint must exactly match input values.
     *
     * <p><b>Validates: Requirements 2.1, 2.5</b></p>
     */
    @Property(tries = 100)
    @Label("Property 1: ServiceConfig toSpringProperties faithfully reproduces connection details")
    void toSpringProperties_faithfullyReproducesConnectionDetails(
            @ForAll("serviceNames") String serviceName,
            @ForAll("validPorts") int port,
            @ForAll("jdbcUrls") String postgresUrl,
            @ForAll("alphaStrings") String postgresUsername,
            @ForAll("alphaStrings") String postgresPassword,
            @ForAll("hosts") String redisHost,
            @ForAll("validPorts") int redisPort,
            @ForAll("s3Endpoints") String s3Endpoint,
            @ForAll("bucketNames") String s3Bucket,
            @ForAll("regions") String s3Region,
            @ForAll("alphaStrings") String s3AccessKey,
            @ForAll("alphaStrings") String s3SecretKey) {

        ServiceConfig config = ServiceConfig.builder()
                .serviceName(serviceName)
                .port(port)
                .postgresUrl(postgresUrl)
                .postgresUsername(postgresUsername)
                .postgresPassword(postgresPassword)
                .redisHost(redisHost)
                .redisPort(redisPort)
                .s3Endpoint(s3Endpoint)
                .s3Bucket(s3Bucket)
                .s3Region(s3Region)
                .s3AccessKey(s3AccessKey)
                .s3SecretKey(s3SecretKey)
                .build();

        Map<String, Object> props = config.toSpringProperties();

        // JDBC URL must exactly match input
        assertThat(props.get("spring.datasource.url")).isEqualTo(postgresUrl);

        // Redis host must exactly match input
        assertThat(props.get("spring.data.redis.host")).isEqualTo(redisHost);

        // Redis port must exactly match input (as String)
        assertThat(props.get("spring.data.redis.port")).isEqualTo(String.valueOf(redisPort));

        // S3 endpoint must exactly match input
        assertThat(props.get("sif.broker.storage.s3.endpoint")).isEqualTo(s3Endpoint);

        // Also verify other connection details are faithfully reproduced
        assertThat(props.get("spring.datasource.username")).isEqualTo(postgresUsername);
        assertThat(props.get("spring.datasource.password")).isEqualTo(postgresPassword);
        assertThat(props.get("server.port")).isEqualTo(String.valueOf(port));
        assertThat(props.get("spring.application.name")).isEqualTo(serviceName);
        assertThat(props.get("message.storage.s3.bucket")).isEqualTo(s3Bucket);
        assertThat(props.get("message.storage.s3.region")).isEqualTo(s3Region);
        assertThat(props.get("sif.broker.storage.s3.access-key")).isEqualTo(s3AccessKey);
        assertThat(props.get("sif.broker.storage.s3.secret-key")).isEqualTo(s3SecretKey);
    }
}
