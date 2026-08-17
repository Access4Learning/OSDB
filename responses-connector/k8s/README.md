# Kubernetes Deployment Guide for Responses Connector

This directory contains Kubernetes manifests for deploying the Responses Connector service.

## Prerequisites

- Kubernetes cluster (v1.24+)
- kubectl configured to access your cluster
- Queue Service deployed and accessible

## Files

- `deployment.yaml` - Deployment configuration with 2 replicas
- `service.yaml` - ClusterIP service for internal access
- `configmap.yaml` - Configuration for Queue Service connection

## Deployment Steps

### 1. Create ConfigMap

```bash
kubectl apply -f configmap.yaml
```

### 2. Deploy the Service

```bash
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
```

### 3. Verify Deployment

```bash
# Check deployment status
kubectl get deployments responses-connector

# Check pods
kubectl get pods -l app=responses-connector

# Check service
kubectl get svc responses-connector

# View logs
kubectl logs -l app=responses-connector --tail=100
```

### 4. Check Health

```bash
# Port forward to access health endpoint
kubectl port-forward svc/responses-connector 8084:8084

# In another terminal, check health
curl http://localhost:8084/actuator/health
```

## Configuration

### Queue Service URL

Update the Queue Service URL in `configmap.yaml`:

```yaml
queue.service.url: "http://queue-service:8082"
```

### Resource Limits

Adjust resource requests and limits in `deployment.yaml`:

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "500m"
```

### Replicas

Adjust the number of replicas in `deployment.yaml`:

```yaml
spec:
  replicas: 2  # Change this value
```

## Scaling

### Manual Scaling

```bash
kubectl scale deployment responses-connector --replicas=3
```

### Horizontal Pod Autoscaler (HPA)

Create an HPA for automatic scaling:

```bash
kubectl autoscale deployment responses-connector \
  --cpu-percent=70 \
  --min=2 \
  --max=10
```

## Monitoring

### View Metrics

```bash
# Port forward to access metrics endpoint
kubectl port-forward svc/responses-connector 8084:8084

# In another terminal, view metrics
curl http://localhost:8084/actuator/metrics
curl http://localhost:8084/actuator/prometheus
```

### View Logs

```bash
# Follow logs from all pods
kubectl logs -f -l app=responses-connector

# View logs from specific pod
kubectl logs -f <pod-name>
```

## Troubleshooting

### Pod Not Starting

```bash
# Describe pod to see events
kubectl describe pod <pod-name>

# Check logs
kubectl logs <pod-name>
```

### Service Not Accessible

```bash
# Check service endpoints
kubectl get endpoints responses-connector

# Check if pods are ready
kubectl get pods -l app=responses-connector
```

### Queue Service Connection Issues

```bash
# Test connectivity from pod
kubectl exec -it <pod-name> -- wget -O- http://queue-service:8082/actuator/health
```

## Cleanup

```bash
kubectl delete -f deployment.yaml
kubectl delete -f service.yaml
kubectl delete -f configmap.yaml
```

## Production Considerations

1. **Resource Limits**: Adjust based on load testing results
2. **Replicas**: Start with 2, scale based on traffic
3. **Health Checks**: Tune probe timings based on startup time
4. **Monitoring**: Set up Prometheus and Grafana for metrics
5. **Logging**: Configure centralized logging (ELK, Splunk, etc.)
6. **Security**: Use NetworkPolicies to restrict traffic
7. **Secrets**: Use Kubernetes Secrets for sensitive configuration
8. **Ingress**: Configure Ingress if external access is needed (typically not for this service)

## Integration with SIF Unity Broker

The Responses Connector integrates with:

- **Queue Service** (downstream): For enqueuing responses to consumer queues
- **API Gateway** (upstream): Receives authenticated provider responses
- **Requests Connector** (upstream): Receives responses from DELAYED_to_IMMEDIATE pattern

Ensure all dependent services are deployed and accessible before deploying the Responses Connector.
