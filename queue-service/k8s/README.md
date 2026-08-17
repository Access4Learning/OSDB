# Queue Service - Kubernetes Deployment

This directory contains Kubernetes manifests for deploying the Queue Service.

## Prerequisites

- Kubernetes cluster (1.24+)
- kubectl configured to access your cluster
- PostgreSQL database available
- Redis instance available
- (Optional) S3 bucket for message storage

## Manifests

- `configmap.yaml` - Configuration settings
- `deployment.yaml` - Queue Service deployment (3 replicas)
- `service.yaml` - ClusterIP service
- `pvc.yaml` - Persistent volume claim for filesystem storage

## Deployment Steps

### 1. Create Secrets

Create PostgreSQL credentials:
```bash
kubectl create secret generic postgres-credentials \
  --from-literal=username=postgres \
  --from-literal=password=your-password
```

Create Redis credentials (if using password):
```bash
kubectl create secret generic redis-credentials \
  --from-literal=password=your-redis-password
```

### 2. Update ConfigMap

Edit `configmap.yaml` to match your environment:
- Database host and port
- Redis host and port
- Storage type (s3 or filesystem)
- S3 bucket and region (if using S3)

### 3. Apply Manifests

```bash
# Apply in order
kubectl apply -f configmap.yaml
kubectl apply -f pvc.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
```

### 4. Verify Deployment

```bash
# Check pods
kubectl get pods -l app=queue-service

# Check service
kubectl get svc queue-service

# View logs
kubectl logs -l app=queue-service --tail=100

# Check health
kubectl port-forward svc/queue-service 8082:8082
curl http://localhost:8082/actuator/health
```

## Configuration

### Storage Options

**S3 Storage (Recommended for Production):**
```yaml
storage.type: "s3"
storage.s3.bucket: "your-bucket-name"
storage.s3.region: "us-east-1"
```

Ensure pods have IAM role with S3 permissions or configure AWS credentials.

**Filesystem Storage (Development/Testing):**
```yaml
storage.type: "filesystem"
storage.filesystem.path: "/app/data/messages"
```

Requires persistent volume with ReadWriteMany access mode.

### Resource Limits

Default resource configuration:
- Requests: 512Mi memory, 500m CPU
- Limits: 1Gi memory, 1000m CPU

Adjust based on your workload in `deployment.yaml`.

### Scaling

Scale the deployment:
```bash
kubectl scale deployment queue-service --replicas=5
```

Or use Horizontal Pod Autoscaler:
```bash
kubectl autoscale deployment queue-service \
  --cpu-percent=70 \
  --min=3 \
  --max=10
```

## Monitoring

The service exposes metrics at `/actuator/metrics` and Prometheus metrics at `/actuator/prometheus`.

Health checks:
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`

## Troubleshooting

### Pods not starting

Check events:
```bash
kubectl describe pod <pod-name>
```

Check logs:
```bash
kubectl logs <pod-name>
```

### Database connection issues

Verify database connectivity:
```bash
kubectl exec -it <pod-name> -- sh
wget -O- http://localhost:8082/actuator/health
```

### Storage issues

For filesystem storage, verify PVC is bound:
```bash
kubectl get pvc queue-service-storage
```

For S3 storage, verify IAM permissions and bucket access.

## Cleanup

```bash
kubectl delete -f service.yaml
kubectl delete -f deployment.yaml
kubectl delete -f pvc.yaml
kubectl delete -f configmap.yaml
kubectl delete secret postgres-credentials redis-credentials
```
