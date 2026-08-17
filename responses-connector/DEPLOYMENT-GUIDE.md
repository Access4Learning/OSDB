# Responses Connector Deployment Guide

## Overview

This guide provides comprehensive instructions for deploying the Responses Connector service in various environments.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Configuration](#configuration)
3. [Local Development](#local-development)
4. [Docker Deployment](#docker-deployment)
5. [Kubernetes Deployment](#kubernetes-deployment)
6. [Monitoring and Logging](#monitoring-and-logging)
7. [Troubleshooting](#troubleshooting)
8. [Production Checklist](#production-checklist)

---

## Prerequisites

### Required

- Java 17 (Amazon Corretto recommended)
- Maven 3.8+
- Queue Service deployed and accessible

### Optional (for containerized deployment)

- Docker 20.10+
- Docker Compose 2.0+
- Kubernetes 1.24+
- kubectl configured

---

## Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | - | No |
| `SERVER_PORT` | Server port | 8084 | No |
| `QUEUE_SERVICE_URL` | Queue Service base URL | http://localhost:8082 | Yes |
| `QUEUE_SERVICE_CONNECTION_TIMEOUT` | Connection timeout (ms) | 5000 | No |
| `QUEUE_SERVICE_READ_TIMEOUT` | Read timeout (ms) | 10000 | No |
| `QUEUE_SERVICE_MAX_RETRIES` | Maximum retry attempts | 3 | No |
| `QUEUE_SERVICE_RETRY_DELAY` | Initial retry delay (ms) | 100 | No |
| `LOGGING_LEVEL_ORG_SIFUNITY_RESPONSESCONNECTOR` | Logging level | INFO | No |

### Application Profiles

#### `dev` - Development
- Verbose logging
- Mock services enabled
- Relaxed timeouts

#### `docker` - Docker Compose
- Container-optimized settings
- Service discovery via container names

#### `kubernetes` - Kubernetes
- Production-ready settings
- ConfigMap-based configuration
- Health probes enabled

#### `prod` - Production
- Minimal logging
- Strict timeouts
- Performance optimizations

---

## Local Development

### 1. Build the Application

```bash
cd responses-connector
mvn clean install
```

### 2. Run Tests

```bash
# Run all tests
mvn test

# Run unit tests only
mvn test -Dtest="*Test"

# Run property-based tests only
mvn test -Dtest="*PropertiesTest"

# Run integration tests only
mvn test -Dtest="*IntegrationTest"
```

### 3. Run Locally

```bash
# Using Maven
mvn spring-boot:run

# Using JAR
java -jar target/responses-connector-1.0.0-SNAPSHOT.jar

# With custom configuration
java -jar target/responses-connector-1.0.0-SNAPSHOT.jar \
  --queue.service.url=http://localhost:8082
```

### 4. Verify Service

```bash
# Check health
curl http://localhost:8084/actuator/health

# Check metrics
curl http://localhost:8084/actuator/metrics
```

---

## Docker Deployment

### 1. Build Docker Image

```bash
cd responses-connector
docker build -t responses-connector:latest .
```

### 2. Run with Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f responses-connector

# Stop services
docker-compose down
```

### 3. Run Standalone Container

```bash
docker run -d \
  --name responses-connector \
  -p 8084:8084 \
  -e QUEUE_SERVICE_URL=http://queue-service:8082 \
  -e SPRING_PROFILES_ACTIVE=docker \
  responses-connector:latest
```

### 4. Verify Deployment

```bash
# Check container status
docker ps | grep responses-connector

# View logs
docker logs -f responses-connector

# Check health
curl http://localhost:8084/actuator/health
```

### Docker Compose Configuration

The `docker-compose.yml` includes:
- Responses Connector service
- Mock Queue Service (for testing)
- Network configuration
- Health checks

**Production Note**: Replace mock services with actual service URLs.

---

## Kubernetes Deployment

### 1. Prerequisites

Ensure the following are deployed:
- Queue Service
- ConfigMaps and Secrets (if needed)

### 2. Create ConfigMap

```bash
kubectl apply -f k8s/configmap.yaml
```

Verify:
```bash
kubectl get configmap responses-connector-config
kubectl describe configmap responses-connector-config
```

### 3. Deploy Service

```bash
# Apply deployment
kubectl apply -f k8s/deployment.yaml

# Apply service
kubectl apply -f k8s/service.yaml
```

### 4. Verify Deployment

```bash
# Check deployment
kubectl get deployment responses-connector
kubectl describe deployment responses-connector

# Check pods
kubectl get pods -l app=responses-connector
kubectl describe pod <pod-name>

# Check service
kubectl get svc responses-connector
kubectl describe svc responses-connector

# View logs
kubectl logs -l app=responses-connector --tail=100 -f
```

### 5. Test Service

```bash
# Port forward
kubectl port-forward svc/responses-connector 8084:8084

# In another terminal, test health
curl http://localhost:8084/actuator/health
```

### 6. Scaling

```bash
# Manual scaling
kubectl scale deployment responses-connector --replicas=3

# Auto-scaling (HPA)
kubectl autoscale deployment responses-connector \
  --cpu-percent=70 \
  --min=2 \
  --max=10

# Check HPA status
kubectl get hpa responses-connector
```

### 7. Rolling Updates

```bash
# Update image
kubectl set image deployment/responses-connector \
  responses-connector=responses-connector:v1.1.0

# Check rollout status
kubectl rollout status deployment/responses-connector

# Rollback if needed
kubectl rollout undo deployment/responses-connector
```

---

## Monitoring and Logging

### Health Checks

#### Liveness Probe
- **Endpoint**: `/actuator/health/liveness`
- **Purpose**: Determines if the application is running
- **Action on Failure**: Restart container

#### Readiness Probe
- **Endpoint**: `/actuator/health/readiness`
- **Purpose**: Determines if the application can accept traffic
- **Action on Failure**: Remove from load balancer

### Metrics

#### Prometheus Integration

```bash
# Access Prometheus metrics
curl http://localhost:8084/actuator/prometheus
```

#### Key Metrics

- `responses_received_total` - Total responses received
- `responses_routed_total` - Total responses successfully routed
- `responses_failed_total` - Total failed routing attempts
- `queue_service_calls_total` - Total Queue Service API calls
- `queue_service_errors_total` - Total Queue Service errors

### Logging

#### Log Levels

```yaml
logging:
  level:
    org.sifunity.responsesconnector: INFO
    org.springframework.web: INFO
```

#### Structured Logging

Logs are output in JSON format for easy parsing:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "level": "INFO",
  "logger": "org.sifunity.responsesconnector.service.ResponseRoutingService",
  "message": "Routing response to queue",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "queueId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "requestId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

#### Viewing Logs

**Docker**:
```bash
docker logs -f responses-connector
```

**Kubernetes**:
```bash
kubectl logs -f -l app=responses-connector
```

---

## Troubleshooting

### Service Won't Start

**Symptoms**: Container/pod crashes or restarts repeatedly

**Checks**:
1. Verify Queue Service is accessible
2. Check configuration (environment variables, ConfigMap)
3. Review logs for errors
4. Verify resource limits (memory, CPU)

**Commands**:
```bash
# Docker
docker logs responses-connector

# Kubernetes
kubectl logs <pod-name>
kubectl describe pod <pod-name>
```

### Queue Service Connection Errors

**Symptoms**: `SERVICE_UNAVAILABLE` errors, retry attempts in logs

**Checks**:
1. Verify Queue Service is running
2. Check Queue Service URL configuration
3. Test connectivity from container/pod
4. Review network policies (Kubernetes)

**Commands**:
```bash
# Docker
docker exec responses-connector wget -O- http://queue-service:8082/actuator/health

# Kubernetes
kubectl exec -it <pod-name> -- wget -O- http://queue-service:8082/actuator/health
```

### High Memory Usage

**Symptoms**: OOMKilled errors, slow performance

**Checks**:
1. Review JVM heap settings
2. Check for memory leaks
3. Analyze heap dumps
4. Increase resource limits

**Commands**:
```bash
# Get heap dump (Kubernetes)
kubectl exec <pod-name> -- jcmd 1 GC.heap_dump /tmp/heap.hprof
kubectl cp <pod-name>:/tmp/heap.hprof ./heap.hprof
```

### Slow Response Times

**Symptoms**: High latency, timeout errors

**Checks**:
1. Check Queue Service response times
2. Review retry configuration
3. Analyze metrics for bottlenecks
4. Check network latency

**Commands**:
```bash
# Check metrics
curl http://localhost:8084/actuator/metrics/queue.service.calls.duration
```

### Health Check Failures

**Symptoms**: Pods not ready, containers restarting

**Checks**:
1. Verify health endpoint is accessible
2. Check probe timing configuration
3. Review application logs
4. Test health endpoint manually

**Commands**:
```bash
# Test health endpoint
kubectl exec <pod-name> -- wget -O- http://localhost:8084/actuator/health
```

---

## Production Checklist

### Pre-Deployment

- [ ] All tests passing (unit, integration, property-based)
- [ ] Code coverage ≥ 80%
- [ ] Security scan completed
- [ ] Performance testing completed
- [ ] Load testing completed (target: 500+ responses/second)
- [ ] Queue Service deployed and accessible
- [ ] Configuration reviewed and validated
- [ ] Secrets configured (if needed)
- [ ] Resource limits defined
- [ ] Monitoring configured (Prometheus, Grafana)
- [ ] Logging configured (centralized logging)
- [ ] Alerting rules defined

### Deployment

- [ ] Deploy to staging environment first
- [ ] Verify health checks pass
- [ ] Test API endpoints
- [ ] Verify Queue Service integration
- [ ] Monitor metrics and logs
- [ ] Perform smoke tests
- [ ] Deploy to production with rolling update
- [ ] Monitor for errors
- [ ] Verify traffic routing

### Post-Deployment

- [ ] Monitor metrics for anomalies
- [ ] Check error rates
- [ ] Verify response times
- [ ] Review logs for errors
- [ ] Test failover scenarios
- [ ] Document any issues
- [ ] Update runbook if needed

### Configuration Recommendations

#### Resource Limits (Kubernetes)

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "500m"
```

#### Replicas

- **Minimum**: 2 (for high availability)
- **Recommended**: 3-5 (based on load)
- **Maximum**: 10 (with HPA)

#### Timeouts

- **Connection Timeout**: 5000ms
- **Read Timeout**: 10000ms
- **Retry Delay**: 100ms (exponential backoff)
- **Max Retries**: 3

#### Health Probes

```yaml
livenessProbe:
  initialDelaySeconds: 40
  periodSeconds: 30
  timeoutSeconds: 5
  failureThreshold: 3

readinessProbe:
  initialDelaySeconds: 20
  periodSeconds: 10
  timeoutSeconds: 3
  failureThreshold: 3
```

---

## Security Considerations

### Network Security

- Deploy behind API Gateway (external access)
- Use internal network for Queue Service communication
- Implement network policies (Kubernetes)
- Use TLS for Gateway ingress

### Container Security

- Run as non-root user (configured in Dockerfile)
- Use minimal base image (Alpine)
- Scan images for vulnerabilities
- Keep dependencies updated

### Configuration Security

- Use Kubernetes Secrets for sensitive data
- Rotate credentials regularly
- Limit access to ConfigMaps
- Use RBAC for access control

---

## Backup and Recovery

### Stateless Service

The Responses Connector is stateless - no data backup required. Recovery involves:

1. Redeploy service from image
2. Verify configuration
3. Test connectivity to Queue Service
4. Monitor for errors

### Disaster Recovery

1. Deploy to secondary region/cluster
2. Configure DNS failover
3. Test failover procedures
4. Document recovery steps

---

## Support and Maintenance

### Regular Maintenance

- Update dependencies monthly
- Review and optimize configuration
- Analyze metrics for trends
- Review logs for errors
- Test disaster recovery procedures

### Monitoring Alerts

Configure alerts for:
- High error rates (> 5%)
- High response times (> 500ms)
- Queue Service connection failures
- Pod restarts
- Resource exhaustion

---

## Additional Resources

- [API Documentation](./API-DOCUMENTATION.md)
- [README](./README.md)
- [Kubernetes README](./k8s/README.md)
- [Design Document](../.kiro/specs/responses-connector/design.md)
- [Requirements Document](../.kiro/specs/responses-connector/requirements.md)

---

## Changelog

### Version 1.0.0 (2024-01-15)
- Initial deployment guide
- Docker and Kubernetes deployment instructions
- Monitoring and troubleshooting guidance
- Production checklist
