# SIF Unity Broker Observability Platform Documentation

## Overview

This directory contains comprehensive documentation for the SIF Unity Broker observability platform, which provides distributed tracing, centralized logging, and metrics collection across all 9 microservices.

## Documentation Index

### Getting Started

1. **[Configuration Guide](./configuration-guide.md)**
   - Complete reference for all configuration properties
   - Environment-specific configuration examples (dev, test, prod)
   - Environment variable overrides
   - Validation and troubleshooting

2. **[Deployment and Operations Guide](./deployment-operations-guide.md)**
   - Local deployment with Docker Compose
   - Production deployment to Kubernetes
   - Configuring alerting destinations
   - Managing retention policies
   - Scaling observability components
   - Backup and recovery procedures

### Using the Observability Platform

3. **[Jaeger Usage Guide](./jaeger-usage-guide.md)**
   - Searching for traces by trace ID, correlation ID, service, and operation
   - Analyzing trace timelines and identifying performance issues
   - Understanding span relationships and dependencies
   - Advanced search techniques

4. **[Kibana Usage Guide](./kibana-usage-guide.md)**
   - Searching logs by trace ID, correlation ID, service, and level
   - Creating custom queries with KQL
   - Building visualizations and dashboards
   - Correlating logs with traces

5. **[Grafana Dashboard Guide](./grafana-dashboard-guide.md)**
   - Interpreting service health dashboard
   - Interpreting request flow dashboard
   - Interpreting error tracking dashboard
   - Interpreting infrastructure dashboard
   - Creating custom dashboards

### Development

6. **[Custom Instrumentation Guide](./custom-instrumentation-guide.md)**
   - Creating custom spans for business operations
   - Adding span attributes for context
   - Creating custom metrics (counters, gauges, timers)
   - Adding structured log fields
   - Best practices and examples

### Operations

7. **[Troubleshooting Runbooks](./troubleshooting-runbooks.md)**
   - High latency investigation
   - Error spike investigation
   - Service down investigation
   - Trace not appearing in Jaeger
   - Logs not appearing in Kibana

## Quick Links

### Local Development

- **Jaeger UI**: http://localhost:16686
- **Prometheus UI**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)
- **Kibana**: http://localhost:5601
- **Elasticsearch**: http://localhost:9200

### Key Concepts

- **Trace**: Complete journey of a request through the system
- **Span**: Single unit of work within a trace
- **Trace ID**: Unique identifier for a trace (32 hex chars)
- **Correlation ID**: Business identifier for tracking requests
- **Metric**: Numerical measurement of system behavior
- **Log**: Structured JSON log entry with trace correlation

### Common Tasks

#### View Traces for a Request

1. Get trace ID from logs or response headers
2. Open Jaeger: http://localhost:16686
3. Paste trace ID in search box
4. Analyze trace timeline

#### Search Logs for a Trace

1. Copy trace ID from Jaeger
2. Open Kibana: http://localhost:5601
3. Search: `trace_id:"<trace-id>"`
4. View all logs for that trace

#### Check Service Metrics

1. Open Grafana: http://localhost:3000
2. Navigate to "Service Health" dashboard
3. View service status, error rates, request rates

#### Create Custom Span

```java
tracingUtils.traceOperation(
    "operation.name",
    Map.of("attribute", "value"),
    () -> yourBusinessLogic()
);
```

#### Create Custom Metric

```java
metricsService.incrementQueueMessageProcessed(
    queueName,
    "success"
);
```

## Architecture

### Components

- **Jaeger**: Distributed tracing backend (OpenTelemetry compatible)
- **Prometheus**: Metrics collection and time-series database
- **Grafana**: Visualization and dashboards
- **Elasticsearch**: Log storage and search engine
- **Logstash**: Log processing and aggregation
- **Kibana**: Log visualization and analysis

### Data Flow

```
SIF Services
    ├─ Traces → Jaeger (via OpenTelemetry)
    ├─ Logs → Logstash → Elasticsearch ← Kibana
    └─ Metrics → Prometheus ← Grafana
```

### Integration

- **Trace-Log Correlation**: Logs include trace_id and span_id
- **Trace-Metric Correlation**: Metrics tagged with service name
- **Cross-Tool Navigation**: Links between Jaeger, Kibana, and Grafana

## Configuration Files

### Service Configuration

- `sif-broker-common/src/main/resources/observability-defaults.yml`
- `sif-broker-common/src/main/resources/logback-spring.xml`

### Docker Compose

- `observability/docker/docker-compose.yml`
- `observability/docker/prometheus/prometheus.yml`
- `observability/docker/prometheus/alerts.yml`
- `observability/docker/grafana/provisioning/`
- `observability/docker/logstash/pipeline/logstash.conf`

### Kubernetes

- See [Deployment and Operations Guide](./deployment-operations-guide.md) for Kubernetes manifests

## Support

### Troubleshooting

1. Check [Troubleshooting Runbooks](./troubleshooting-runbooks.md)
2. Review service logs
3. Check observability component health
4. Consult configuration guide

### Common Issues

- **Traces not appearing**: Check sampling rate, Jaeger connectivity
- **Logs not appearing**: Check Logstash connectivity, Elasticsearch health
- **High memory usage**: Reduce sampling rate, adjust retention policies
- **Slow performance**: Check resource utilization, optimize queries

### Getting Help

1. Review relevant documentation guide
2. Check troubleshooting runbooks
3. Examine service and observability component logs
4. Contact platform team with diagnostic information

## Additional Resources

- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Elasticsearch Documentation](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)
- [Kibana Documentation](https://www.elastic.co/guide/en/kibana/current/index.html)

## Contributing

When updating documentation:

1. Keep examples practical and tested
2. Include screenshots descriptions where helpful
3. Update this README if adding new guides
4. Maintain consistent formatting and style
5. Cross-reference related guides

## Version

Documentation Version: 1.0
Last Updated: 2024-01-15
Platform Version: SIF Unity Broker v1.0
