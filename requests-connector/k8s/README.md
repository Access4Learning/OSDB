# Kubernetes Deployment Guide

This directory contains Kubernetes manifests for deploying the Requests Connector service.

## Prerequisites

- Kubernetes cluster (v1.20+)
- kubectl configured to access your cluster
- Redis deployed in the cluster
- Core services (Environment, Queue, Subscription) deployed
- Responses Connector deployed

## Files

- `deployment.yaml` - Deployment configuration with 3 replicas
- `service.yaml` - ClusterIP service for internal communication
- `configmap.yaml` - Configuration parameters

## Deployment Steps

### 1. Create Namespace (if not exists)

```bash
kubectl create namespace sif-broker
```

### 2. Apply ConfigMap

```bash
kubectl apply -f configmap.yaml
```

### 3. Deploy the Service

```bash
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
```

### 4. Verify Deployment

```bash
# Check pod status
kubectl get pods -n sif-broker -l app=requests-connector

# Check service
kubectl get svc -n sif-broker requests-connector

# View logs
kubectl logs -n sif-broker -l app=requests-connector --tail=100

# Check health
kubectl exec -n sif-broker -it <pod-name> -- wget -qO- http://localhost:8080/actuator/health
```

## Configuration

### Environment Variables

All configuration is managed through the ConfigMap. To update configuration:

1. Edit `configmap.yaml`
2. Apply changes: `kubectl apply -f configmap.yaml`
3. Restart pods: `kubectl rollout restart deployment/requests-connector -n sif-broker`

### Resource Limits

Default resource configuration:
- **Requests**: 512Mi memory, 250m CPU
- **Limits**: 1Gi memory, 1000m CPU

Adjust in `deployment.yaml` based on your workload.

## Scaling

### Manual Scaling

```bash
kubectl scale deployment requests-connector -n sif-broker --replicas=5
```

### Horizontal Pod Autoscaler (HPA)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: requests-connector-hpa
  namespace: sif-broker
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: requests-connector
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

## Monitoring

### Health Checks

- **Liveness**: `/actuator/health/liveness`
- **Readiness**: `/actuator/health/readiness`
- **Startup**: `/actuator/health`

### Metrics

Metrics are exposed at `/actuator/prometheus` for Prometheus scraping.

## Troubleshooting

### Pods Not Starting

```bash
# Check pod events
kubectl describe pod <pod-name> -n sif-broker

# Check logs
kubectl logs <pod-name> -n sif-broker

# Check resource constraints
kubectl top pods -n sif-broker
```

### Connection Issues

```bash
# Test Redis connectivity
kubectl exec -n sif-broker -it <pod-name> -- sh
wget -qO- http://redis-service:6379

# Test Core Service connectivity
wget -qO- http://environment-service:8080/actuator/health
```

### Performance Issues

```bash
# Check resource usage
kubectl top pods -n sif-broker -l app=requests-connector

# Check HPA status
kubectl get hpa -n sif-broker

# View detailed metrics
kubectl describe hpa requests-connector-hpa -n sif-broker
```

## Cleanup

```bash
kubectl delete -f service.yaml
kubectl delete -f deployment.yaml
kubectl delete -f configmap.yaml
```
