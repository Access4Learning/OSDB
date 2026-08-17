# Kubernetes Deployment for Events Connector

This directory contains Kubernetes manifests for deploying the Events Connector service.

## Prerequisites

- Kubernetes cluster (v1.24+)
- kubectl configured to access your cluster
- Subscription Service deployed and accessible
- Queue Service deployed and accessible

## Manifests

- **deployment.yaml**: Deployment with 3 replicas, resource limits, and health probes
- **service.yaml**: ClusterIP service exposing port 8084
- **configmap.yaml**: Configuration for service URLs and retry settings
- **hpa.yaml**: HorizontalPodAutoscaler for automatic scaling (3-10 replicas)

## Deployment

### Deploy All Resources

```bash
kubectl apply -f k8s/
```

### Deploy Individual Resources

```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml
```

## Configuration

### Update Service URLs

Edit `configmap.yaml` to update the Subscription Service and Queue Service URLs:

```yaml
data:
  subscription.service.url: "http://subscription-service:8083"
  queue.service.url: "http://queue-service:8082"
```

After updating, apply the changes:

```bash
kubectl apply -f k8s/configmap.yaml
kubectl rollout restart deployment/events-connector
```

## Monitoring

### Check Deployment Status

```bash
kubectl get deployment events-connector
kubectl get pods -l app=events-connector
```

### Check Service

```bash
kubectl get service events-connector
```

### Check HPA Status

```bash
kubectl get hpa events-connector-hpa
```

### View Logs

```bash
# All pods
kubectl logs -l app=events-connector --tail=100 -f

# Specific pod
kubectl logs <pod-name> -f
```

### Check Health

```bash
# Port forward to access health endpoint
kubectl port-forward service/events-connector 8084:8084

# In another terminal
curl http://localhost:8084/actuator/health
```

## Scaling

### Manual Scaling

```bash
kubectl scale deployment events-connector --replicas=5
```

### Auto-scaling

The HPA automatically scales based on:
- CPU utilization (target: 70%)
- Memory utilization (target: 80%)
- Min replicas: 3
- Max replicas: 10

## Resource Limits

Each pod has the following resource configuration:

**Requests** (guaranteed):
- Memory: 512Mi
- CPU: 250m

**Limits** (maximum):
- Memory: 1Gi
- CPU: 500m

## Health Probes

### Liveness Probe
- Path: `/actuator/health/liveness`
- Initial delay: 40s
- Period: 30s
- Timeout: 5s
- Failure threshold: 3

### Readiness Probe
- Path: `/actuator/health/readiness`
- Initial delay: 20s
- Period: 10s
- Timeout: 3s
- Failure threshold: 3

## Troubleshooting

### Pods Not Starting

```bash
# Check pod status
kubectl describe pod <pod-name>

# Check events
kubectl get events --sort-by=.metadata.creationTimestamp
```

### Service Unavailable

```bash
# Check if Subscription Service is accessible
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- \
  curl http://subscription-service:8083/actuator/health

# Check if Queue Service is accessible
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- \
  curl http://queue-service:8082/actuator/health
```

### High Memory/CPU Usage

```bash
# Check resource usage
kubectl top pods -l app=events-connector

# Check HPA metrics
kubectl describe hpa events-connector-hpa
```

### Rolling Update Issues

```bash
# Check rollout status
kubectl rollout status deployment/events-connector

# View rollout history
kubectl rollout history deployment/events-connector

# Rollback to previous version
kubectl rollout undo deployment/events-connector
```

## Cleanup

### Delete All Resources

```bash
kubectl delete -f k8s/
```

### Delete Individual Resources

```bash
kubectl delete hpa events-connector-hpa
kubectl delete service events-connector
kubectl delete deployment events-connector
kubectl delete configmap events-connector-config
```

## Production Considerations

1. **Namespace**: Deploy to a dedicated namespace (e.g., `sif-broker`)
2. **Resource Limits**: Adjust based on actual load and performance testing
3. **Replicas**: Start with 3 replicas for high availability
4. **Monitoring**: Integrate with Prometheus and Grafana for metrics
5. **Logging**: Configure centralized logging (e.g., ELK stack)
6. **Network Policies**: Implement network policies to restrict traffic
7. **Service Mesh**: Consider using Istio or Linkerd for advanced traffic management
8. **Secrets**: Use Kubernetes Secrets for sensitive configuration
9. **Ingress**: Configure Ingress for external access (if needed)
10. **Backup**: Ensure configuration is version controlled

## Integration with Gateway

The Events Connector should be accessed through the API Gateway. Configure the Gateway to route `/events` requests to the Events Connector service:

```yaml
# Gateway route configuration
- id: events-connector
  uri: http://events-connector:8084
  predicates:
    - Path=/events/**
  filters:
    - StripPrefix=0
```
