# Grafana Dashboard Guide

## Table of Contents

1. [Overview](#overview)
2. [Accessing Grafana](#accessing-grafana)
3. [Service Health Dashboard](#service-health-dashboard)
4. [Request Flow Dashboard](#request-flow-dashboard)
5. [Error Tracking Dashboard](#error-tracking-dashboard)
6. [Infrastructure Dashboard](#infrastructure-dashboard)
7. [Creating Custom Dashboards](#creating-custom-dashboards)
8. [Tips and Best Practices](#tips-and-best-practices)

## Overview

Grafana is a visualization and analytics platform that displays metrics from Prometheus. In the SIF Unity Broker platform, Grafana provides pre-built dashboards for monitoring service health, performance, errors, and infrastructure.

### Key Features

- **Real-time Monitoring**: Live metrics updated every few seconds
- **Time Series Visualization**: Charts showing metrics over time
- **Alerting**: Visual indicators when metrics exceed thresholds
- **Drill-down**: Click on metrics to investigate further
- **Sharing**: Share dashboards and panels with team

### Available Dashboards

1. **Service Health**: Service availability, error rates, request rates
2. **Request Flow**: Request patterns, latency percentiles, throughput
3. **Error Tracking**: Error rates, error types, error trends
4. **Infrastructure**: CPU, memory, JVM metrics, database connections

## Accessing Grafana

### Local Development

**URL**: http://localhost:3000

**Default Credentials**:
- Username: `admin`
- Password: `admin`

**First Login**: You'll be prompted to change the password

### Production/Staging

**URL**: Provided by your infrastructure team (e.g., https://grafana.your-domain.com)

**Authentication**: SSO or provided credentials

### Navigation

**Main Menu** (left sidebar):
- **Home**: Dashboard list
- **Dashboards**: Browse and search dashboards
- **Explore**: Ad-hoc queries and exploration
- **Alerting**: Alert rules and notifications
- **Configuration**: Data sources, plugins, preferences

## Service Health Dashboard

### Purpose

Monitor the overall health and availability of all SIF Unity Broker services.

### Accessing

1. Click **Dashboards** in left menu
2. Search for "Service Health" or "SIF Service Health"
3. Click to open

### Panels

#### 1. Service Up/Down Status

**Type**: Gauge panel

**Description**: Shows current status of each service (Up/Down)

**Metrics**:
```promql
up{job=~"gateway|environment-service|queue-service|subscription-service|job-service|requests-connector|responses-connector|events-connector|services-connector"}
```

**Interpretation**:
- **Green (1)**: Service is up and responding to health checks
- **Red (0)**: Service is down or not responding

**Actions**:
- If service is down: Check service logs, restart service
- If multiple services down: Check infrastructure (network, database)

**Screenshot Description**: Grid of 9 gauges, one per service, showing green (up) or red (down) status

#### 2. Error Rate by Service

**Type**: Time series graph

**Description**: Shows percentage of requests resulting in errors (4xx/5xx) over time

**Metrics**:
```promql
sum(rate(http_server_requests_seconds_count{status=~"[45].."}[5m])) by (service) /
sum(rate(http_server_requests_seconds_count[5m])) by (service) * 100
```

**Interpretation**:
- **< 1%**: Normal error rate
- **1-5%**: Elevated error rate, investigate
- **> 5%**: High error rate, alert triggered

**Actions**:
- Hover over line to see exact error rate
- Click on service name in legend to isolate that service
- Check Error Tracking dashboard for details
- Search logs in Kibana for error messages

**Screenshot Description**: Line graph showing error rate percentage over time for each service, with different colored lines

#### 3. Request Rate by Service

**Type**: Time series graph

**Description**: Shows number of requests per second handled by each service

**Metrics**:
```promql
sum(rate(http_server_requests_seconds_count[5m])) by (service)
```

**Interpretation**:
- **Steady line**: Normal traffic pattern
- **Spikes**: Traffic bursts (may be normal or attack)
- **Drops**: Potential service issues or upstream failures
- **Gradual increase**: Growing traffic (capacity planning needed)

**Actions**:
- Compare with historical patterns
- Check if spike correlates with errors or latency
- Verify auto-scaling is working
- Plan capacity if sustained growth

**Screenshot Description**: Stacked area chart showing request rate for each service over time

#### 4. Recent Alerts

**Type**: Table panel

**Description**: Lists recent alerts that have fired

**Columns**:
- Alert name
- Service
- Severity
- Status (firing/resolved)
- Time

**Interpretation**:
- **Firing**: Alert is currently active
- **Resolved**: Alert has cleared

**Actions**:
- Click alert name to see details
- Click service to filter dashboard by that service
- Check related dashboards for more context

### Time Range Controls

**Location**: Top right corner

**Common Ranges**:
- Last 5 minutes (real-time monitoring)
- Last 1 hour (recent trends)
- Last 24 hours (daily patterns)
- Last 7 days (weekly trends)

**Auto-refresh**: Set refresh interval (5s, 10s, 30s, 1m)

### Variables

**Service Filter**: Dropdown to filter dashboard by specific service(s)

**Usage**:
1. Click service dropdown at top
2. Select one or more services
3. Dashboard updates to show only selected services

## Request Flow Dashboard

### Purpose

Analyze request patterns, latency, and throughput across services.

### Panels

#### 1. Request Rate by Endpoint

**Type**: Time series graph

**Description**: Shows requests per second for each endpoint

**Metrics**:
```promql
sum(rate(http_server_requests_seconds_count[5m])) by (uri, service)
```

**Interpretation**:
- Identify most frequently called endpoints
- Spot unusual traffic patterns
- Verify load distribution

**Actions**:
- Click endpoint in legend to isolate
- Compare with expected traffic patterns
- Check if high-traffic endpoints are optimized

#### 2. Latency Percentiles by Service

**Type**: Time series graph with multiple lines

**Description**: Shows p50, p95, and p99 latency for each service

**Metrics**:
```promql
# P50 (median)
histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket[5m])) by (service, le))

# P95
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (service, le))

# P99
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (service, le))
```

**Interpretation**:
- **P50 (median)**: Typical request latency
- **P95**: 95% of requests complete within this time
- **P99**: 99% of requests complete within this time (worst case for most users)

**Thresholds**:
- **< 100ms**: Excellent
- **100-500ms**: Good
- **500ms-2s**: Acceptable
- **> 2s**: Slow, investigate
- **> 5s**: Very slow, alert triggered

**Actions**:
- If p99 is high: Check for slow queries, external service calls
- If gap between p50 and p99 is large: Investigate outliers
- Compare services to identify bottlenecks

**Screenshot Description**: Graph showing three lines per service (p50, p95, p99) with different colors/styles

#### 3. Request Duration Heatmap

**Type**: Heatmap

**Description**: Visual representation of request duration distribution

**Interpretation**:
- **Horizontal axis**: Time
- **Vertical axis**: Duration buckets
- **Color intensity**: Number of requests in that duration bucket
- **Hot spots**: Concentrations of requests at specific durations

**Actions**:
- Identify patterns (e.g., all requests ~100ms)
- Spot anomalies (e.g., sudden spike in slow requests)
- Compare with latency percentiles

#### 4. Top Slowest Endpoints

**Type**: Table

**Description**: Lists endpoints with highest p99 latency

**Columns**:
- Endpoint URI
- Service
- P99 latency
- Request count

**Interpretation**:
- Prioritize optimization efforts
- Identify endpoints needing caching
- Find candidates for async processing

**Actions**:
- Click endpoint to see traces in Jaeger
- Check logs in Kibana for that endpoint
- Review code for optimization opportunities

### Using This Dashboard

**Scenario 1: Performance Investigation**
1. Set time range to match performance issue
2. Check latency percentiles for affected service
3. Identify slow endpoints in table
4. Click endpoint to see traces in Jaeger

**Scenario 2: Capacity Planning**
1. Set time range to last 30 days
2. Analyze request rate trends
3. Check if latency is increasing with traffic
4. Plan scaling based on trends

## Error Tracking Dashboard

### Purpose

Monitor and analyze errors across the system.

### Panels

#### 1. Error Rate by Service

**Type**: Time series graph

**Description**: Shows error rate (percentage) for each service over time

**Metrics**:
```promql
sum(rate(http_server_requests_seconds_count{status=~"[45].."}[5m])) by (service) /
sum(rate(http_server_requests_seconds_count[5m])) by (service) * 100
```

**Interpretation**:
- **< 1%**: Normal
- **1-5%**: Elevated, investigate
- **> 5%**: High, alert triggered

**Actions**:
- Identify which service has errors
- Check if errors correlate with deployments
- Search logs in Kibana for error details

#### 2. Error Count by Type

**Type**: Pie chart

**Description**: Distribution of errors by HTTP status code

**Metrics**:
```promql
sum(rate(http_server_requests_seconds_count{status=~"[45].."}[5m])) by (status)
```

**Interpretation**:
- **4xx errors**: Client errors (bad requests, auth failures, not found)
  - 400: Bad request (validation errors)
  - 401: Unauthorized (authentication failures)
  - 403: Forbidden (authorization failures)
  - 404: Not found (invalid endpoints or resources)
- **5xx errors**: Server errors (internal errors, service unavailable)
  - 500: Internal server error (bugs, exceptions)
  - 502: Bad gateway (upstream service errors)
  - 503: Service unavailable (overload, maintenance)
  - 504: Gateway timeout (slow upstream services)

**Actions**:
- High 4xx: Check client requests, API documentation
- High 5xx: Check service logs, database, external services

**Screenshot Description**: Pie chart with slices for each status code, labeled with percentage

#### 3. Recent Error Logs

**Type**: Table with links

**Description**: Lists recent error log entries with links to Kibana

**Columns**:
- Timestamp
- Service
- Message
- Trace ID (link to Jaeger)
- View in Kibana (link)

**Actions**:
- Click trace ID to see full trace in Jaeger
- Click Kibana link to see full log entry and context
- Analyze error patterns

#### 4. Error Rate Change

**Type**: Stat panel

**Description**: Shows change in error rate compared to previous period

**Metrics**:
```promql
# Current error rate
sum(rate(http_server_requests_seconds_count{status=~"[45].."}[5m]))

# Previous period error rate (for comparison)
sum(rate(http_server_requests_seconds_count{status=~"[45].."}[5m] offset 1h))
```

**Interpretation**:
- **Green arrow down**: Error rate decreasing (good)
- **Red arrow up**: Error rate increasing (investigate)
- **Percentage**: Magnitude of change

**Actions**:
- If increasing: Check recent deployments, infrastructure changes
- If decreasing: Verify fix was effective

### Using This Dashboard

**Scenario 1: Error Spike Investigation**
1. Notice error rate spike in graph
2. Check error count by type to identify status codes
3. Click on recent error logs to see details
4. Follow trace ID to Jaeger for full context
5. Search Kibana for related errors

**Scenario 2: Post-Deployment Validation**
1. Deploy new version
2. Monitor error rate for 30 minutes
3. Check error rate change panel
4. Verify no increase in errors
5. Check error types haven't changed

## Infrastructure Dashboard

### Purpose

Monitor resource utilization and system health.

### Panels

#### 1. CPU Usage by Service

**Type**: Time series graph

**Description**: Shows CPU usage percentage for each service

**Metrics**:
```promql
process_cpu_usage{job=~"gateway|environment-service|queue-service|subscription-service|job-service|requests-connector|responses-connector|events-connector|services-connector"} * 100
```

**Interpretation**:
- **< 50%**: Normal
- **50-80%**: Moderate load
- **80-95%**: High load, consider scaling
- **> 95%**: Critical, scale immediately

**Actions**:
- If consistently high: Scale horizontally or optimize code
- If spikes: Check for traffic bursts or inefficient operations
- Compare with request rate to understand load

**Screenshot Description**: Line graph showing CPU percentage for each service over time

#### 2. Memory Usage by Service

**Type**: Time series graph

**Description**: Shows heap memory usage as percentage of max heap

**Metrics**:
```promql
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100
```

**Interpretation**:
- **< 70%**: Normal
- **70-85%**: Moderate usage
- **85-95%**: High usage, monitor closely
- **> 95%**: Critical, risk of OutOfMemoryError

**Actions**:
- If consistently high: Increase heap size or investigate memory leaks
- If sawtooth pattern: Normal GC behavior
- If steadily increasing: Possible memory leak

#### 3. JVM Heap Usage

**Type**: Time series graph with multiple metrics

**Description**: Shows heap memory used, committed, and max

**Metrics**:
```promql
# Used heap
jvm_memory_used_bytes{area="heap"}

# Committed heap
jvm_memory_committed_bytes{area="heap"}

# Max heap
jvm_memory_max_bytes{area="heap"}
```

**Interpretation**:
- **Used**: Currently allocated objects
- **Committed**: Memory guaranteed by OS
- **Max**: Maximum heap size (-Xmx)

**Pattern**:
- Sawtooth: Normal (GC reclaiming memory)
- Steadily increasing: Possible memory leak
- Flat at max: Out of memory, increase heap

#### 4. GC Pause Time

**Type**: Time series graph

**Description**: Shows time spent in garbage collection

**Metrics**:
```promql
rate(jvm_gc_pause_seconds_sum[5m]) * 100
```

**Interpretation**:
- **< 1%**: Excellent
- **1-5%**: Normal
- **5-10%**: High, consider tuning
- **> 10%**: Critical, GC is impacting performance

**Actions**:
- If high: Tune GC settings, increase heap, reduce object allocation
- Check GC algorithm (G1GC recommended for most cases)
- Consider different GC (ZGC, Shenandoah for low latency)

#### 5. Thread Count

**Type**: Time series graph

**Description**: Shows number of live threads in JVM

**Metrics**:
```promql
jvm_threads_live
```

**Interpretation**:
- **Steady**: Normal
- **Gradually increasing**: Possible thread leak
- **Spikes**: Thread pool adjusting to load

**Typical Values**:
- Gateway: 50-200 threads
- Services: 20-100 threads

**Actions**:
- If steadily increasing: Check for thread leaks
- If very high: Review thread pool configuration

#### 6. Database Connection Pool Utilization

**Type**: Time series graph

**Description**: Shows active connections as percentage of pool size

**Metrics**:
```promql
db_pool_active / db_pool_total * 100
```

**Interpretation**:
- **< 50%**: Normal
- **50-80%**: Moderate usage
- **80-90%**: High usage, consider increasing pool
- **> 90%**: Critical, requests may be waiting for connections

**Actions**:
- If consistently high: Increase pool size
- If spikes: Check for slow queries holding connections
- Monitor connection wait time

**Screenshot Description**: Graph showing connection pool utilization percentage for each service

### Using This Dashboard

**Scenario 1: Performance Degradation**
1. Check CPU usage for affected service
2. Check memory usage and GC pause time
3. Check thread count for thread exhaustion
4. Check database pool for connection exhaustion
5. Correlate with request rate and latency

**Scenario 2: Capacity Planning**
1. Set time range to last 30 days
2. Analyze CPU and memory trends
3. Check peak usage during high traffic
4. Plan scaling based on growth trends
5. Set alerts for thresholds

## Creating Custom Dashboards

### Creating a New Dashboard

**Steps**:
1. Click **+** in left menu → **Dashboard**
2. Click **Add visualization**
3. Select data source: Prometheus
4. Enter PromQL query
5. Configure visualization type and options
6. Click **Apply**
7. Add more panels as needed
8. Click **Save dashboard**

### Common Panel Types

#### Time Series Graph

**Use Case**: Show metrics over time

**Configuration**:
- Query: PromQL expression
- Legend: `{{label}}` to show label values
- Y-axis: Unit (percent, bytes, seconds)
- Thresholds: Add warning/critical lines

#### Gauge

**Use Case**: Show current value with thresholds

**Configuration**:
- Query: Current value
- Min/Max: Set range
- Thresholds: Green/yellow/red zones

#### Stat

**Use Case**: Show single number with trend

**Configuration**:
- Query: Current value
- Calculation: Last, Mean, Max, etc.
- Graph mode: Show sparkline

#### Table

**Use Case**: Show multiple metrics in rows

**Configuration**:
- Multiple queries
- Column formatting
- Sorting

### Example: Custom Queue Metrics Dashboard

**Panel 1: Queue Depth**
```promql
sif_queue_depth{queue_name=~".*"}
```

**Panel 2: Messages Processed**
```promql
rate(sif_queue_messages_processed_total[5m])
```

**Panel 3: Processing Time**
```promql
histogram_quantile(0.99, rate(sif_queue_processing_seconds_bucket[5m]))
```

### Variables

**Use Case**: Create dynamic dashboards with filters

**Creating a Variable**:
1. Dashboard settings → Variables → Add variable
2. Name: `service`
3. Type: Query
4. Query: `label_values(up, service)`
5. Save

**Using Variable**:
```promql
up{service="$service"}
```

**Result**: Dropdown to filter dashboard by service

### Templating

**Use Case**: Reuse dashboard for different environments

**Steps**:
1. Create variables for environment, namespace, etc.
2. Use variables in queries
3. Import dashboard with different variable values

## Tips and Best Practices

### Dashboard Design

1. **Organize Logically**: Group related panels together
2. **Use Consistent Colors**: Same service = same color across panels
3. **Add Descriptions**: Panel descriptions help users understand metrics
4. **Set Appropriate Ranges**: Y-axis ranges should match typical values
5. **Use Thresholds**: Visual indicators for warning/critical levels

### Query Optimization

1. **Use Recording Rules**: Pre-compute expensive queries
2. **Limit Time Range**: Shorter ranges query faster
3. **Use Rate**: `rate()` instead of `increase()` for per-second rates
4. **Aggregate Early**: `sum by (label)` before `rate()`
5. **Avoid High Cardinality**: Don't group by unbounded labels

### Monitoring Best Practices

1. **Set Alerts**: Don't rely on manual dashboard checking
2. **Use Auto-refresh**: Keep dashboards live during incidents
3. **Create Runbooks**: Link dashboards to troubleshooting guides
4. **Share Dashboards**: Make dashboards accessible to team
5. **Version Control**: Export dashboard JSON to git

### Performance Tips

1. **Limit Panels**: Too many panels slow down dashboard
2. **Use Variables**: Filter data instead of showing everything
3. **Disable Auto-refresh**: When not needed
4. **Use Shorter Time Ranges**: For real-time monitoring
5. **Cache Queries**: Use recording rules for expensive queries

### Collaboration Tips

1. **Naming Conventions**: Use clear, consistent dashboard names
2. **Folders**: Organize dashboards into folders by team/service
3. **Permissions**: Set appropriate view/edit permissions
4. **Annotations**: Add annotations for deployments, incidents
5. **Snapshots**: Share dashboard snapshots with team

## Integration with Other Tools

### Jaeger Integration

**Use Case**: Jump from metrics to traces

**Configuration**:
1. Add data link to panel
2. URL: `http://jaeger:16686/search?service=${__field.labels.service}`
3. Click metric to open Jaeger

### Kibana Integration

**Use Case**: Jump from metrics to logs

**Configuration**:
1. Add data link to panel
2. URL: `http://kibana:5601/app/discover#/?_a=(query:(query_string:(query:'service:${__field.labels.service}')))`
3. Click metric to open Kibana

### Alert Integration

**Use Case**: Show alerts on dashboard

**Configuration**:
1. Add alert rule to panel
2. Configure notification channels
3. Alerts appear on dashboard

## Quick Reference

### Common PromQL Queries

```promql
# Service up/down
up{job="service-name"}

# Request rate
rate(http_server_requests_seconds_count[5m])

# Error rate
rate(http_server_requests_seconds_count{status=~"[45].."}[5m])

# P99 latency
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))

# CPU usage
process_cpu_usage * 100

# Memory usage
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# GC pause time
rate(jvm_gc_pause_seconds_sum[5m]) * 100

# Database pool utilization
db_pool_active / db_pool_total * 100
```

### Keyboard Shortcuts

- `d k`: Toggle kiosk mode (full screen)
- `d s`: Save dashboard
- `d e`: Expand/collapse row
- `Ctrl+S` / `Cmd+S`: Save dashboard
- `Ctrl+H` / `Cmd+H`: Hide/show controls
- `t`: Open time picker
- `r`: Refresh dashboard

### Time Range Shortcuts

- `now-5m`: Last 5 minutes
- `now-1h`: Last 1 hour
- `now-24h`: Last 24 hours
- `now-7d`: Last 7 days
- `now-30d`: Last 30 days

## Additional Resources

- [Grafana Documentation](https://grafana.com/docs/grafana/latest/)
- [PromQL Documentation](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Dashboard Best Practices](https://grafana.com/docs/grafana/latest/best-practices/best-practices-for-creating-dashboards/)
- [Configuration Guide](./configuration-guide.md)
- [Troubleshooting Runbooks](./troubleshooting-runbooks.md)
- [Jaeger Usage Guide](./jaeger-usage-guide.md)
- [Kibana Usage Guide](./kibana-usage-guide.md)
