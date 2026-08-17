# Kubernetes Deployment Guide for Services Connector

This directory contains Kubernetes manifests for deploying the Services Connector service.

## Prerequisites

- Kubernetes cluster (v1.24+)
- kubectl configured to access your cluster
- Redis deployed in the cluster
- Job Service deployed in the cluster
- Environment Service deployed in the cluster
- Docker image built and pushed to registry

## Manifests Overview

### deployment.yaml

Contains all necessary Kubernetes resources:

1. **ConfigMap** (`services-connector-config`)
   - Application configuration
   - Service URLs
   - Timeout settings
   - Redis configuration

2. **Deployment** (`services-connector`)
   - 3 replicas for high availability
   - Rolling update strategy
   - Resource limits: 512Mi-1Gi memory, 250m-500m CPU
   - Health probes (liveness, readiness, startup)
   - Security context (non-root user)

3. **Service** (`services-connector`)
   - ClusterIP type
   - Port 8085 for HTTP and Actuator

4. **HorizontalPodAutoscaler** (`services-connector-hpa`)
   - Min replicas: 3
   - Max replicas: 10
   - CPU target: 70%
   - Memory target: 80%

5. **PodDisruptionBudget** (`services-connector-pdb`)
   - Minimum available: 2 pods
   - Ensures high availability during disruptions

## Deployment Steps

### 1. Build and Push Docker Image

```bash
# Build the application
cd services-connector
mvn clean package -DskipTests

# Build Docker image
docker build -t sif-unity-broker/services-connector:latest .

# Tag for your registry
docker tag sif-unity-broker/services-connector:latest <your-registry>/services-connector:latest

# Push to registry
docker push <your-registry>/services-connector:latest
```

### 2. Update Image Reference

Edit `deployment.yaml` and update the image reference:

```yaml
spec:
  containers:
  - name: services-connector
    image: <your-registry>/services-connector:latest
```

### 3. Configure Environment-Specific Settings

Edit the ConfigMap in `deployment.yaml` to match your environment:

```yaml
data:
  JOB_SERVICE_URL: "http://job-service:8083"
  ENVIRONMENT_SERVICE_URL: "http://environment-service:8081"
  SPRING_REDIS_HOST: "redis"
  SPRING_REDIS_PORT: "6379"
```

### 4. Deploy to Kubernetes

```bash
# Apply all manifests
kubectl apply -f k8s/deployment.yaml

# Verify deployment
kubectl get deployments services-connector
kubectl get pods -l app=services-connector
kubectl get services services-connector
kubectl get hpa services-connector-hpa
```

### 5. Verify Deployment

```bash
# Check pod status
kubectl get pods -l app=services-connector

# Check logs
kubectl logs -l app=services-connector --tail=100

# Check health endpoint
kubectl port-forward svc/services-connector 8085:8085
curl http://localhost:8085/actuator/health
```

## Configuration

### Environment Variables

All configuration is managed through the ConfigMap. Key settings:

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 8085 | Application port |
| `JOB_SERVICE_URL` | http://job-service:8083 | Job Service URL |
| `ENVIRONMENT_SERVICE_URL` | http://environment-service:8081 | Environment Service URL |
| `SPRING_REDIS_HOST` | redis | Redis host |
| `SPRING_REDIS_PORT` | 6379 | Redis port |
| `PROVIDER_TIMEOUT_CONNECTION` | 5000 | Provider connection timeout (ms) |
| `PROVIDER_TIMEOUT_READ` | 30000 | Provider read timeout (ms) |
| `PROVIDER_CACHE_TTL` | 300 | Provider cache TTL (seconds) |

### Resource Limits

Default resource configuration:

- **Requests**: 512Mi memory, 250m CPU
- **Limits**: 1Gi memory, 500m CPU

Adjust based on your workload:

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "500m"
```

### Autoscaling

The HPA automatically scales based on:
- CPU utilization (target: 70%)
- Memory utilization (target: 80%)
- Min replicas: 3
- Max replicas: 10

Adjust scaling parameters:

```yaml
spec:
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

## Health Checks

The deployment includes three types of probes:

### Liveness Probe
- Endpoint: `/actuator/health/liveness`
- Initial delay: 30s
- Period: 10s
- Failure threshold: 3

### Readiness Probe
- Endpoint: `/actuator/health/readiness`
- Initial delay: 20s
- Period: 5s
- Failure threshold: 3

### Startup Probe
- Endpoint: `/actuator/health/liveness`
- Initial delay: 10s
- Period: 5s
- Failure threshold: 12 (60s total)

## Monitoring

### Prometheus Metrics

The service exposes Prometheus metrics at `/actuator/prometheus`.

Annotations for Prometheus scraping:

```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "8085"
  prometheus.io/path: "/actuator/prometheus"
```

### Key Metrics

- `http_server_requests_seconds` - HTTP request latency
- `jvm_memory_used_bytes` - JVM memory usage
- `jvm_gc_pause_seconds` - GC pause time
- Custom application metrics

## Troubleshooting

### Pods Not Starting

```bash
# Check pod events
kubectl describe pod <pod-name>

# Check logs
kubectl logs <pod-name>

# Check resource constraints
kubectl top pods -l app=services-connector
```

### Service Unavailable

```bash
# Check service endpoints
kubectl get endpoints services-connector

# Check pod readiness
kubectl get pods -l app=services-connector

# Test connectivity
kubectl run -it --rm debug --image=busybox --restart=Never -- wget -O- http://services-connector:8085/actuator/health
```

### High Memory Usage

```bash
# Check memory metrics
kubectl top pods -l app=services-connector

# Adjust JVM heap size in Dockerfile
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

# Increase resource limits
resources:
  limits:
    memory: "2Gi"
```

### Redis Connection Issues

```bash
# Check Redis service
kubectl get svc redis

# Test Redis connectivity
kubectl run -it --rm redis-test --image=redis --restart=Never -- redis-cli -h redis ping

# Check Redis logs
kubectl logs -l app=redis
```

## Scaling

### Manual Scaling

```bash
# Scale to 5 replicas
kubectl scale deployment services-connector --replicas=5

# Verify scaling
kubectl get pods -l app=services-connector
```

### Autoscaling

The HPA automatically scales based on metrics. Monitor autoscaling:

```bash
# Check HPA status
kubectl get hpa services-connector-hpa

# Describe HPA for details
kubectl describe hpa services-connector-hpa
```

## Updates and Rollbacks

### Rolling Update

```bash
# Update image
kubectl set image deployment/services-connector services-connector=<new-image>

# Monitor rollout
kubectl rollout status deployment/services-connector

# Check rollout history
kubectl rollout history deployment/services-connector
```

### Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/services-connector

# Rollback to specific revision
kubectl rollout undo deployment/services-connector --to-revision=2
```

## Cleanup

```bash
# Delete all resources
kubectl delete -f k8s/deployment.yaml

# Verify deletion
kubectl get all -l app=services-connector
```

## Security Considerations

1. **Non-root User**: Containers run as user 1000
2. **Read-only Root Filesystem**: Disabled (required for temp files)
3. **Dropped Capabilities**: All capabilities dropped
4. **Network Policies**: Consider adding network policies to restrict traffic
5. **Secrets**: Use Kubernetes Secrets for sensitive data (not ConfigMaps)

## Production Checklist

- [ ] Docker image built and pushed to registry
- [ ] ConfigMap updated with production URLs
- [ ] Resource limits adjusted for production workload
- [ ] Redis deployed and accessible
- [ ] Job Service deployed and accessible
- [ ] Environment Service deployed and accessible
- [ ] Prometheus monitoring configured
- [ ] Logging aggregation configured
- [ ] Network policies defined
- [ ] Backup and disaster recovery plan in place
- [ ] Load testing completed
- [ ] Security scan completed

## Support

For issues or questions:
- Check logs: `kubectl logs -l app=services-connector`
- Check events: `kubectl get events --sort-by='.lastTimestamp'`
- Review metrics: Access Prometheus/Grafana dashboards
- Contact: SIF Unity Broker Team
