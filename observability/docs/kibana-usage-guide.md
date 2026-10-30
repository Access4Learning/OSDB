# Kibana Usage Guide

## Table of Contents

1. [Overview](#overview)
2. [Accessing Kibana](#accessing-kibana)
3. [Setting Up Index Patterns](#setting-up-index-patterns)
4. [Searching Logs](#searching-logs)
5. [Creating Custom Queries](#creating-custom-queries)
6. [Visualizations and Dashboards](#visualizations-and-dashboards)
7. [Common Use Cases](#common-use-cases)
8. [Tips and Best Practices](#tips-and-best-practices)

## Overview

Kibana is a data visualization and exploration tool for Elasticsearch. In the SIF Unity Broker platform, Kibana provides a powerful interface for searching, analyzing, and visualizing logs from all 9 microservices.

### Key Features

- **Discover**: Search and filter logs in real-time
- **Visualize**: Create charts and graphs from log data
- **Dashboard**: Combine visualizations into comprehensive dashboards
- **Dev Tools**: Execute Elasticsearch queries directly
- **Saved Searches**: Save and share common queries

### Log Structure

All logs are stored in JSON format with consistent fields:

```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "service": "queue-service",
  "environment": "production",
  "level": "INFO",
  "logger": "org.sifunity.broker.queue.QueueService",
  "thread": "http-nio-8080-exec-1",
  "message": "Processing message from queue",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7",
  "correlation_id": "550e8400-e29b-41d4-a716-446655440000",
  "queue_name": "immediate-requests",
  "message_id": "msg-12345"
}
```

## Accessing Kibana

### Local Development

**URL**: http://localhost:5601

### Production/Staging

**URL**: Provided by your infrastructure team (e.g., https://kibana.your-domain.com)

### First-Time Setup

1. Open Kibana URL
2. Wait for Kibana to initialize (may take 1-2 minutes)
3. You'll be directed to the home page
4. Set up index patterns (see next section)

## Setting Up Index Patterns

Index patterns tell Kibana which Elasticsearch indices to search.

### Creating the SIF Logs Index Pattern

**Steps**:
1. Click **☰** menu → **Stack Management**
2. Click **Index Patterns** under Kibana
3. Click **Create index pattern**
4. Enter index pattern: `sif-logs-*`
5. Click **Next step**
6. Select **timestamp** as the time field
7. Click **Create index pattern**

**Screenshot Description**: Index pattern creation showing `sif-logs-*` pattern matching multiple indices

### Verifying Index Pattern

**Steps**:
1. Go to **Stack Management** → **Index Patterns**
2. Click on `sif-logs-*`
3. Verify fields are listed (timestamp, service, level, message, etc.)

**Common Fields**:
- `timestamp`: Log timestamp
- `service`: Service name
- `level`: Log level (INFO, WARN, ERROR, DEBUG)
- `message`: Log message
- `trace_id`: Distributed trace ID
- `span_id`: Span ID within trace
- `correlation_id`: Business correlation ID
- `logger`: Logger class name
- `thread`: Thread name

## Searching Logs

### Basic Search (Discover Page)

#### Accessing Discover

1. Click **☰** menu → **Discover**
2. Select `sif-logs-*` index pattern (if not already selected)
3. Set time range (top right)

#### Time Range Selection

**Common Ranges**:
- Last 15 minutes
- Last 1 hour
- Last 24 hours
- Last 7 days
- Custom range

**Quick Time Range**:
- Click clock icon in top right
- Select from quick ranges
- Or click **Absolute** tab for specific dates/times

#### Viewing Logs

**Log Display**:
- Each log entry is shown as a row
- Click **>** to expand and see all fields
- Click **<** to collapse

**Columns**:
- Default: timestamp and message
- Add columns: Click **+** next to field name in left sidebar
- Remove columns: Click **x** on column header
- Reorder columns: Drag column headers

### Search by Trace ID

**Use Case**: Find all logs for a specific trace

**Query**:
```
trace_id:"4bf92f3577b34da6a3ce929d0e0e4736"
```

**Steps**:
1. Copy trace ID from Jaeger or application logs
2. Paste query in search bar
3. Press Enter

**Result**: All logs from all services for that trace

**Screenshot Description**: Search results showing logs from multiple services (gateway, queue-service, database) all with the same trace_id

### Search by Correlation ID

**Use Case**: Track a business transaction

**Query**:
```
correlation_id:"550e8400-e29b-41d4-a716-446655440000"
```

**Steps**:
1. Get correlation ID from user or system
2. Enter query in search bar
3. Press Enter

**Result**: All logs related to that business transaction

### Search by Service

**Use Case**: View logs from a specific service

**Query**:
```
service:"queue-service"
```

**Multiple Services**:
```
service:("queue-service" OR "subscription-service")
```

**All Services Except One**:
```
NOT service:"gateway"
```

### Search by Log Level

**Use Case**: Find errors or warnings

**Error Logs Only**:
```
level:"ERROR"
```

**Errors and Warnings**:
```
level:("ERROR" OR "WARN")
```

**Info and Above** (exclude DEBUG):
```
NOT level:"DEBUG"
```

### Search by Service and Level

**Use Case**: Find errors in a specific service

**Query**:
```
service:"queue-service" AND level:"ERROR"
```

**Alternative Syntax**:
```
service:"queue-service" level:"ERROR"
```

### Full-Text Search

**Use Case**: Search for specific text in log messages

**Simple Text**:
```
"database connection failed"
```

**Wildcard Search**:
```
message:*timeout*
```

**Phrase Search**:
```
message:"processing message from queue"
```

### Field-Specific Search

**Use Case**: Search specific fields

**Queue Name**:
```
queue_name:"immediate-requests"
```

**Message Type**:
```
message_type:"StudentPersonal"
```

**Zone ID**:
```
zone_id:"test-zone"
```

**HTTP Status**:
```
http_status_code:500
```

## Creating Custom Queries

### Query Syntax (KQL - Kibana Query Language)

#### Basic Operators

**AND**:
```
service:"queue-service" AND level:"ERROR"
```

**OR**:
```
level:"ERROR" OR level:"WARN"
```

**NOT**:
```
NOT level:"DEBUG"
```

**Grouping**:
```
service:"queue-service" AND (level:"ERROR" OR level:"WARN")
```

#### Comparison Operators

**Exists**:
```
trace_id:*
```

**Does Not Exist**:
```
NOT trace_id:*
```

**Greater Than**:
```
http_status_code >= 500
```

**Less Than**:
```
response_time < 1000
```

**Range**:
```
http_status_code >= 400 AND http_status_code < 500
```

#### Wildcards

**Prefix**:
```
message:database*
```

**Suffix**:
```
message:*failed
```

**Contains**:
```
message:*connection*
```

### Advanced Query Examples

#### Find Slow Requests

```
service:"queue-service" AND message:*slow* AND level:"WARN"
```

#### Find Database Errors

```
logger:*database* AND level:"ERROR"
```

#### Find Specific Exception

```
message:*NullPointerException*
```

#### Find Logs Without Trace ID

```
NOT trace_id:*
```

#### Find Logs from Specific Thread

```
thread:"http-nio-8080-exec-1"
```

#### Find Logs in Time Range with Condition

```
service:"queue-service" AND level:"ERROR" AND timestamp >= "2024-01-15T10:00:00" AND timestamp <= "2024-01-15T11:00:00"
```

### Saving Searches

**Steps**:
1. Create your query
2. Click **Save** in top right
3. Enter name (e.g., "Queue Service Errors")
4. Click **Save**

**Loading Saved Search**:
1. Click **Open** in top right
2. Select saved search from list

**Sharing Saved Search**:
1. Open saved search
2. Click **Share** in top right
3. Copy link or export as CSV

### Filtering

#### Adding Filters

**Method 1: Click Field Value**
1. Expand a log entry
2. Click **+** next to field value to filter for it
3. Click **-** to filter it out

**Method 2: Add Filter Button**
1. Click **Add filter** below search bar
2. Select field (e.g., "service")
3. Select operator (e.g., "is")
4. Enter value (e.g., "queue-service")
5. Click **Save**

**Screenshot Description**: Filter builder showing field selection, operator, and value input

#### Managing Filters

**Enable/Disable**: Click filter to toggle
**Edit**: Click pencil icon on filter
**Delete**: Click X on filter
**Invert**: Click filter → **Invert**
**Pin**: Click filter → **Pin across all apps**

#### Filter Examples

**Service Filter**:
- Field: service
- Operator: is
- Value: queue-service

**Level Filter**:
- Field: level
- Operator: is one of
- Values: ERROR, WARN

**Time Range Filter**:
- Field: timestamp
- Operator: is between
- Values: 2024-01-15 10:00:00 to 2024-01-15 11:00:00

## Visualizations and Dashboards

### Creating Visualizations

#### Log Count Over Time

**Steps**:
1. Go to **Visualize** → **Create visualization**
2. Select **Line** chart
3. Select `sif-logs-*` index pattern
4. Y-axis: Count
5. X-axis: Date Histogram on timestamp
6. Click **Update**
7. Save visualization

**Use Case**: See log volume trends

#### Error Count by Service

**Steps**:
1. Create **Pie** chart
2. Add filter: `level:"ERROR"`
3. Slice by: service.keyword
4. Save visualization

**Use Case**: Identify which services have most errors

#### Log Level Distribution

**Steps**:
1. Create **Bar** chart
2. X-axis: level.keyword
3. Y-axis: Count
4. Save visualization

**Use Case**: See distribution of log levels

### Creating Dashboards

**Steps**:
1. Go to **Dashboard** → **Create dashboard**
2. Click **Add** → Select saved visualizations
3. Arrange visualizations on dashboard
4. Click **Save**
5. Enter dashboard name

**Example Dashboard**: "SIF Logs Overview"
- Log count over time (line chart)
- Error count by service (pie chart)
- Log level distribution (bar chart)
- Recent errors (saved search)

### Dashboard Filters

**Apply Filter to Dashboard**:
1. Open dashboard
2. Add filter or search query
3. All visualizations update automatically

**Time Range**:
- Set time range in top right
- Applies to all visualizations

## Common Use Cases

### Use Case 1: Debugging with Trace ID

**Scenario**: User reports an error, you have trace ID from Jaeger

**Steps**:
1. Go to Discover
2. Search: `trace_id:"<trace-id>"`
3. Sort by timestamp (ascending)
4. Expand each log to see details
5. Look for ERROR level logs
6. Check stack traces and error messages

**Example Finding**: NullPointerException in queue-service when processing message

### Use Case 2: Monitoring Service Health

**Scenario**: Check if a service is logging errors

**Steps**:
1. Go to Discover
2. Search: `service:"queue-service" AND level:"ERROR"`
3. Set time range: Last 1 hour
4. Check log count
5. Analyze error patterns

**Example Finding**: 50 errors in last hour, all related to database connection

### Use Case 3: Investigating Performance Issues

**Scenario**: Find slow operations

**Steps**:
1. Search: `message:*slow* OR message:*timeout*`
2. Add filter: service
3. Set time range to match performance issue
4. Analyze log messages
5. Correlate with traces in Jaeger

**Example Finding**: Database queries timing out during peak hours

### Use Case 4: Tracking Business Transaction

**Scenario**: Track a subscription creation from start to finish

**Steps**:
1. Get correlation ID from API response
2. Search: `correlation_id:"<correlation-id>"`
3. Sort by timestamp
4. Follow log sequence through services
5. Verify each step completed successfully

**Example Finding**: Subscription created successfully, processed through 4 services

### Use Case 5: Security Audit

**Scenario**: Find authentication failures

**Steps**:
1. Search: `message:*authentication* AND level:"ERROR"`
2. Or: `message:*unauthorized* OR message:*forbidden*`
3. Set time range: Last 7 days
4. Export results for audit

**Example Finding**: 10 failed authentication attempts from specific IP

### Use Case 6: Capacity Planning

**Scenario**: Analyze log volume trends

**Steps**:
1. Create visualization: Log count over time
2. Set time range: Last 30 days
3. Group by service
4. Identify growth trends
5. Plan storage capacity

**Example Finding**: Log volume growing 20% per month, need storage expansion

## Tips and Best Practices

### Search Tips

1. **Start Broad, Then Narrow**: Begin with service or time range, add filters progressively
2. **Use Saved Searches**: Save common queries for quick access
3. **Use Filters Over Queries**: Filters are easier to manage and combine
4. **Check Time Range**: Always verify time range matches your investigation period
5. **Use Wildcards Carefully**: Wildcards can slow down searches on large datasets

### Performance Tips

1. **Limit Time Range**: Shorter time ranges search faster
2. **Use Specific Fields**: Field-specific searches are faster than full-text
3. **Avoid Leading Wildcards**: `*text` is slower than `text*`
4. **Use Index Patterns**: Specific patterns (e.g., `sif-logs-2024.01.*`) are faster than `sif-logs-*`
5. **Refresh Interval**: Disable auto-refresh when not needed

### Analysis Tips

1. **Sort by Timestamp**: Understand sequence of events
2. **Expand Log Entries**: View all fields for context
3. **Use Surrounding Documents**: Click arrows to see logs before/after
4. **Correlate with Traces**: Use trace_id to link logs with Jaeger traces
5. **Look for Patterns**: Analyze multiple logs, not just one

### Collaboration Tips

1. **Share Saved Searches**: Share common queries with team
2. **Create Team Dashboards**: Build dashboards for common monitoring needs
3. **Export Data**: Export search results as CSV for offline analysis
4. **Document Queries**: Add descriptions to saved searches
5. **Use Consistent Naming**: Name saved searches and dashboards clearly

### Troubleshooting Tips

1. **No Results**: Check time range, verify index pattern, check filters
2. **Slow Searches**: Reduce time range, use more specific queries
3. **Missing Fields**: Refresh index pattern field list
4. **Incorrect Time**: Check timezone settings in Kibana
5. **Index Not Found**: Verify logs are being sent to Elasticsearch

## Advanced Features

### Dev Tools Console

**Use Case**: Execute Elasticsearch queries directly

**Access**: **☰** menu → **Dev Tools**

**Example Query**:
```json
GET sif-logs-*/_search
{
  "query": {
    "bool": {
      "must": [
        { "match": { "service": "queue-service" } },
        { "match": { "level": "ERROR" } }
      ]
    }
  },
  "size": 100,
  "sort": [
    { "timestamp": "desc" }
  ]
}
```

### Aggregations

**Use Case**: Statistical analysis of logs

**Example**: Count errors by service
```json
GET sif-logs-*/_search
{
  "size": 0,
  "query": {
    "match": { "level": "ERROR" }
  },
  "aggs": {
    "errors_by_service": {
      "terms": {
        "field": "service.keyword",
        "size": 10
      }
    }
  }
}
```

### Alerts (Watcher)

**Use Case**: Get notified of specific log patterns

**Example**: Alert on high error rate
1. Go to **Stack Management** → **Watcher**
2. Create new watch
3. Define condition (e.g., >100 errors in 5 minutes)
4. Configure action (email, Slack, webhook)

### Machine Learning

**Use Case**: Detect anomalies in log patterns

**Example**: Detect unusual error rates
1. Go to **Machine Learning** → **Anomaly Detection**
2. Create job on `sif-logs-*`
3. Detect anomalies in log count by service
4. View anomalies in dashboard

## Integration with Other Tools

### Jaeger Integration

**Use Case**: View trace details from logs

**Steps**:
1. Copy trace_id from log entry
2. Open Jaeger: `http://localhost:16686/trace/<trace-id>`
3. View trace timeline

**Benefit**: See full request flow alongside detailed logs

### Grafana Integration

**Use Case**: Correlate logs with metrics

**Steps**:
1. Note timestamp from log entry
2. Open Grafana dashboard
3. Set time range to match log timestamp
4. Analyze metrics at that time

**Benefit**: Understand system state when log was generated

### Alert Integration

**Use Case**: Link from alert to relevant logs

**Alert Annotation**:
```yaml
annotations:
  kibana_url: "http://kibana:5601/app/discover#/?_a=(query:(query_string:(query:'service:{{ $labels.service }} AND level:ERROR')))"
```

**Benefit**: Quick access to logs when alert fires

## Quick Reference

### Common Queries

```
# All errors
level:"ERROR"

# Errors in specific service
service:"queue-service" AND level:"ERROR"

# Logs by trace ID
trace_id:"4bf92f3577b34da6a3ce929d0e0e4736"

# Logs by correlation ID
correlation_id:"550e8400-e29b-41d4-a716-446655440000"

# Database errors
logger:*database* AND level:"ERROR"

# Slow operations
message:*slow* OR message:*timeout*

# Authentication failures
message:*authentication* AND level:"ERROR"

# Specific exception
message:*NullPointerException*

# Logs without trace ID
NOT trace_id:*

# Multiple services
service:("queue-service" OR "subscription-service")
```

### Keyboard Shortcuts

- `Ctrl+/` / `Cmd+/`: Focus search bar
- `Ctrl+Enter` / `Cmd+Enter`: Execute search
- `Esc`: Clear search
- `↑` / `↓`: Navigate log entries
- `Enter`: Expand/collapse log entry

### Time Range Shortcuts

- `t`: Open time picker
- `r`: Refresh
- `Ctrl+Shift+D` / `Cmd+Shift+D`: Toggle dark mode

## Additional Resources

- [Kibana Documentation](https://www.elastic.co/guide/en/kibana/current/index.html)
- [KQL Syntax](https://www.elastic.co/guide/en/kibana/current/kuery-query.html)
- [Elasticsearch Query DSL](https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl.html)
- [Configuration Guide](./configuration-guide.md)
- [Troubleshooting Runbooks](./troubleshooting-runbooks.md)
- [Jaeger Usage Guide](./jaeger-usage-guide.md)
