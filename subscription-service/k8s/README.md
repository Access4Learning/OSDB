# Subscription Service - Kubernetes Deployment

This directory contains Kubernetes manifests for deploying the Subscription Service.

## Prerequisites

- Kubernetes cluster (1.24+)
- kubectl configured to access your cluster
- PostgreSQL database available
- Redis instance available
- Queue Service deployed and accessible

## Manifests

- `configmap.yaml` - Configuration settings
- `secret.yaml` - Secret template for database credentials
- `deployment.yaml` - Subscription Service deployment (2 replicas)
- `service.yaml` - ClusterIP service exposing port 8083

## Deployment Steps

### 1. Create Secrets

The subscription service uses shared secrets for PostgreSQL and Redis credentials.

**Create PostgreSQL credentials:**
```bash
kubectl create secret generic postgres-credentials \
  --from-literal=username=postgres \
  --from-literal=password=your-password
```

**Create Redis credentials (if using password):**
```bash
kubectl create secret generic redis-credentials \
  --from-literal=password=your-redis-password
```

Alternatively, you can use the `secret.yaml` template (update with base64 encoded values):
```bash
# Encode your credentials
echo -n "your-username" | base64
echo -n "your-password" | base64

# Edit secret.yaml with the encoded values, then apply
kubectl apply -f secret.yaml
```

### 2. Update ConfigMap

Edit `configmap.yaml` to match your environment:
- Database host and port (default: postgres-service:5432)
- Redis host and port (default: redis-service:6379)
- Queue Service URL (default: http://queue-service:8082)

### 3. Apply Manifests

```bash
# Apply in order
kubectl apply -f configmap.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
```

### 4. Verify Deployment

```bash
# Check pods
kubectl get pods -l app=subscription-service

# Check service
kubectl get svc subscription-service

# View logs
kubectl logs -l app=subscription-service --tail=100

# Check health
kubectl port-forward svc/subscription-service 8083:8083
curl http://localhost:8083/actuator/health
```

## Configuration

### Environment Variables

The deployment uses the following environment variables from ConfigMap and Secrets:

**Database Configuration (from ConfigMap and Secrets):**
- `DB_HOST` - PostgreSQL host (default: postgres-service)
- `DB_PORT` - PostgreSQL port (default: 5432)
- `DB_NAME` - Database name (default: sif_broker)
- `DB_USERNAME` - Database username (from secret)
- `DB_PASSWORD` - Database password (from secret)

**Redis Configuration (from ConfigMap and Secrets):**
- `REDIS_HOST` - Redis host (default: redis-service)
- `REDIS_PORT` - Redis port (default: 6379)
- `REDIS_PASSWORD` - Redis password (from secret, optional)

**Queue Service Configuration (from ConfigMap):**
- `QUEUE_SERVICE_URL` - Queue Service endpoint (default: http://queue-service:8082)

**Server Configuration:**
- `SERVER_PORT` - HTTP port (default: 8083)

### Resource Limits

Default resource configuration:
- Requests: 512Mi memory, 250m CPU
- Limits: 1Gi memory, 500m CPU

Adjust based on your workload in `deployment.yaml`.

### Health Checks

**Liveness Probe:**
- Path: `/actuator/health/liveness`
- Initial delay: 40 seconds
- Period: 30 seconds
- Timeout: 5 seconds
- Failure threshold: 3

**Readiness Probe:**
- Path: `/actuator/health/readiness`
- Initial delay: 20 seconds
- Period: 10 seconds
- Timeout: 3 seconds
- Failure threshold: 3

## Scaling

Scale the deployment:
```bash
kubectl scale deployment subscription-service --replicas=3
```

Or use Horizontal Pod Autoscaler:
```bash
kubectl autoscale deployment subscription-service \
  --cpu-percent=70 \
  --min=2 \
  --max=10
```

## Monitoring

The service exposes metrics at `/actuator/metrics` and Prometheus metrics at `/actuator/prometheus`.

Health checks:
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`

## API Access

The service is exposed internally via ClusterIP service on port 8083.

**Service DNS:**
```
subscription-service.default.svc.cluster.local:8083
```

**API Endpoints:**
- `POST /subscriptions` - Create subscription
- `GET /subscriptions/events` - Query EVENT subscriptions
- `GET /subscriptions/delayed-requests` - Query DELAYED_REQUEST subscription
- `GET /subscriptions/{id}` - Get subscription by ID
- `GET /subscriptions?queueId={uuid}` - List subscriptions by queue
- `DELETE /subscriptions/{id}` - Delete subscription

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
wget -O- http://localhost:8083/actuator/health
```

Check database credentials:
```bash
kubectl get secret postgres-credentials -o yaml
```

### Redis connection issues

Verify Redis connectivity:
```bash
kubectl exec -it <pod-name> -- sh
# Check if Redis is accessible
nc -zv redis-service 6379
```

### Queue Service connectivity issues

Verify Queue Service is running:
```bash
kubectl get pods -l app=queue-service
kubectl get svc queue-service
```

Test connectivity from subscription service pod:
```bash
kubectl exec -it <subscription-pod-name> -- sh
wget -O- http://queue-service:8082/actuator/health
```

### View application logs

```bash
# All logs
kubectl logs -l app=subscription-service

# Follow logs
kubectl logs -l app=subscription-service -f

# Logs from specific pod
kubectl logs <pod-name>

# Previous container logs (if pod restarted)
kubectl logs <pod-name> --previous
```

## Cleanup

```bash
kubectl delete -f service.yaml
kubectl delete -f deployment.yaml
kubectl delete -f configmap.yaml
kubectl delete secret postgres-credentials redis-credentials
```

## Production Considerations

### Secret Management

For production deployments, use proper secret management:

**Option 1: Sealed Secrets**
```bash
# Install Sealed Secrets controller
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.18.0/controller.yaml

# Create sealed secret
kubeseal --format=yaml < secret.yaml > sealed-secret.yaml
kubectl apply -f sealed-secret.yaml
```

**Option 2: External Secrets Operator**
```bash
# Install External Secrets Operator
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets

# Create ExternalSecret resource pointing to your secret store (AWS Secrets Manager, Vault, etc.)
```

### High Availability

For production, consider:
- Running at least 3 replicas across multiple availability zones
- Using pod anti-affinity to spread pods across nodes
- Configuring pod disruption budgets
- Using horizontal pod autoscaling based on CPU/memory metrics

### Monitoring and Alerting

Set up monitoring:
- Prometheus for metrics collection
- Grafana for visualization
- Alert rules for critical conditions (pod restarts, high error rates, etc.)

### Network Policies

Implement network policies to restrict traffic:
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: subscription-service-netpol
spec:
  podSelector:
    matchLabels:
      app: subscription-service
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: events-connector
    - podSelector:
        matchLabels:
          app: requests-connector
    ports:
    - protocol: TCP
      port: 8083
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: postgres
    ports:
    - protocol: TCP
      port: 5432
  - to:
    - podSelector:
        matchLabels:
          app: redis
    ports:
    - protocol: TCP
      port: 6379
  - to:
    - podSelector:
        matchLabels:
          app: queue-service
    ports:
    - protocol: TCP
      port: 8082
```

