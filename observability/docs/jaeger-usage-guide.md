# Jaeger Usage Guide

## Table of Contents

1. [Overview](#overview)
2. [Accessing Jaeger UI](#accessing-jaeger-ui)
3. [Searching for Traces](#searching-for-traces)
4. [Analyzing Trace Timelines](#analyzing-trace-timelines)
5. [Advanced Search Techniques](#advanced-search-techniques)
6. [Common Use Cases](#common-use-cases)
7. [Tips and Best Practices](#tips-and-best-practices)

## Overview

Jaeger is a distributed tracing system that helps you monitor and troubleshoot transactions in complex distributed systems. In the SIF Unity Broker platform, Jaeger captures traces across all 9 microservices, allowing you to:

- Track requests end-to-end across services
- Identify performance bottlenecks
- Debug errors and failures
- Understand service dependencies
- Analyze request flow patterns

### Key Concepts

- **Trace**: A complete journey of a request through the system
- **Span**: A single unit of work within a trace (e.g., HTTP request, database query)
- **Trace ID**: Unique identifier for a trace (32 hexadecimal characters)
- **Span ID**: Unique identifier for a span (16 hexadecimal characters)
- **Parent Span**: The span that initiated a child span
- **Tags**: Key-value pairs that provide metadata about a span
- **Logs**: Timestamped events within a span

## Accessing Jaeger UI

### Local Development

**URL**: http://localhost:16686

### Production/Staging

**URL**: Provided by your infrastructure team (e.g., https://jaeger.your-domain.com)

### UI Overview

The Jaeger UI consists of several main sections:

1. **Search Page**: Find traces by service, operation, tags, and time range
2. **Trace Detail Page**: View detailed timeline and spans for a specific trace
3. **Compare Page**: Compare multiple traces side-by-side
4. **System Architecture**: Visualize service dependencies
5. **Monitor**: View service metrics and health

## Searching for Traces

### Basic Search

#### Search by Service and Operation

1. Open Jaeger UI
2. Select **Service** from dropdown (e.g., "queue-service")
3. Select **Operation** from dropdown (e.g., "GET /api/queues")
4. Set **Lookback** time range (e.g., "Last Hour")
5. Click **Find Traces**

**Screenshot Description**: Service dropdown showing all 9 SIF services, operation dropdown showing HTTP endpoints

#### Search Results

Results show:
- **Trace ID**: Click to view detailed trace
- **Service & Operation**: Primary service and operation
- **Duration**: Total trace duration
- **Spans**: Number of spans in the trace
- **Timestamp**: When the trace started

### Search by Trace ID

**Use Case**: You have a trace ID from logs and want to see the full trace

**Steps**:
1. Copy trace ID from logs (e.g., `4bf92f3577b34da6a3ce929d0e0e4736`)
2. Open Jaeger UI
3. Paste trace ID in the search box at the top
4. Press Enter

**Example Log Entry**:
```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "message": "Processing queue message"
}
```

**Direct URL**:
```
http://localhost:16686/trace/4bf92f3577b34da6a3ce929d0e0e4736
```

### Search by Correlation ID

**Use Case**: Track a request using the business correlation ID

**Steps**:
1. Select **Service** (e.g., "gateway")
2. Click **Tags** section to expand
3. Add tag: `correlation.id` = `550e8400-e29b-41d4-a716-446655440000`
4. Click **Find Traces**

**Screenshot Description**: Tags section showing correlation.id tag input

**Alternative**: Use the search box
```
correlation.id=550e8400-e29b-41d4-a716-446655440000
```

### Search by Service and Operation

**Use Case**: Find all traces for a specific endpoint

**Steps**:
1. Select **Service** (e.g., "queue-service")
2. Select **Operation** (e.g., "POST /api/queues")
3. Set time range
4. Click **Find Traces**

**Common Operations**:
- Gateway: `GET /api/*`, `POST /api/*`
- Queue Service: `POST /api/queues`, `GET /api/queues/{id}`
- Subscription Service: `POST /api/subscriptions`, `GET /api/subscriptions`
- Environment Service: `GET /api/environments`, `POST /api/environments/environment`

### Advanced Filters

#### Filter by Duration

**Use Case**: Find slow requests

**Steps**:
1. Select service and operation
2. Set **Min Duration** (e.g., "5s" for requests slower than 5 seconds)
3. Set **Max Duration** (optional)
4. Click **Find Traces**

**Screenshot Description**: Min/Max Duration inputs showing "5s" threshold

#### Filter by Tags

**Use Case**: Find traces with specific attributes

**Common Tags**:
- `http.method`: HTTP method (GET, POST, PUT, DELETE)
- `http.status_code`: HTTP status code (200, 404, 500)
- `error`: true/false (find failed traces)
- `queue.name`: Queue name (e.g., "immediate-requests")
- `message.type`: SIF message type (e.g., "StudentPersonal")
- `zone.id`: SIF zone ID
- `context.id`: SIF context ID

**Example**: Find all failed requests
```
Tags: error=true
```

**Example**: Find all requests to a specific queue
```
Tags: queue.name=immediate-requests
```

#### Filter by Number of Spans

**Use Case**: Find complex traces with many operations

**Steps**:
1. Select service
2. Set **Min Spans** (e.g., "10")
3. Click **Find Traces**

#### Limit Results

**Use Case**: Control number of results returned

**Steps**:
1. Set **Limit Results** (default: 20, max: 1500)
2. Click **Find Traces**

**Note**: Higher limits may slow down the UI

## Analyzing Trace Timelines

### Trace Detail View

Click on any trace in search results to open the detailed view.

### Timeline Components

#### Trace Header

Shows:
- **Trace ID**: Unique identifier
- **Duration**: Total trace duration
- **Services**: Number of services involved
- **Depth**: Maximum span depth
- **Total Spans**: Number of spans in trace
- **Start Time**: When trace began

#### Span List

Each span shows:
- **Service Name**: Which service executed the span
- **Operation Name**: What operation was performed
- **Duration**: How long the span took
- **Timeline Bar**: Visual representation of span timing

**Screenshot Description**: Trace timeline showing multiple services (gateway → queue-service → database) with nested spans

### Understanding Span Relationships

#### Parent-Child Relationships

- **Indentation**: Child spans are indented under parent spans
- **Timeline**: Child spans are contained within parent span duration
- **Arrows**: Click arrows to expand/collapse child spans

**Example Hierarchy**:
```
gateway: GET /api/queues (100ms)
  ├─ queue-service: GET /api/queues (80ms)
  │   ├─ database: SELECT * FROM queues (30ms)
  │   └─ redis: GET queue:cache (5ms)
  └─ tracing: span export (5ms)
```

#### Span Types

Identified by color and icon:

- **Server Span** (blue): Incoming HTTP request
- **Client Span** (green): Outgoing HTTP request
- **Internal Span** (gray): Internal operation
- **Database Span** (purple): Database query
- **Cache Span** (orange): Cache operation

### Analyzing Span Details

Click on any span to view details:

#### Tags

Key-value pairs providing metadata:

**HTTP Spans**:
- `http.method`: GET, POST, PUT, DELETE
- `http.url`: Full request URL
- `http.status_code`: Response status code
- `http.target`: Request path

**Database Spans**:
- `db.system`: Database type (postgresql)
- `db.statement`: SQL query
- `db.connection_string`: Database connection
- `db.user`: Database user

**Cache Spans**:
- `cache.name`: Cache name (e.g., "subscription-cache")
- `cache.operation`: Operation (get, set, delete)
- `cache.hit`: true/false

**Business Spans**:
- `queue.name`: Queue name
- `message.id`: Message ID
- `message.type`: SIF message type
- `zone.id`: SIF zone
- `context.id`: SIF context

#### Logs

Timestamped events within a span:

**Example**:
```
10:30:45.123 - Message received
10:30:45.150 - Validation passed
10:30:45.200 - Message queued
```

#### Process

Information about the service that created the span:
- Service name
- Service version
- Hostname
- IP address

### Identifying Performance Issues

#### Long Spans

**Indicator**: Span duration is significantly longer than others

**Actions**:
1. Check span tags for operation details
2. Look for database queries (check `db.statement`)
3. Look for external API calls
4. Check for N+1 query patterns (many sequential database spans)

**Example**: Database query taking 5 seconds
```
database: SELECT * FROM large_table WHERE ... (5000ms)
Tags:
  db.statement: SELECT * FROM large_table WHERE id IN (...)
  db.rows_affected: 10000
```

**Solution**: Add database index, optimize query, or implement pagination

#### Gaps Between Spans

**Indicator**: Large time gaps between child spans

**Possible Causes**:
- Thread pool exhaustion (waiting for available thread)
- CPU contention
- Garbage collection pauses
- Network latency

**Actions**:
1. Check infrastructure metrics (CPU, memory)
2. Check thread pool configuration
3. Check GC metrics

#### Sequential Operations

**Indicator**: Many spans executing sequentially instead of in parallel

**Example**: N+1 Query Pattern
```
queue-service: process_messages (1000ms)
  ├─ database: SELECT * FROM messages WHERE queue_id=1 (10ms)
  ├─ database: SELECT * FROM message_details WHERE message_id=1 (10ms)
  ├─ database: SELECT * FROM message_details WHERE message_id=2 (10ms)
  ├─ database: SELECT * FROM message_details WHERE message_id=3 (10ms)
  ... (100 more queries)
```

**Solution**: Use batch queries or JOIN operations

#### Error Spans

**Indicator**: Span has error tag or exception logs

**Identification**:
- Red color in timeline
- `error=true` tag
- Exception logs in span

**Actions**:
1. Check span logs for exception details
2. Check error message and stack trace
3. Correlate with error logs in Kibana

### Cross-Service Analysis

#### Service Dependencies

**View**: System Architecture page

Shows:
- Which services call which services
- Request rates between services
- Error rates between services

**Screenshot Description**: Dependency graph showing gateway → connectors → core services → database

#### Request Flow

**Trace Timeline**: Shows the path of a request through services

**Example Flow**:
```
1. gateway (10ms)
2. ├─ requests-connector (50ms)
3.    ├─ queue-service (30ms)
4.    │  └─ database (20ms)
5.    └─ subscription-service (15ms)
6.       └─ redis (5ms)
```

**Analysis**:
- Total duration: 60ms (gateway start to end)
- Most time spent in queue-service (30ms)
- Database query is the bottleneck (20ms of 30ms)

## Advanced Search Techniques

### Combining Multiple Filters

**Use Case**: Find slow failed requests to a specific endpoint

**Filters**:
- Service: queue-service
- Operation: POST /api/queues
- Tags: error=true
- Min Duration: 1s
- Lookback: Last 24 Hours

### Using Regular Expressions

**Use Case**: Search for operations matching a pattern

**Example**: Find all GET operations
```
Operation: GET.*
```

**Example**: Find all queue operations
```
Operation: .*queue.*
```

### Comparing Traces

**Use Case**: Compare a slow trace with a fast trace

**Steps**:
1. Find two traces (one slow, one fast)
2. Click checkboxes next to both traces
3. Click **Compare** button
4. View side-by-side comparison

**Screenshot Description**: Compare view showing two traces with highlighted differences in span durations

### Exporting Traces

**Use Case**: Share trace with team or save for analysis

**Steps**:
1. Open trace detail view
2. Click **JSON** button in top right
3. Copy JSON or download file

**Use Cases**:
- Share with team for debugging
- Save for post-incident analysis
- Import into analysis tools

## Common Use Cases

### Use Case 1: Debugging Slow Request

**Scenario**: User reports slow response time

**Steps**:
1. Get correlation ID or trace ID from user/logs
2. Search by correlation ID in Jaeger
3. Open trace detail view
4. Identify longest span(s)
5. Check span tags for operation details
6. Correlate with logs in Kibana using trace ID

**Example Finding**: Database query taking 5 seconds due to missing index

### Use Case 2: Investigating Error

**Scenario**: Error alert triggered

**Steps**:
1. Search by service and operation
2. Add filter: `error=true`
3. Set time range to match alert
4. Open failing trace
5. Find span with error tag
6. Check span logs for exception details
7. Check related logs in Kibana

**Example Finding**: External service timeout causing cascading failures

### Use Case 3: Understanding Request Flow

**Scenario**: Need to understand how a feature works

**Steps**:
1. Trigger the feature (e.g., create subscription)
2. Get trace ID from response headers or logs
3. Open trace in Jaeger
4. Analyze span hierarchy
5. Document service interactions

**Example Finding**: Subscription creation involves 5 services and 3 database queries

### Use Case 4: Performance Optimization

**Scenario**: Optimize endpoint performance

**Steps**:
1. Search for endpoint traces
2. Set Min Duration to find slow requests
3. Analyze multiple slow traces
4. Identify common bottlenecks
5. Prioritize optimization efforts

**Example Finding**: 80% of time spent in database queries, need caching

### Use Case 5: Capacity Planning

**Scenario**: Plan for increased traffic

**Steps**:
1. Search for high-traffic endpoints
2. Analyze trace patterns
3. Identify resource-intensive operations
4. Calculate resource requirements
5. Plan scaling strategy

**Example Finding**: Each request requires 3 database queries, need connection pool scaling

## Tips and Best Practices

### Search Tips

1. **Start Broad, Then Narrow**: Begin with service-level search, then add filters
2. **Use Time Ranges Wisely**: Shorter ranges load faster
3. **Limit Results**: Use reasonable limits (20-100) for faster loading
4. **Save Common Searches**: Bookmark URLs for frequently used searches
5. **Use Tags Effectively**: Tags are powerful for filtering specific scenarios

### Analysis Tips

1. **Look for Patterns**: Analyze multiple traces, not just one
2. **Check Parent Spans First**: Start with top-level spans, drill down as needed
3. **Compare Fast and Slow**: Compare successful fast traces with slow ones
4. **Correlate with Logs**: Use trace ID to find related logs in Kibana
5. **Check Error Spans**: Always investigate spans with error tags

### Performance Tips

1. **Use Sampling**: In production, use appropriate sampling rates (1-10%)
2. **Limit Trace Retention**: Configure retention based on storage capacity
3. **Archive Important Traces**: Export and save critical traces for analysis
4. **Monitor Jaeger Health**: Ensure Jaeger backend is healthy and responsive

### Collaboration Tips

1. **Share Trace URLs**: Send direct links to specific traces
2. **Export JSON**: Share trace JSON for offline analysis
3. **Document Findings**: Add comments/notes about important traces
4. **Create Runbooks**: Document common trace patterns for team reference

### Troubleshooting Tips

1. **Trace Not Found**: Check sampling rate, verify tracing is enabled
2. **Incomplete Traces**: Check for service connectivity issues
3. **Missing Spans**: Verify all services are instrumented correctly
4. **Slow UI**: Reduce result limit, use shorter time ranges

## Integration with Other Tools

### Kibana Integration

**Use Case**: View logs for a specific trace

**Steps**:
1. Copy trace ID from Jaeger
2. Open Kibana
3. Search: `trace_id:"<trace-id>"`
4. View all logs for that trace

**Benefit**: See detailed logs alongside trace timeline

### Grafana Integration

**Use Case**: View metrics for time period of trace

**Steps**:
1. Note trace timestamp from Jaeger
2. Open Grafana dashboard
3. Set time range to match trace
4. Analyze metrics (CPU, memory, request rate)

**Benefit**: Correlate trace performance with system metrics

### Alert Integration

**Use Case**: Link from alert to relevant traces

**Alert Configuration**:
```yaml
annotations:
  jaeger_url: "http://jaeger:16686/search?service={{ $labels.service }}&start={{ $value }}"
```

**Benefit**: Quick access to traces when alert fires

## Additional Resources

- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [OpenTelemetry Tracing](https://opentelemetry.io/docs/concepts/signals/traces/)
- [Configuration Guide](./configuration-guide.md)
- [Troubleshooting Runbooks](./troubleshooting-runbooks.md)
- [Kibana Usage Guide](./kibana-usage-guide.md)

## Quick Reference

### Common Search Patterns

```
# Find slow requests
Min Duration: 5s

# Find failed requests
Tags: error=true

# Find requests to specific queue
Tags: queue.name=immediate-requests

# Find requests by correlation ID
Tags: correlation.id=<correlation-id>

# Find requests by HTTP status
Tags: http.status_code=500

# Find database queries
Operation: SELECT*
```

### Keyboard Shortcuts

- `Ctrl+F` / `Cmd+F`: Search within page
- `Esc`: Close span details
- `←` / `→`: Navigate between traces
- `↑` / `↓`: Navigate between spans

### URL Parameters

```
# Direct trace link
/trace/<trace-id>

# Search with filters
/search?service=queue-service&operation=GET%20/api/queues&start=<timestamp>&end=<timestamp>

# Search with tags
/search?service=queue-service&tags={"error":"true"}
```
