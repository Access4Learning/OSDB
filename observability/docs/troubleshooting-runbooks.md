# Observability Platform Troubleshooting Runbooks

## Table of Contents

1. [High Latency Investigation](#high-latency-investigation)
2. [Error Spike Investigation](#error-spike-investigation)
3. [Service Down Investigation](#service-down-investigation)
4. [Trace Not Appearing in Jaeger](#trace-not-appearing-in-jaeger)
5. [Logs Not Appearing in Kibana](#logs-not-appearing-in-kibana)

## Overview

This document provides step-by-step runbooks for investigating common observability and operational issues in the SIF Unity Broker platform. Each runbook follows a structured approach: Symptoms → Detection → Investigation → Resolution.

---

## High Latency Investigation

### Symptoms

- Users reporting slow response times
- Alert: "HighLatency" triggered (p99 latency > 5 seconds)
- Dashboard shows increased latency percentiles

### Detection

**Grafana Dashboard**: Request Flow Dashboard

1. Open Grafana: http://localhost:3000
2. Navigate to "Request Flow" dashboard
3. Check "Latency Percentiles by Service" panel
4. Identify which service(s) show elevated latency

**Prometheus Query**:
```promql
# P99 latency by service
histogram_quantile(0.99, 
  sum(rate(http_server_requests_seconds_bucket[5m])) by (service, le)
)
```

### Investigation Steps

#### Step 1: Identify Affected Services

**Grafana**:
- Check "Latency Percentiles by Service" panel
- Note which services have p99 > 5s

**Prometheus**:
```promql
# Services with high latency
topk(5, 
  histogram_quantile(0.99, 
    sum(rate(http_server_requests_seconds_bucket[5m])) by (service, le)
  )
)
```

#### Step 2: Identify Slow Endpoints

**Grafana**:
- Check "Top Slowest Endpoints" table
- Note endpoint URIs and their latencies

**Prometheus**:
```promql
# Slowest endpoints
topk(10, 
  histogram_quantile(0.99, 
    sum(rate(http_server_requests_seconds_bucket[5m])) by (uri, le)
  )
)
```

#### Step 3: Analyze Traces for Slow Requests

**Jaeger**:
1. Open Jaeger UI: http://localhost:16686
2. Select the affected service
3. Set "Min Duration" to 5s (or threshold from alert)
4. Click "Find Traces"
5. Select a slow trace to analyze

**What to Look For**:
- Long database query spans (check `db.statement` attribute)
- Long external API call spans
- Long Redis operation spans
- Many sequential operations (N+1 query pattern)
- Large gaps between spans (thread pool exhaustion)

#### Step 4: Check Database Performance

**Prometheus Queries**:
```promql
# Database connection pool utilization
db_pool_active / db_pool_total

# Database query duration
histogram_quantile(0.99, 
  sum(rate(jdbc_query_seconds_bucket[5m])) by (query, le)
)
```

**Actions**:
- If pool utilization > 90%: Increase connection pool size
- If query duration is high: Check database indexes and query plans
- Check for long-running transactions in database

#### Step 5: Check External Dependencies

**Jaeger**:
- Look for spans with `span.kind=CLIENT`
- Check duration of external HTTP calls
- Note any timeout or error spans

**Prometheus**:
```promql
# External call duration
histogram_quantile(0.99, 
  sum(rate(http_client_requests_seconds_bucket[5m])) by (uri, le)
)
```

**Actions**:
- If external service is slow: Contact external service team
- If timeouts are occurring: Adjust timeout configuration
- Consider implementing circuit breaker pattern

#### Step 6: Check Resource Utilization

**Grafana**: Infrastructure Dashboard

**Prometheus Queries**:
```promql
# CPU usage
process_cpu_usage{service="affected-service"}

# Memory usage
jvm_memory_used_bytes{area="heap", service="affected-service"} / 
jvm_memory_max_bytes{area="heap", service="affected-service"}

# GC pause time
rate(jvm_gc_pause_seconds_sum{service="affected-service"}[5m])

# Thread count
jvm_threads_live{service="affected-service"}
```

**Actions**:
- If CPU > 80%: Scale horizontally or optimize code
- If memory > 85%: Increase heap size or investigate memory leaks
- If GC pause time > 10%: Tune GC settings or increase heap
- If thread count is maxed: Increase thread pool size

#### Step 7: Check for Traffic Spikes

**Prometheus**:
```promql
# Request rate
sum(rate(http_server_requests_seconds_count[5m])) by (service)
```

**Actions**:
- If traffic spike: Scale horizontally
- If unusual traffic pattern: Check for DDoS or bot activity
- If expected traffic: Ensure auto-scaling is configured

### Resolution Actions

#### Immediate Actions (Incident Response)

1. **Scale Affected Services**:
   ```bash
   # Kubernetes
   kubectl scale deployment affected-service --replicas=5
   
   # Docker Compose
   docker-compose up -d --scale affected-service=3
   ```

2. **Reduce Trace Sampling** (if tracing overhead is contributing):
   ```bash
   kubectl set env deployment/affected-service TRACING_SAMPLING_RATE=0.01
   ```

3. **Enable Circuit Breaker** (if external dependency is slow):
   - Configure circuit breaker for slow external service
   - Set appropriate timeout and fallback

4. **Increase Resource Limits**:
   ```yaml
   # Kubernetes
   resources:
     limits:
       cpu: "2000m"
       memory: "2Gi"
   ```

#### Long-Term Actions (Post-Incident)

1. **Optimize Slow Queries**:
   - Add database indexes
   - Optimize query logic
   - Implement caching

2. **Implement Caching**:
   - Cache frequently accessed data
   - Use Redis for distributed caching
   - Set appropriate TTLs

3. **Optimize Code**:
   - Fix N+1 query patterns
   - Implement batch operations
   - Use async processing where appropriate

4. **Adjust Configuration**:
   - Increase connection pool sizes
   - Tune thread pool sizes
   - Adjust timeout values

5. **Set Up Proactive Monitoring**:
   - Create alerts for latency thresholds
   - Monitor resource utilization trends
   - Set up capacity planning dashboards

### Verification

After implementing fixes:

1. **Check Latency Metrics**:
   ```promql
   histogram_quantile(0.99, 
     sum(rate(http_server_requests_seconds_bucket[5m])) by (service, le)
   )
   ```

2. **Verify in Jaeger**:
   - Check that trace durations are within acceptable range
   - Verify no long-running spans

3. **Monitor for 24 Hours**:
   - Ensure latency remains stable
   - Check for any regressions

---

## Error Spike Investigation

### Symptoms

- Alert: "HighErrorRate" triggered (error rate > 5%)
- Dashboard shows increased 4xx or 5xx responses
- Users reporting errors or failures

### Detection

**Grafana Dashboard**: Error Tracking Dashboard

1. Open Grafana: http://localhost:3000
2. Navigate to "Error Tracking" dashboard
3. Check "Error Rate by Service" panel
4. Check "Error Count by Type" pie chart

**Prometheus Query**:
```promql
# Error rate by service
sum(rate(http_server_requests_seconds_count{status=~"[45].."}[5m])) by (service) /
sum(rate(http_server_requests_seconds_count[5m])) by (service)
```

### Investigation Steps

#### Step 1: Identify Error Types

**Prometheus**:
```promql
# Errors by status code
sum(rate(http_server_requests_seconds_count{status=~"[45].."}[5m])) 
  by (service, status)

# Errors by exception type
sum(rate(http_server_requests_seconds_count{exception!="None"}[5m])) 
  by (service, exception)
```

**Classification**:
- **4xx errors**: Client errors (bad requests, authentication, not found)
- **5xx errors**: Server errors (internal errors, service unavailable)

#### Step 2: Analyze Error Logs

**Kibana**:
1. Open Kibana: http://localhost:5601
2. Go to "Discover"
3. Search: `level:"ERROR" AND service:"affected-service"`
4. Set time range to match error spike
5. Analyze error messages and stack traces

**Common Patterns to Look For**:
- Database connection errors
- External service timeouts
- Null pointer exceptions
- Authentication/authorization failures
- Resource exhaustion (out of memory, file descriptors)

#### Step 3: Find Failing Traces

**Jaeger**:
1. Open Jaeger UI: http://localhost:16686
2. Select affected service
3. Add tag filter: `error=true`
4. Click "Find Traces"
5. Analyze failing traces

**What to Look For**:
- Spans with `status.code=ERROR`
- Exception events in spans
- Failed database or external service calls
- Missing or invalid data in span attributes

#### Step 4: Check Dependencies

**Prometheus**:
```promql
# Database connection pool exhaustion
db_pool_active / db_pool_total > 0.9

# External service availability
up{job="external-service"} == 0
```

**Actions**:
- Check if database is available and responsive
- Check if external services are available
- Check if Redis is available
- Check network connectivity

#### Step 5: Check for Recent Changes

**Questions to Ask**:
- Was there a recent deployment?
- Were there configuration changes?
- Were there database schema changes?
- Were there external service changes?

**Actions**:
- Review recent commits and deployments
- Check deployment logs
- Compare current configuration with previous version

#### Step 6: Analyze Error Patterns

**Kibana Queries**:
```
# Group errors by message
level:"ERROR" | stats count() by message

# Find correlation with specific users/requests
level:"ERROR" AND correlation_id:*
```

**Patterns**:
- **All requests failing**: Service-wide issue (database down, configuration error)
- **Specific endpoints failing**: Endpoint-specific bug
- **Intermittent failures**: Race condition, resource exhaustion, external service flakiness
- **Specific users failing**: Data-specific issue, permission issue

### Resolution Actions

#### Immediate Actions (Incident Response)

1. **Rollback Recent Deployment** (if applicable):
   ```bash
   # Kubernetes
   kubectl rollout undo deployment/affected-service
   
   # Docker Compose
   docker-compose up -d affected-service:previous-tag
   ```

2. **Restart Affected Services** (if resource exhaustion):
   ```bash
   # Kubernetes
   kubectl rollout restart deployment/affected-service
   
   # Docker Compose
   docker-compose restart affected-service
   ```

3. **Increase Resource Limits** (if out of memory/connections):
   ```yaml
   # Increase database connection pool
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20  # Increase from 10
   ```

4. **Enable Circuit Breaker** (if external dependency is failing):
   - Configure circuit breaker to fail fast
   - Implement fallback behavior

5. **Disable Problematic Feature** (if specific feature is causing errors):
   - Use feature flags to disable problematic feature
   - Communicate with users about temporary unavailability

#### Long-Term Actions (Post-Incident)

1. **Fix Root Cause**:
   - Fix bugs identified in error logs
   - Add null checks and validation
   - Improve error handling

2. **Add Resilience Patterns**:
   - Implement retry logic with exponential backoff
   - Implement circuit breakers for external dependencies
   - Add timeouts to prevent hanging requests

3. **Improve Validation**:
   - Add input validation
   - Add schema validation
   - Return clear error messages

4. **Add Tests**:
   - Add unit tests for error scenarios
   - Add integration tests for failure cases
   - Add property-based tests for edge cases

5. **Improve Monitoring**:
   - Add alerts for specific error types
   - Add dashboards for error analysis
   - Add logging for debugging

### Verification

After implementing fixes:

1. **Check Error Rate**:
   ```promql
   sum(rate(http_server_requests_seconds_count{status=~"[45].."}[5m])) by (service) /
   sum(rate(http_server_requests_seconds_count[5m])) by (service)
   ```

2. **Verify in Kibana**:
   - Check that error logs have decreased
   - Verify no new error patterns

3. **Verify in Jaeger**:
   - Check that traces no longer show errors
   - Verify all spans complete successfully

4. **Monitor for 24 Hours**:
   - Ensure error rate remains below threshold
   - Check for any new error patterns

---

## Service Down Investigation

### Symptoms

- Alert: "ServiceDown" triggered (service unavailable > 1 minute)
- Dashboard shows service status as "DOWN"
- Health check endpoint returning errors
- Users unable to access service

### Detection

**Grafana Dashboard**: Service Health Dashboard

1. Open Grafana: http://localhost:3000
2. Navigate to "Service Health" dashboard
3. Check "Service Up/Down Status" panel
4. Identify which service(s) are down

**Prometheus Query**:
```promql
# Service availability
up{job="sif-unity-broker"} == 0
```

### Investigation Steps

#### Step 1: Verify Service Status

**Kubernetes**:
```bash
# Check pod status
kubectl get pods -l app=affected-service

# Check pod events
kubectl describe pod affected-service-xxx

# Check pod logs
kubectl logs affected-service-xxx --tail=100
```

**Docker Compose**:
```bash
# Check container status
docker-compose ps affected-service

# Check container logs
docker-compose logs --tail=100 affected-service
```

#### Step 2: Check Service Logs

**Look for**:
- Startup errors
- Configuration errors
- Database connection errors
- Out of memory errors
- Port binding errors

**Common Error Patterns**:
```
# Configuration error
Failed to bind properties under 'spring.datasource.url'

# Database connection error
Unable to acquire JDBC Connection

# Port already in use
Port 8080 is already in use

# Out of memory
java.lang.OutOfMemoryError: Java heap space
```

#### Step 3: Check Health Endpoint

**Direct Check**:
```bash
# Check health endpoint
curl http://localhost:8080/actuator/health

# Check with details
curl http://localhost:8080/actuator/health | jq
```

**Expected Response** (healthy):
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "diskSpace": {"status": "UP"}
  }
}
```

**Unhealthy Response**:
```json
{
  "status": "DOWN",
  "components": {
    "db": {
      "status": "DOWN",
      "details": {
        "error": "Unable to acquire JDBC Connection"
      }
    }
  }
}
```

#### Step 4: Check Dependencies

**Database**:
```bash
# Check database connectivity
psql -h database-host -U username -d database-name -c "SELECT 1"

# Check database status
kubectl get pods -l app=postgresql
```

**Redis**:
```bash
# Check Redis connectivity
redis-cli -h redis-host ping

# Check Redis status
kubectl get pods -l app=redis
```

**External Services**:
```bash
# Check external service availability
curl http://external-service/health
```

#### Step 5: Check Resource Availability

**Kubernetes**:
```bash
# Check node resources
kubectl top nodes

# Check pod resources
kubectl top pods

# Check resource limits
kubectl describe pod affected-service-xxx | grep -A 5 "Limits"
```

**Prometheus**:
```promql
# Memory usage
container_memory_usage_bytes{pod=~"affected-service.*"} / 
container_spec_memory_limit_bytes{pod=~"affected-service.*"}

# CPU usage
rate(container_cpu_usage_seconds_total{pod=~"affected-service.*"}[5m])
```

#### Step 6: Check Recent Changes

**Questions**:
- Was there a recent deployment?
- Were there configuration changes?
- Were there infrastructure changes?
- Were there dependency updates?

**Actions**:
```bash
# Check deployment history
kubectl rollout history deployment/affected-service

# Check recent events
kubectl get events --sort-by='.lastTimestamp' | grep affected-service
```

### Resolution Actions

#### Immediate Actions (Incident Response)

1. **Restart Service**:
   ```bash
   # Kubernetes
   kubectl rollout restart deployment/affected-service
   
   # Docker Compose
   docker-compose restart affected-service
   ```

2. **Rollback to Previous Version**:
   ```bash
   # Kubernetes
   kubectl rollout undo deployment/affected-service
   
   # Check rollout status
   kubectl rollout status deployment/affected-service
   ```

3. **Scale Up Replicas** (if some instances are healthy):
   ```bash
   kubectl scale deployment affected-service --replicas=5
   ```

4. **Fix Configuration Issues**:
   ```bash
   # Update ConfigMap
   kubectl edit configmap affected-service-config
   
   # Restart to pick up changes
   kubectl rollout restart deployment/affected-service
   ```

5. **Increase Resource Limits**:
   ```yaml
   # Update deployment
   resources:
     limits:
       memory: "2Gi"  # Increase from 1Gi
       cpu: "2000m"   # Increase from 1000m
   ```

6. **Fix Database Connection**:
   ```bash
   # Check database is running
   kubectl get pods -l app=postgresql
   
   # Restart database if needed
   kubectl rollout restart statefulset/postgresql
   ```

#### Long-Term Actions (Post-Incident)

1. **Improve Health Checks**:
   - Add liveness probe
   - Add readiness probe
   - Add startup probe for slow-starting services

2. **Add Resource Monitoring**:
   - Set up alerts for high memory usage
   - Set up alerts for high CPU usage
   - Monitor resource trends

3. **Improve Resilience**:
   - Add retry logic for database connections
   - Implement graceful degradation
   - Add circuit breakers for dependencies

4. **Improve Deployment Process**:
   - Add smoke tests after deployment
   - Implement blue-green deployment
   - Implement canary deployment

5. **Document Runbooks**:
   - Document common failure scenarios
   - Document recovery procedures
   - Document escalation paths

### Verification

After service recovery:

1. **Check Service Status**:
   ```promql
   up{job="sif-unity-broker", service="affected-service"} == 1
   ```

2. **Check Health Endpoint**:
   ```bash
   curl http://localhost:8080/actuator/health
   ```

3. **Verify Functionality**:
   - Test critical endpoints
   - Verify database connectivity
   - Verify external service connectivity

4. **Monitor for 1 Hour**:
   - Ensure service remains stable
   - Check for any errors in logs
   - Verify metrics are normal

---

## Trace Not Appearing in Jaeger

### Symptoms

- Expected traces not visible in Jaeger UI
- Trace ID from logs not found in Jaeger
- Service shows no traces despite handling requests

### Investigation Steps

#### Step 1: Verify Tracing is Enabled

**Check Configuration**:
```bash
# Check if tracing is enabled
curl http://localhost:8080/actuator/configprops | jq '.observability.tracing.enabled'

# Check sampling rate
curl http://localhost:8080/actuator/configprops | jq '.observability.tracing.sampling.probability'
```

**Expected Values**:
- `tracing.enabled`: `true`
- `sampling.probability`: > 0 (e.g., 0.1 for 10% sampling)

#### Step 2: Check Jaeger Connectivity

**Test Jaeger Endpoint**:
```bash
# Check Jaeger collector is reachable
curl http://jaeger-endpoint:14250

# Check Jaeger UI is accessible
curl http://localhost:16686
```

**Check Service Logs**:
```bash
# Look for Jaeger connection errors
kubectl logs affected-service-xxx | grep -i jaeger

# Look for span export errors
kubectl logs affected-service-xxx | grep -i "export"
```

#### Step 3: Verify Trace Generation

**Check Service Logs for Trace IDs**:
```bash
# Search for trace_id in logs
kubectl logs affected-service-xxx | grep trace_id
```

**Expected Log Entry**:
```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7",
  "message": "Processing request"
}
```

If trace_id is present in logs but not in Jaeger, the issue is with export.

#### Step 4: Check Sampling Configuration

**Verify Sampling Rate**:
```yaml
# Check current sampling rate
observability:
  tracing:
    sampling:
      probability: 1.0  # Should be > 0
```

**Note**: If sampling rate is 0.1 (10%), only 1 in 10 traces will appear in Jaeger. This is expected behavior.

#### Step 5: Check Jaeger Backend

**Verify Jaeger Components**:
```bash
# Check Jaeger pods
kubectl get pods -n observability -l app=jaeger

# Check Jaeger collector logs
kubectl logs -n observability jaeger-collector-xxx
```

**Check Jaeger Storage**:
```bash
# Check Elasticsearch (if using Elasticsearch backend)
curl http://elasticsearch:9200/_cat/indices | grep jaeger

# Check Cassandra (if using Cassandra backend)
kubectl exec -it cassandra-0 -- cqlsh -e "SELECT COUNT(*) FROM jaeger_v1_dc1.traces;"
```

### Resolution Actions

#### Issue: Tracing is Disabled

**Solution**:
```bash
# Enable tracing
kubectl set env deployment/affected-service TRACING_ENABLED=true

# Verify
kubectl rollout status deployment/affected-service
```

#### Issue: Sampling Rate is 0

**Solution**:
```bash
# Set sampling rate to 100% for testing
kubectl set env deployment/affected-service TRACING_SAMPLING_RATE=1.0

# Or set to 10% for production
kubectl set env deployment/affected-service TRACING_SAMPLING_RATE=0.1
```

#### Issue: Jaeger Endpoint is Incorrect

**Solution**:
```bash
# Update Jaeger endpoint
kubectl set env deployment/affected-service \
  JAEGER_ENDPOINT=http://jaeger-collector.observability.svc.cluster.local:14250

# Restart service
kubectl rollout restart deployment/affected-service
```

#### Issue: Jaeger is Down

**Solution**:
```bash
# Check Jaeger status
kubectl get pods -n observability -l app=jaeger

# Restart Jaeger if needed
kubectl rollout restart deployment/jaeger-collector -n observability

# Service will automatically retry exporting spans
```

#### Issue: Span Queue is Full

**Solution**:
```bash
# Increase queue size
kubectl set env deployment/affected-service JAEGER_MAX_QUEUE_SIZE=4096

# Increase export batch size
kubectl set env deployment/affected-service JAEGER_MAX_EXPORT_BATCH_SIZE=1024

# Restart service
kubectl rollout restart deployment/affected-service
```

### Verification

1. **Generate Test Trace**:
   ```bash
   # Make a request to generate a trace
   curl http://localhost:8080/api/test
   ```

2. **Check Logs for Trace ID**:
   ```bash
   kubectl logs affected-service-xxx --tail=10 | grep trace_id
   ```

3. **Search in Jaeger**:
   - Open Jaeger UI: http://localhost:16686
   - Select service
   - Click "Find Traces"
   - Verify trace appears

4. **Search by Trace ID**:
   - Copy trace_id from logs
   - Paste into Jaeger search box
   - Verify trace is found

---

## Logs Not Appearing in Kibana

### Symptoms

- Expected logs not visible in Kibana
- Log entries from service not appearing in Elasticsearch
- Kibana shows "No results found"

### Investigation Steps

#### Step 1: Verify Logging Configuration

**Check Logstash Configuration**:
```bash
# Check Logstash endpoint
curl http://localhost:8080/actuator/configprops | jq '.observability.logging.logstash'
```

**Expected Values**:
```json
{
  "host": "logstash",
  "port": 5000
}
```

#### Step 2: Check Logstash Connectivity

**Test Logstash Connection**:
```bash
# Test TCP connection to Logstash
telnet logstash-host 5000

# Or using nc
nc -zv logstash-host 5000
```

**Check Service Logs**:
```bash
# Look for Logstash connection errors
kubectl logs affected-service-xxx | grep -i logstash

# Look for log appender errors
kubectl logs affected-service-xxx | grep -i "appender"
```

#### Step 3: Verify Log Generation

**Check Console Logs**:
```bash
# Service should still log to console
kubectl logs affected-service-xxx --tail=20
```

**Expected Output** (JSON format):
```json
{"timestamp":"2024-01-15T10:30:45.123Z","level":"INFO","message":"Processing request"}
```

If logs appear in console but not Kibana, the issue is with Logstash connection.

#### Step 4: Check Logstash Pipeline

**Check Logstash Status**:
```bash
# Check Logstash pods
kubectl get pods -l app=logstash

# Check Logstash logs
kubectl logs logstash-xxx --tail=50
```

**Look for**:
- Connection errors from services
- Pipeline errors
- Elasticsearch connection errors

#### Step 5: Check Elasticsearch

**Verify Elasticsearch is Running**:
```bash
# Check Elasticsearch pods
kubectl get pods -l app=elasticsearch

# Check Elasticsearch health
curl http://elasticsearch:9200/_cluster/health
```

**Check Indices**:
```bash
# List indices
curl http://elasticsearch:9200/_cat/indices

# Look for sif-logs-* indices
curl http://elasticsearch:9200/_cat/indices | grep sif-logs
```

**Check Index Mapping**:
```bash
# Check if index exists
curl http://elasticsearch:9200/sif-logs-*/_mapping
```

#### Step 6: Check Kibana Index Pattern

**Verify Index Pattern**:
1. Open Kibana: http://localhost:5601
2. Go to "Stack Management" → "Index Patterns"
3. Check if `sif-logs-*` pattern exists
4. If not, create it

### Resolution Actions

#### Issue: Logstash is Not Reachable

**Solution**:
```bash
# Update Logstash endpoint
kubectl set env deployment/affected-service LOGSTASH_HOST=logstash.observability.svc.cluster.local
kubectl set env deployment/affected-service LOGSTASH_PORT=5000

# Restart service
kubectl rollout restart deployment/affected-service
```

#### Issue: Logstash is Down

**Solution**:
```bash
# Check Logstash status
kubectl get pods -l app=logstash

# Restart Logstash
kubectl rollout restart deployment/logstash

# Service will automatically retry sending logs
```

#### Issue: Elasticsearch is Down

**Solution**:
```bash
# Check Elasticsearch status
kubectl get pods -l app=elasticsearch

# Restart Elasticsearch
kubectl rollout restart statefulset/elasticsearch

# Wait for Elasticsearch to be ready
kubectl wait --for=condition=ready pod -l app=elasticsearch --timeout=300s
```

#### Issue: Logstash Pipeline Configuration Error

**Solution**:
```bash
# Check Logstash pipeline configuration
kubectl get configmap logstash-pipeline -o yaml

# Fix configuration and update
kubectl edit configmap logstash-pipeline

# Restart Logstash to pick up changes
kubectl rollout restart deployment/logstash
```

#### Issue: Kibana Index Pattern Missing

**Solution**:
1. Open Kibana: http://localhost:5601
2. Go to "Stack Management" → "Index Patterns"
3. Click "Create index pattern"
4. Enter pattern: `sif-logs-*`
5. Select timestamp field: `timestamp`
6. Click "Create index pattern"

#### Issue: Elasticsearch Disk Space Full

**Solution**:
```bash
# Check disk usage
kubectl exec -it elasticsearch-0 -- df -h

# Delete old indices
curl -X DELETE "http://elasticsearch:9200/sif-logs-*-$(date -d '30 days ago' +%Y.%m.%d)"

# Or increase persistent volume size
kubectl edit pvc elasticsearch-data
```

### Verification

1. **Generate Test Logs**:
   ```bash
   # Make requests to generate logs
   curl http://localhost:8080/api/test
   ```

2. **Check Logstash Received Logs**:
   ```bash
   # Check Logstash logs for incoming connections
   kubectl logs logstash-xxx --tail=20 | grep "connection"
   ```

3. **Check Elasticsearch Indices**:
   ```bash
   # Verify new documents are being indexed
   curl "http://elasticsearch:9200/sif-logs-*/_count"
   ```

4. **Search in Kibana**:
   - Open Kibana: http://localhost:5601
   - Go to "Discover"
   - Select `sif-logs-*` index pattern
   - Set time range to "Last 15 minutes"
   - Verify logs appear

5. **Search by Service**:
   ```
   service:"affected-service"
   ```

6. **Search by Trace ID**:
   ```
   trace_id:"4bf92f3577b34da6a3ce929d0e0e4736"
   ```

---

## Additional Resources

- [Configuration Guide](./configuration-guide.md)
- [Jaeger Usage Guide](./jaeger-usage-guide.md)
- [Kibana Usage Guide](./kibana-usage-guide.md)
- [Grafana Dashboard Guide](./grafana-dashboard-guide.md)
- [Deployment and Operations Guide](./deployment-operations-guide.md)

## Escalation

If issues persist after following these runbooks:

1. **Check System Status**: Verify all observability components are healthy
2. **Review Recent Changes**: Check for recent deployments or configuration changes
3. **Collect Diagnostics**: Gather logs, metrics, and traces for analysis
4. **Contact Support**: Escalate to platform team with diagnostic information

### Diagnostic Information to Collect

- Service logs (last 1000 lines)
- Jaeger traces (if available)
- Prometheus metrics (last 1 hour)
- Kibana logs (if available)
- Configuration files
- Recent deployment history
- Resource utilization metrics
