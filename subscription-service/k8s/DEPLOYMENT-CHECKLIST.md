# Subscription Service - Kubernetes Deployment Checklist

This checklist helps ensure a successful deployment of the Subscription Service to Kubernetes.

## Pre-Deployment Checklist

### Infrastructure Requirements
- [ ] Kubernetes cluster (1.24+) is available and accessible
- [ ] kubectl is configured and can access the cluster
- [ ] PostgreSQL database is deployed and accessible
- [ ] Redis instance is deployed and accessible
- [ ] Queue Service is deployed and accessible at http://queue-service:8082

### Database Setup
- [ ] Database `sif_broker` exists
- [ ] Database user has appropriate permissions (CREATE, SELECT, INSERT, UPDATE, DELETE)
- [ ] Flyway migrations will run automatically on first startup

### Secrets Configuration
- [ ] PostgreSQL credentials secret created:
  ```bash
  kubectl create secret generic postgres-credentials \
    --from-literal=username=<username> \
    --from-literal=password=<password>
  ```
- [ ] Redis credentials secret created (if Redis requires authentication):
  ```bash
  kubectl create secret generic redis-credentials \
    --from-literal=password=<redis-password>
  ```

### ConfigMap Configuration
- [ ] Review `configmap.yaml` and update if needed:
  - [ ] Database host (default: postgres-service)
  - [ ] Database port (default: 5432)
  - [ ] Database name (default: sif_broker)
  - [ ] Redis host (default: redis-service)
  - [ ] Redis port (default: 6379)
  - [ ] Queue Service URL (default: http://queue-service:8082)

## Deployment Steps

### 1. Apply Manifests
```bash
# Apply ConfigMap
kubectl apply -f configmap.yaml

# Apply Deployment
kubectl apply -f deployment.yaml

# Apply Service
kubectl apply -f service.yaml
```

### 2. Verify Deployment
- [ ] Check pods are running:
  ```bash
  kubectl get pods -l app=subscription-service
  ```
  Expected: 2 pods in Running state

- [ ] Check service is created:
  ```bash
  kubectl get svc subscription-service
  ```
  Expected: ClusterIP service on port 8083

- [ ] View pod logs:
  ```bash
  kubectl logs -l app=subscription-service --tail=50
  ```
  Expected: No errors, successful startup messages

### 3. Health Check Verification
- [ ] Port forward to service:
  ```bash
  kubectl port-forward svc/subscription-service 8083:8083
  ```

- [ ] Check liveness endpoint:
  ```bash
  curl http://localhost:8083/actuator/health/liveness
  ```
  Expected: `{"status":"UP"}`

- [ ] Check readiness endpoint:
  ```bash
  curl http://localhost:8083/actuator/health/readiness
  ```
  Expected: `{"status":"UP"}` with database and Redis components UP

- [ ] Check full health:
  ```bash
  curl http://localhost:8083/actuator/health
  ```
  Expected: All components (db, redis, diskSpace, ping) showing UP

### 4. Functional Testing
- [ ] Test subscription creation:
  ```bash
  curl -X POST http://localhost:8083/subscriptions \
    -H "Content-Type: application/json" \
    -d '{
      "queueId": "<valid-queue-uuid>",
      "serviceName": "TestService",
      "zoneId": "test-zone",
      "contextId": "test-context",
      "type": "EVENT"
    }'
  ```
  Expected: 201 Created with subscription details

- [ ] Test subscription query:
  ```bash
  curl "http://localhost:8083/subscriptions/events?serviceName=TestService&zoneId=test-zone&contextId=test-context"
  ```
  Expected: 200 OK with array of matching subscriptions

## Post-Deployment Verification

### Monitoring
- [ ] Verify metrics endpoint is accessible:
  ```bash
  curl http://localhost:8083/actuator/metrics
  ```

- [ ] Verify Prometheus metrics endpoint:
  ```bash
  curl http://localhost:8083/actuator/prometheus
  ```

### Resource Usage
- [ ] Check pod resource usage:
  ```bash
  kubectl top pods -l app=subscription-service
  ```
  Expected: Within configured limits (512Mi-1Gi memory, 250m-500m CPU)

### Logs
- [ ] Check for any errors or warnings:
  ```bash
  kubectl logs -l app=subscription-service --tail=100 | grep -i error
  kubectl logs -l app=subscription-service --tail=100 | grep -i warn
  ```

### Integration Testing
- [ ] Verify connectivity to PostgreSQL:
  - Check logs for successful database connection
  - Verify Flyway migrations ran successfully

- [ ] Verify connectivity to Redis:
  - Check logs for successful Redis connection
  - Test caching by querying same subscription twice

- [ ] Verify connectivity to Queue Service:
  - Create subscription with valid queue ID
  - Verify queue validation works (try invalid queue ID, should get 404)

## Scaling Verification

### Horizontal Scaling
- [ ] Scale to 3 replicas:
  ```bash
  kubectl scale deployment subscription-service --replicas=3
  ```

- [ ] Verify all pods are running:
  ```bash
  kubectl get pods -l app=subscription-service
  ```

- [ ] Test load distribution:
  - Make multiple API calls
  - Check logs from different pods to verify requests are distributed

### Rolling Update
- [ ] Test rolling update:
  ```bash
  kubectl set image deployment/subscription-service \
    subscription-service=sif-broker/subscription-service:new-version
  ```

- [ ] Verify zero-downtime update:
  - Monitor pod status during update
  - Verify service remains accessible

## Troubleshooting Common Issues

### Pods Not Starting
- [ ] Check pod events: `kubectl describe pod <pod-name>`
- [ ] Check pod logs: `kubectl logs <pod-name>`
- [ ] Verify secrets exist: `kubectl get secrets`
- [ ] Verify ConfigMap exists: `kubectl get configmap subscription-service-config`

### Database Connection Failures
- [ ] Verify PostgreSQL is accessible from pod:
  ```bash
  kubectl exec -it <pod-name> -- sh
  nc -zv postgres-service 5432
  ```
- [ ] Check database credentials in secret
- [ ] Verify database name and permissions

### Redis Connection Failures
- [ ] Verify Redis is accessible from pod:
  ```bash
  kubectl exec -it <pod-name> -- sh
  nc -zv redis-service 6379
  ```
- [ ] Check Redis password in secret (if applicable)

### Queue Service Connection Failures
- [ ] Verify Queue Service is running:
  ```bash
  kubectl get pods -l app=queue-service
  kubectl get svc queue-service
  ```
- [ ] Test connectivity from subscription service pod:
  ```bash
  kubectl exec -it <pod-name> -- sh
  wget -O- http://queue-service:8082/actuator/health
  ```

### Health Check Failures
- [ ] Check readiness probe logs in pod events
- [ ] Verify health endpoints are accessible inside pod:
  ```bash
  kubectl exec -it <pod-name> -- sh
  wget -O- http://localhost:8083/actuator/health/readiness
  ```
- [ ] Check if database or Redis is down

## Rollback Procedure

If deployment fails:

1. [ ] Check previous deployment revision:
   ```bash
   kubectl rollout history deployment/subscription-service
   ```

2. [ ] Rollback to previous version:
   ```bash
   kubectl rollout undo deployment/subscription-service
   ```

3. [ ] Verify rollback success:
   ```bash
   kubectl rollout status deployment/subscription-service
   ```

## Production Readiness

### Security
- [ ] Use proper secret management (Sealed Secrets, External Secrets Operator)
- [ ] Implement network policies to restrict traffic
- [ ] Enable pod security policies/standards
- [ ] Use non-root user (already configured in deployment)

### High Availability
- [ ] Run at least 3 replicas across multiple availability zones
- [ ] Configure pod anti-affinity for node distribution
- [ ] Set up pod disruption budgets
- [ ] Configure horizontal pod autoscaling

### Monitoring and Alerting
- [ ] Set up Prometheus scraping for metrics
- [ ] Create Grafana dashboards
- [ ] Configure alerts for:
  - Pod restarts
  - High error rates
  - Database connection failures
  - Redis connection failures
  - High latency

### Backup and Recovery
- [ ] Ensure PostgreSQL backups are configured
- [ ] Document recovery procedures
- [ ] Test disaster recovery process

## Sign-Off

- [ ] Development team approval
- [ ] QA team approval
- [ ] Operations team approval
- [ ] Security team approval (for production)

---

**Deployment Date:** _______________

**Deployed By:** _______________

**Environment:** _______________

**Version:** _______________

**Notes:**
