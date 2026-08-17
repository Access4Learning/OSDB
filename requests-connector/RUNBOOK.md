# Requests Connector Runbook

## Overview

This runbook provides operational procedures for managing and troubleshooting the Requests Connector service.

## Table of Contents

1. [Service Health Checks](#service-health-checks)
2. [Common Operations](#common-operations)
3. [Troubleshooting](#troubleshooting)
4. [Monitoring and Alerts](#monitoring-and-alerts)
5. [Performance Tuning](#performance-tuning)
6. [Disaster Recovery](#disaster-recovery)

---

## Service Health Checks

### Quick Health Check

```bash
curl http://localhost:8080/actuator/health
```

**Expected Response**:
```json
{
  "status": "UP",
  "components": {
    "redis": {
      "status": "UP"
    },
    "diskSpace": {
      "status": "UP"
    }
  }
}
```

### Detailed Health Check

```bash
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
```

### Check Metrics

```bash
# View all metrics
curl http://localhost:8080/actuator/metrics

# View specific metric
curl http://localhost:8080/actuator/metrics/requests.immediate.count
curl http://localhost:8080/actuator/metrics/provider.call.latency
```

---

## Common Operations

### 1. Restart Service

**Docker**:
```bash
docker restart requests-connector
```

**Kubernetes**:
```bash
kubectl rollout restart deployment/requests-connector -n sif-broker
```

**Verify Restart**:
```bash
# Check pod status
kubectl get pods -n sif-broker -l app=requests-connector

# Check logs
kubectl logs -n sif-broker -l app=requests-connector --tail=100
```

### 2. Scale Service

**Kubernetes**:
```bash
# Scale to 5 replicas
kubectl scale deployment requests-connector -n sif-broker --replicas=5

# Verify scaling
kubectl get pods -n sif-broker -l app=requests-connector
```

### 3. Update Configuration

**Kubernetes**:
```bash
# Edit ConfigMap
kubectl edit configmap requests-connector-config -n sif-broker

# Restart pods to apply changes
kubectl rollout restart deployment/requests-connector -n sif-broker
```

### 4. Clear Cache

The service uses Redis for caching. To clear the cache:

```bash
# Connect to Redis
kubectl exec -it redis-pod -n sif-broker -- redis-cli

# Clear all cache keys
FLUSHDB

# Or clear specific cache
KEYS provider-registry:*
DEL provider-registry:StudentPersonal:DEFAULT:null
```

### 5. View Logs

**Docker**:
```bash
docker logs requests-connector --tail=100 -f
```

**Kubernetes**:
```bash
# View logs from all pods
kubectl logs -n sif-broker -l app=requests-connector --tail=100 -f

# View logs from specific pod
kubectl logs -n sif-broker <pod-name> --tail=100 -f

# View logs with grep
kubectl logs -n sif-broker -l app=requests-connector | grep ERROR
```

---

## Troubleshooting

### Issue 1: Service Not Starting

**Symptoms**:
- Pods in CrashLoopBackOff state
- Health check failing
- Application logs show startup errors

**Diagnosis**:
```bash
# Check pod status
kubectl describe pod <pod-name> -n sif-broker

# Check logs
kubectl logs <pod-name> -n sif-broker

# Check events
kubectl get events -n sif-broker --sort-by='.lastTimestamp'
```

**Common Causes**:

1. **Redis Connection Failed**
   ```
   Solution: Verify Redis is running and accessible
   kubectl get pods -n sif-broker -l app=redis
   kubectl logs -n sif-broker redis-pod
   ```

2. **Configuration Error**
   ```
   Solution: Check ConfigMap values
   kubectl get configmap requests-connector-config -n sif-broker -o yaml
   ```

3. **Resource Constraints**
   ```
   Solution: Check resource usage
   kubectl top pods -n sif-broker
   kubectl describe node <node-name>
   ```

### Issue 2: High Latency

**Symptoms**:
- Slow response times
- Timeout errors
- High CPU/memory usage

**Diagnosis**:
```bash
# Check resource usage
kubectl top pods -n sif-broker -l app=requests-connector

# Check metrics
curl http://localhost:8080/actuator/metrics/provider.call.latency
curl http://localhost:8080/actuator/metrics/cache.hit.rate

# Check logs for slow requests
kubectl logs -n sif-broker -l app=requests-connector | grep "duration="
```

**Common Causes**:

1. **Provider Slow Response**
   ```
   Solution: Check provider service health and performance
   - Review provider call latency metrics
   - Check provider service logs
   - Consider increasing timeout values
   ```

2. **Cache Miss Rate High**
   ```
   Solution: Investigate cache configuration
   - Check Redis connectivity
   - Verify cache TTL settings
   - Review cache hit/miss metrics
   ```

3. **Resource Constraints**
   ```
   Solution: Scale up resources
   kubectl scale deployment requests-connector -n sif-broker --replicas=5
   # Or increase resource limits in deployment.yaml
   ```

### Issue 3: Provider Connection Failures

**Symptoms**:
- HTTP 503 Service Unavailable errors
- "Provider unavailable" in logs
- WebClientResponseException errors

**Diagnosis**:
```bash
# Check provider connectivity
kubectl exec -it <pod-name> -n sif-broker -- sh
wget -qO- http://provider-endpoint:8080/health

# Check logs
kubectl logs -n sif-broker -l app=requests-connector | grep "Provider call failed"

# Check network policies
kubectl get networkpolicies -n sif-broker
```

**Solutions**:

1. **Provider Service Down**
   ```
   - Check provider service status
   - Restart provider service if needed
   - Verify provider endpoint configuration
   ```

2. **Network Issues**
   ```
   - Check network policies
   - Verify DNS resolution
   - Test connectivity from pod
   ```

3. **Timeout Too Short**
   ```
   - Increase PROVIDER_READ_TIMEOUT in ConfigMap
   - Restart pods to apply changes
   ```

### Issue 4: Background Worker Not Processing

**Symptoms**:
- DELAYED to IMMEDIATE requests not completing
- Proxy queue depth increasing
- No worker activity in logs

**Diagnosis**:
```bash
# Check Redis queue depth
kubectl exec -it redis-pod -n sif-broker -- redis-cli
LLEN proxy-requests

# Check worker logs
kubectl logs -n sif-broker -l app=requests-connector | grep "ProxyWorkerService"

# Check metrics
curl http://localhost:8080/actuator/metrics/proxy.queue.depth
```

**Solutions**:

1. **Worker Thread Stuck**
   ```
   Solution: Restart service
   kubectl rollout restart deployment/requests-connector -n sif-broker
   ```

2. **Redis Connection Lost**
   ```
   Solution: Check Redis connectivity
   kubectl get pods -n sif-broker -l app=redis
   kubectl logs -n sif-broker redis-pod
   ```

3. **High Retry Rate**
   ```
   Solution: Check provider availability and increase max retries
   - Review PROXY_MAX_RETRIES setting
   - Check provider service health
   ```

### Issue 5: Memory Leaks

**Symptoms**:
- Increasing memory usage over time
- OutOfMemoryError in logs
- Pods being OOMKilled

**Diagnosis**:
```bash
# Check memory usage
kubectl top pods -n sif-broker -l app=requests-connector

# Check heap dump
kubectl exec -it <pod-name> -n sif-broker -- jmap -heap 1

# Get thread dump
kubectl exec -it <pod-name> -n sif-broker -- jstack 1
```

**Solutions**:

1. **Increase Memory Limits**
   ```yaml
   resources:
     limits:
       memory: "2Gi"  # Increase from 1Gi
   ```

2. **Tune JVM Options**
   ```
   JAVA_OPTS: "-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
   ```

3. **Check for Resource Leaks**
   ```
   - Review application logs for unclosed connections
   - Check WebClient connection pool settings
   - Monitor Redis connection pool
   ```

---

## Monitoring and Alerts

### Key Metrics to Monitor

1. **Request Metrics**
   - `requests.immediate.count`: IMMEDIATE request count
   - `requests.delayed.count`: DELAYED request count
   - `requests.error.rate`: Error rate

2. **Performance Metrics**
   - `provider.call.latency`: Provider call latency (p50, p95, p99)
   - `cache.hit.rate`: Cache hit rate (should be > 90%)
   - `proxy.queue.depth`: Background worker queue depth

3. **Resource Metrics**
   - `jvm.memory.used`: JVM memory usage
   - `jvm.threads.live`: Active thread count
   - `system.cpu.usage`: CPU usage

### Recommended Alerts

1. **High Error Rate**
   ```
   Alert: requests.error.rate > 5%
   Action: Check logs for error patterns, verify provider availability
   ```

2. **High Latency**
   ```
   Alert: provider.call.latency.p95 > 5000ms
   Action: Check provider performance, consider scaling
   ```

3. **Low Cache Hit Rate**
   ```
   Alert: cache.hit.rate < 80%
   Action: Check Redis connectivity, review cache TTL
   ```

4. **High Queue Depth**
   ```
   Alert: proxy.queue.depth > 1000
   Action: Check worker processing, verify provider availability
   ```

5. **Memory Usage**
   ```
   Alert: jvm.memory.used > 80% of limit
   Action: Check for memory leaks, consider increasing limits
   ```

---

## Performance Tuning

### 1. Optimize Cache Settings

```yaml
# Increase cache TTL for stable environments
cache.ttl: "600000"  # 10 minutes instead of 5

# Adjust Redis connection pool
spring.redis.lettuce.pool.max-active: 20
spring.redis.lettuce.pool.max-idle: 10
```

### 2. Tune Provider Timeouts

```yaml
# Adjust based on provider performance
provider.timeout.connection: "3000"  # Reduce for faster failure
provider.timeout.read: "60000"  # Increase for slow providers
```

### 3. Optimize Worker Settings

```yaml
# Increase worker parallelism
proxy.worker.poll-interval: "500"  # Poll more frequently
proxy.worker.max-retries: "5"  # More retries for transient failures
```

### 4. Scale Horizontally

```bash
# Add more replicas for high load
kubectl scale deployment requests-connector -n sif-broker --replicas=10

# Enable HPA for automatic scaling
kubectl autoscale deployment requests-connector -n sif-broker \
  --min=3 --max=10 --cpu-percent=70
```

### 5. Optimize JVM Settings

```yaml
env:
  - name: JAVA_OPTS
    value: "-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"
```

---

## Disaster Recovery

### Backup Procedures

1. **Configuration Backup**
   ```bash
   # Export ConfigMap
   kubectl get configmap requests-connector-config -n sif-broker -o yaml > config-backup.yaml
   
   # Export Deployment
   kubectl get deployment requests-connector -n sif-broker -o yaml > deployment-backup.yaml
   ```

2. **Redis Backup**
   ```bash
   # Create Redis snapshot
   kubectl exec -it redis-pod -n sif-broker -- redis-cli BGSAVE
   
   # Copy snapshot
   kubectl cp sif-broker/redis-pod:/data/dump.rdb ./redis-backup.rdb
   ```

### Recovery Procedures

1. **Service Recovery**
   ```bash
   # Restore from backup
   kubectl apply -f config-backup.yaml
   kubectl apply -f deployment-backup.yaml
   
   # Verify recovery
   kubectl get pods -n sif-broker -l app=requests-connector
   curl http://localhost:8080/actuator/health
   ```

2. **Redis Recovery**
   ```bash
   # Copy backup to Redis pod
   kubectl cp ./redis-backup.rdb sif-broker/redis-pod:/data/dump.rdb
   
   # Restart Redis
   kubectl delete pod redis-pod -n sif-broker
   ```

3. **Rollback Deployment**
   ```bash
   # View rollout history
   kubectl rollout history deployment/requests-connector -n sif-broker
   
   # Rollback to previous version
   kubectl rollout undo deployment/requests-connector -n sif-broker
   
   # Rollback to specific revision
   kubectl rollout undo deployment/requests-connector -n sif-broker --to-revision=2
   ```

---

## Contact Information

- **On-Call Team**: sif-broker-oncall@example.com
- **Slack Channel**: #sif-broker-ops
- **Documentation**: https://docs.sif-broker.example.com
- **Issue Tracker**: https://jira.example.com/projects/SIFBROKER
