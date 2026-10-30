# Docker Compose Configuration Validation

This document provides manual validation steps for the Docker Compose configuration.

## Automated Validation (Optional)

The validation tests in `tests/` directory can be run from the project root:

```bash
mvn -f observability/docker/tests/pom.xml test
```

**Note**: Tests expect to be run from the project root directory.

## Manual Validation Checklist

### 1. File Existence

Verify all required files exist:

- [ ] `docker-compose.yml` - Main Docker Compose configuration
- [ ] `prometheus/prometheus.yml` - Prometheus scrape configuration
- [ ] `prometheus/alerts.yml` - Prometheus alerting rules
- [ ] `logstash/config/logstash.yml` - Logstash configuration
- [ ] `logstash/pipeline/logstash.conf` - Logstash pipeline configuration
- [ ] `grafana/provisioning/datasources/prometheus.yml` - Grafana datasource
- [ ] `grafana/provisioning/dashboards/dashboards.yml` - Grafana dashboard provider
- [ ] `grafana/dashboards/service-health.json` - Service health dashboard
- [ ] `README.md` - Usage documentation

### 2. YAML Syntax Validation

Validate YAML files are syntactically correct:

```bash
# Validate docker-compose.yml
docker-compose -f observability/docker/docker-compose.yml config

# Validate Prometheus config
promtool check config observability/docker/prometheus/prometheus.yml

# Validate Prometheus alerts
promtool check rules observability/docker/prometheus/alerts.yml
```

### 3. Docker Compose Services

Verify all required services are defined in `docker-compose.yml`:

- [ ] jaeger - Distributed tracing
- [ ] prometheus - Metrics collection
- [ ] grafana - Visualization
- [ ] elasticsearch - Log storage
- [ ] logstash - Log processing
- [ ] kibana - Log visualization

### 4. Port Mappings

Verify correct port mappings:

- [ ] Jaeger UI: 16686:16686
- [ ] Jaeger gRPC: 14250:14250
- [ ] Prometheus: 9090:9090
- [ ] Grafana: 3000:3000
- [ ] Kibana: 5601:5601
- [ ] Logstash TCP: 5000:5000
- [ ] Elasticsearch: 9200:9200

### 5. Prometheus Scrape Configuration

Verify all 9 SIF Unity Broker services are configured in `prometheus/prometheus.yml`:

- [ ] gateway (port 8080)
- [ ] environment-service (port 8081)
- [ ] queue-service (port 8082)
- [ ] subscription-service (port 8083)
- [ ] job-service (port 8084)
- [ ] requests-connector (port 8085)
- [ ] responses-connector (port 8086)
- [ ] events-connector (port 8087)
- [ ] services-connector (port 8088)

### 6. Prometheus Alerts

Verify alerting rules are defined in `prometheus/alerts.yml`:

- [ ] ServiceDown - Service down for >1 minute
- [ ] HighErrorRate - Error rate >5% for 5 minutes
- [ ] HighLatency - P99 latency >5s for 5 minutes
- [ ] HighDatabasePoolUtilization - DB pool >90% for 2 minutes
- [ ] HighHeapUsage - JVM heap >85% for 5 minutes
- [ ] HighGCPauseTime - GC time >10% for 5 minutes
- [ ] HighQueueDepth - Queue depth >1000 for 5 minutes

### 7. Logstash Pipeline

Verify Logstash pipeline configuration in `logstash/pipeline/logstash.conf`:

- [ ] TCP input on port 5000
- [ ] UDP input on port 5000
- [ ] JSON codec for log parsing
- [ ] Elasticsearch output configured
- [ ] Index naming pattern includes service name and date

### 8. Grafana Provisioning

Verify Grafana provisioning configuration:

- [ ] Prometheus datasource configured at `http://prometheus:9090`
- [ ] Dashboard provider configured to load from `/var/lib/grafana/dashboards`
- [ ] Service health dashboard exists

### 9. Docker Networks and Volumes

Verify Docker Compose defines:

- [ ] Network: `observability` (bridge driver)
- [ ] Volume: `jaeger-data`
- [ ] Volume: `prometheus-data`
- [ ] Volume: `grafana-data`
- [ ] Volume: `elasticsearch-data`

### 10. Health Checks

Verify health checks are configured for:

- [ ] elasticsearch
- [ ] logstash
- [ ] kibana

### 11. Restart Policies

Verify all services have restart policy set to `unless-stopped`

### 12. Functional Testing

Start the stack and verify functionality:

```bash
cd observability/docker
docker-compose up -d
```

Wait for all services to be healthy:

```bash
docker-compose ps
```

Access UIs and verify they load:

- [ ] Jaeger UI: http://localhost:16686
- [ ] Prometheus UI: http://localhost:9090
- [ ] Grafana: http://localhost:3000 (admin/admin)
- [ ] Kibana: http://localhost:5601
- [ ] Elasticsearch: http://localhost:9200

Check Prometheus targets:

- [ ] Navigate to http://localhost:9090/targets
- [ ] Verify Prometheus is scraping itself

Check Grafana datasource:

- [ ] Log in to Grafana
- [ ] Go to Configuration > Data Sources
- [ ] Verify Prometheus datasource is configured and working

Stop the stack:

```bash
docker-compose down
```

## Validation Results

Date: _______________
Validated by: _______________

All checks passed: [ ] Yes [ ] No

Notes:
_________________________________________________________________
_________________________________________________________________
_________________________________________________________________
