# SIF Unity Broker Observability Stack

This directory contains the Docker Compose configuration for running the complete observability stack locally for development and testing.

## Overview

The observability stack includes:

- **Jaeger** - Distributed tracing backend and UI
- **Prometheus** - Metrics collection and time-series database
- **Grafana** - Visualization and dashboards
- **Elasticsearch** - Log storage and search
- **Logstash** - Log processing and aggregation
- **Kibana** - Log visualization and analysis

## Prerequisites

- Docker Engine 20.10+
- Docker Compose 2.0+
- At least 4GB of available RAM
- Ports 3000, 5000, 5601, 9090, 9200, 14250, 16686 available

## Quick Start

### 1. Start the Observability Stack

```bash
cd observability/docker
docker-compose up -d
```

This will start all observability services in the background.

### 2. Verify Services are Running

```bash
docker-compose ps
```

All services should show status as "Up" or "healthy".

### 3. Access the UIs

Once all services are running, you can access:

- **Jaeger UI**: http://localhost:16686
  - View distributed traces
  - Search by trace ID, service, operation
  - Analyze request timelines

- **Prometheus UI**: http://localhost:9090
  - Query metrics using PromQL
  - View targets and their health
  - Check alerting rules

- **Grafana**: http://localhost:3000
  - Default credentials: admin/admin
  - Pre-configured dashboards for SIF services
  - Prometheus datasource already configured

- **Kibana**: http://localhost:5601
  - Search and analyze logs
  - Create custom queries and visualizations
  - View logs correlated with traces

- **Elasticsearch**: http://localhost:9200
  - Direct access to log data
  - REST API for advanced queries

## Configuration

### Prometheus Scrape Configuration

The Prometheus configuration (`prometheus/prometheus.yml`) is set up to scrape metrics from all 9 SIF Unity Broker services:

- Gateway (port 8080)
- Environment Service (port 8081)
- Queue Service (port 8082)
- Subscription Service (port 8083)
- Job Service (port 8084)
- Requests Connector (port 8085)
- Responses Connector (port 8086)
- Events Connector (port 8087)
- Services Connector (port 8088)

**Note**: Services must expose metrics at `/actuator/prometheus` endpoint.

### Prometheus Alerts

Alert rules are defined in `prometheus/alerts.yml`:

- **ServiceDown**: Triggers when a service is down for >1 minute
- **HighErrorRate**: Triggers when error rate exceeds 5% for 5 minutes
- **HighLatency**: Triggers when p99 latency exceeds 5 seconds for 5 minutes
- **HighDatabasePoolUtilization**: Triggers when DB pool usage exceeds 90% for 2 minutes
- **HighHeapUsage**: Triggers when JVM heap usage exceeds 85% for 5 minutes
- **HighGCPauseTime**: Triggers when GC pause time exceeds 10% for 5 minutes
- **HighQueueDepth**: Triggers when queue depth exceeds 1000 for 5 minutes

### Grafana Dashboards

Pre-configured dashboards are available in `grafana/dashboards/`:

- **Service Health**: Service status, error rates, request rates
- (Additional dashboards can be added as JSON files)

Dashboards are automatically provisioned when Grafana starts.

### Logstash Pipeline

The Logstash pipeline (`logstash/pipeline/logstash.conf`) is configured to:

1. Receive JSON logs via TCP on port 5000
2. Parse timestamps and add metadata
3. Tag logs based on level and trace information
4. Send logs to Elasticsearch with daily indices per service

## Service Configuration

### Connecting SIF Services to Observability Stack

Configure your SIF Unity Broker services with these environment variables:

```yaml
# Tracing
JAEGER_ENDPOINT: http://localhost:14250
TRACING_SAMPLING_RATE: 1.0

# Logging
LOGSTASH_HOST: localhost
LOGSTASH_PORT: 5000

# Metrics (exposed via Spring Boot Actuator)
management.endpoints.web.exposure.include: health,info,prometheus,metrics
```

Or in `application.yml`:

```yaml
observability:
  tracing:
    enabled: true
    jaeger:
      endpoint: http://localhost:14250
    sampling:
      probability: 1.0

# Logback will automatically send logs to Logstash via TCP
```

## Usage Examples

### Viewing Traces in Jaeger

1. Open http://localhost:16686
2. Select a service from the dropdown
3. Click "Find Traces"
4. Click on a trace to see the detailed timeline
5. Use the trace ID to correlate with logs in Kibana

### Querying Metrics in Prometheus

Example queries:

```promql
# Request rate by service
sum(rate(http_server_requests_seconds_count[5m])) by (service)

# Error rate
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service)

# P99 latency
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (service, le))

# JVM heap usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
```

### Searching Logs in Kibana

1. Open http://localhost:5601
2. Go to "Discover"
3. Create an index pattern: `sif-logs-*`
4. Search examples:
   - By trace ID: `trace_id:"4bf92f3577b34da6a3ce929d0e0e4736"`
   - By service: `service:"queue-service"`
   - By log level: `level:"error"`
   - By correlation ID: `correlation_id:"550e8400-e29b-41d4-a716-446655440000"`

## Troubleshooting

### Services Not Starting

Check logs for a specific service:

```bash
docker-compose logs -f <service-name>
```

For example:
```bash
docker-compose logs -f elasticsearch
```

### Elasticsearch Won't Start

Elasticsearch requires sufficient memory. Increase Docker memory allocation to at least 4GB.

On Linux, you may need to increase `vm.max_map_count`:

```bash
sudo sysctl -w vm.max_map_count=262144
```

### Prometheus Not Scraping Services

1. Check that SIF services are running and exposing metrics
2. Verify the service ports in `prometheus/prometheus.yml`
3. Check Prometheus targets: http://localhost:9090/targets
4. Ensure `host.docker.internal` resolves correctly (or use actual host IP)

### Logs Not Appearing in Kibana

1. Verify Logstash is receiving logs:
   ```bash
   docker-compose logs -f logstash
   ```

2. Check that services are configured to send logs to `localhost:5000`

3. Verify Elasticsearch indices exist:
   ```bash
   curl http://localhost:9200/_cat/indices
   ```

4. Create index pattern in Kibana if not auto-created

### Grafana Dashboards Not Loading

1. Check that Prometheus datasource is configured:
   - Go to Configuration > Data Sources
   - Verify Prometheus URL is `http://prometheus:9090`

2. Check dashboard provisioning:
   ```bash
   docker-compose logs -f grafana
   ```

## Stopping the Stack

To stop all services:

```bash
docker-compose down
```

To stop and remove all data volumes:

```bash
docker-compose down -v
```

**Warning**: This will delete all stored traces, metrics, and logs!

## Resource Usage

Typical resource usage:

- **CPU**: 1-2 cores under normal load
- **Memory**: 3-4 GB total
- **Disk**: 1-5 GB depending on retention

For production deployments, see the Kubernetes manifests in the parent directory.

## Maintenance

### Cleaning Up Old Data

Elasticsearch indices can grow large over time. To delete old indices:

```bash
# Delete indices older than 7 days
curl -X DELETE "localhost:9200/sif-logs-*-$(date -d '7 days ago' +%Y.%m.%d)"
```

### Backing Up Data

To backup persistent volumes:

```bash
docker-compose down
docker run --rm -v sif-prometheus-data:/data -v $(pwd):/backup alpine tar czf /backup/prometheus-backup.tar.gz /data
docker run --rm -v sif-grafana-data:/data -v $(pwd):/backup alpine tar czf /backup/grafana-backup.tar.gz /data
```

## Next Steps

- Configure SIF services to send telemetry to this stack
- Create custom Grafana dashboards for your use cases
- Set up alert notifications (email, Slack, PagerDuty)
- Review and adjust retention policies
- Consider deploying to Kubernetes for production

## Support

For issues or questions:

1. Check the troubleshooting section above
2. Review service logs: `docker-compose logs <service>`
3. Consult the main observability documentation
4. Check the requirements and design documents in `.kiro/specs/observability-platform/`
