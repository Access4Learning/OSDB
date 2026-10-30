// SPDX-License-Identifier: MIT
package org.sifunity.observability.docker;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation tests for Docker Compose configuration.
 * 
 * These tests verify that the Docker Compose configuration is valid and contains
 * all required services with correct port mappings.
 * 
 * Requirements: 15.1, 15.2, 15.3, 15.4
 */
class DockerComposeValidationTest {

    private static final String DOCKER_COMPOSE_PATH = "observability/docker/docker-compose.yml";
    private static final String PROMETHEUS_CONFIG_PATH = "observability/docker/prometheus/prometheus.yml";
    private static final String PROMETHEUS_ALERTS_PATH = "observability/docker/prometheus/alerts.yml";
    private static final String LOGSTASH_CONFIG_PATH = "observability/docker/logstash/config/logstash.yml";
    private static final String LOGSTASH_PIPELINE_PATH = "observability/docker/logstash/pipeline/logstash.conf";
    private static final String GRAFANA_DATASOURCE_PATH = "observability/docker/grafana/provisioning/datasources/prometheus.yml";
    private static final String GRAFANA_DASHBOARD_PROVIDER_PATH = "observability/docker/grafana/provisioning/dashboards/dashboards.yml";

    @Test
    void dockerComposeFileExists() {
        Path path = Paths.get(DOCKER_COMPOSE_PATH);
        assertThat(path).exists();
        assertThat(path).isRegularFile();
    }

    @Test
    void dockerComposeIsValidYaml() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(DOCKER_COMPOSE_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            assertThat(config).isNotNull();
            assertThat(config).containsKey("services");
        }
    }

    @Test
    void dockerComposeContainsAllRequiredServices() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(DOCKER_COMPOSE_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            
            // Verify all required services are present
            assertThat(services).containsKeys(
                "jaeger",           // Distributed tracing
                "prometheus",       // Metrics collection
                "grafana",          // Visualization
                "elasticsearch",    // Log storage
                "logstash",         // Log processing
                "kibana"           // Log visualization
            );
        }
    }

    @Test
    void jaegerServiceHasCorrectPorts() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(DOCKER_COMPOSE_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> jaeger = (Map<String, Object>) services.get("jaeger");
            
            assertThat(jaeger).isNotNull();
            
            @SuppressWarnings("unchecked")
            List<String> ports = (List<String>) jaeger.get("ports");
            
            // Verify Jaeger UI port (16686) is exposed
            assertThat(ports).anyMatch(port -> port.contains("16686:16686"));
            
            // Verify gRPC port (14250) is exposed for trace collection
            assertThat(ports).anyMatch(port -> port.contains("14250:14250"));
        }
    }

    @Test
    void prometheusServiceHasCorrectPorts() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(DOCKER_COMPOSE_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> prometheus = (Map<String, Object>) services.get("prometheus");
            
            assertThat(prometheus).isNotNull();
            
            @SuppressWarnings("unchecked")
            List<String> ports = (List<String>) prometheus.get("ports");
            
            // Verify Prometheus UI port (9090) is exposed
            assertThat(ports).anyMatch(port -> port.contains("9090:9090"));
        }
    }

    @Test
    void grafanaServiceHasCorrectPorts() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(DOCKER_COMPOSE_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> grafana = (Map<String, Object>) services.get("grafana");
            
            assertThat(grafana).isNotNull();
            
            @SuppressWarnings("unchecked")
            List<String> ports = (List<String>) grafana.get("ports");
            
            // Verify Grafana UI port (3000) is exposed
            assertThat(ports).anyMatch(port -> port.contains("3000:3000"));
        }
    }

    @Test
    void kibanaServiceHasCorrectPorts() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(DOCKER_COMPOSE_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> kibana = (Map<String, Object>) services.get("kibana");
            
            assertThat(kibana).isNotNull();
            
            @SuppressWarnings("unchecked")
            List<String> ports = (List<String>) kibana.get("ports");
            
            // Verify Kibana UI port (5601) is exposed
            assertThat(ports).anyMatch(port -> port.contains("5601:5601"));
        }
    }

    @Test
    void logstashServiceHasCorrectPorts() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(DOCKER_COMPOSE_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> logstash = (Map<String, Object>) services.get("logstash");
            
            assertThat(logstash).isNotNull();
            
            @SuppressWarnings("unchecked")
            List<String> ports = (List<String>) logstash.get("ports");
            
            // Verify Logstash TCP port (5000) is exposed
            assertThat(ports).anyMatch(port -> port.contains("5000:5000/tcp"));
        }
    }

    @Test
    void dockerComposeDefinesNetworks() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(DOCKER_COMPOSE_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            
            assertThat(config).containsKey("networks");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> networks = (Map<String, Object>) config.get("networks");
            
            assertThat(networks).containsKey("observability");
        }
    }

    @Test
    void dockerComposeDefinesVolumes() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(DOCKER_COMPOSE_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            
            assertThat(config).containsKey("volumes");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> volumes = (Map<String, Object>) config.get("volumes");
            
            // Verify persistent volumes for data storage
            assertThat(volumes).containsKeys(
                "jaeger-data",
                "prometheus-data",
                "grafana-data",
                "elasticsearch-data"
            );
        }
    }

    @Test
    void prometheusConfigFileExists() {
        Path path = Paths.get(PROMETHEUS_CONFIG_PATH);
        assertThat(path).exists();
        assertThat(path).isRegularFile();
    }

    @Test
    void prometheusConfigIsValidYaml() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(PROMETHEUS_CONFIG_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            assertThat(config).isNotNull();
            assertThat(config).containsKey("scrape_configs");
        }
    }

    @Test
    void prometheusConfigContainsAllServiceJobs() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(PROMETHEUS_CONFIG_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> scrapeConfigs = (List<Map<String, Object>>) config.get("scrape_configs");
            
            assertThat(scrapeConfigs).isNotNull();
            
            // Extract job names
            List<String> jobNames = scrapeConfigs.stream()
                .map(job -> (String) job.get("job_name"))
                .toList();
            
            // Verify all 9 SIF Unity Broker services are configured
            assertThat(jobNames).contains(
                "gateway",
                "environment-service",
                "queue-service",
                "subscription-service",
                "job-service",
                "requests-connector",
                "responses-connector",
                "events-connector",
                "services-connector"
            );
        }
    }

    @Test
    void prometheusAlertsFileExists() {
        Path path = Paths.get(PROMETHEUS_ALERTS_PATH);
        assertThat(path).exists();
        assertThat(path).isRegularFile();
    }

    @Test
    void prometheusAlertsIsValidYaml() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(PROMETHEUS_ALERTS_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            assertThat(config).isNotNull();
            assertThat(config).containsKey("groups");
        }
    }

    @Test
    void logstashConfigFileExists() {
        Path path = Paths.get(LOGSTASH_CONFIG_PATH);
        assertThat(path).exists();
        assertThat(path).isRegularFile();
    }

    @Test
    void logstashPipelineFileExists() {
        Path path = Paths.get(LOGSTASH_PIPELINE_PATH);
        assertThat(path).exists();
        assertThat(path).isRegularFile();
    }

    @Test
    void logstashPipelineContainsInputOutputSections() throws IOException {
        String content = Files.readString(Paths.get(LOGSTASH_PIPELINE_PATH));
        
        // Verify pipeline has input, filter, and output sections
        assertThat(content).contains("input {");
        assertThat(content).contains("filter {");
        assertThat(content).contains("output {");
        
        // Verify TCP input on port 5000
        assertThat(content).contains("tcp {");
        assertThat(content).contains("port => 5000");
        
        // Verify Elasticsearch output
        assertThat(content).contains("elasticsearch {");
    }

    @Test
    void grafanaDatasourceConfigFileExists() {
        Path path = Paths.get(GRAFANA_DATASOURCE_PATH);
        assertThat(path).exists();
        assertThat(path).isRegularFile();
    }

    @Test
    void grafanaDatasourceConfigIsValidYaml() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(GRAFANA_DATASOURCE_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            assertThat(config).isNotNull();
            assertThat(config).containsKey("datasources");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> datasources = (List<Map<String, Object>>) config.get("datasources");
            
            assertThat(datasources).isNotEmpty();
            
            // Verify Prometheus datasource is configured
            Map<String, Object> promDs = datasources.get(0);
            assertThat(promDs.get("name")).isEqualTo("Prometheus");
            assertThat(promDs.get("type")).isEqualTo("prometheus");
            assertThat(promDs.get("url")).isEqualTo("http://prometheus:9090");
        }
    }

    @Test
    void grafanaDashboardProviderConfigFileExists() {
        Path path = Paths.get(GRAFANA_DASHBOARD_PROVIDER_PATH);
        assertThat(path).exists();
        assertThat(path).isRegularFile();
    }

    @Test
    void grafanaDashboardProviderConfigIsValidYaml() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(GRAFANA_DASHBOARD_PROVIDER_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            assertThat(config).isNotNull();
            assertThat(config).containsKey("providers");
        }
    }

    @Test
    void servicesHaveHealthChecks() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(DOCKER_COMPOSE_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            
            // Verify critical services have health checks
            @SuppressWarnings("unchecked")
            Map<String, Object> elasticsearch = (Map<String, Object>) services.get("elasticsearch");
            assertThat(elasticsearch).containsKey("healthcheck");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> logstash = (Map<String, Object>) services.get("logstash");
            assertThat(logstash).containsKey("healthcheck");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> kibana = (Map<String, Object>) services.get("kibana");
            assertThat(kibana).containsKey("healthcheck");
        }
    }

    @Test
    void servicesHaveRestartPolicies() throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(DOCKER_COMPOSE_PATH)) {
            Map<String, Object> config = yaml.load(fis);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) config.get("services");
            
            // Verify all services have restart policies
            for (String serviceName : List.of("jaeger", "prometheus", "grafana", 
                                              "elasticsearch", "logstash", "kibana")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> service = (Map<String, Object>) services.get(serviceName);
                assertThat(service).containsKey("restart");
            }
        }
    }
}
