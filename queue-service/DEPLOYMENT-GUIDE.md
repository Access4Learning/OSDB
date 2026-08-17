# Queue Service - Deployment Guide

## Quick Start

### Local Development with Docker Compose

```bash
# Start all services (PostgreSQL, Redis, Queue Service)
docker-compose up

# The service will be available at http://localhost:8082
# Health check: http://localhost:8082/actuator/health
```

### Local Development with Maven

```bash
# Ensure PostgreSQL and Redis are running
# Update application.yml with connection details

# Run the service
./mvnw spring-boot:run

# Or build and run JAR
./mvnw clean package
java -jar target/queue-service-1.0.0-SNAPSHOT.jar
```

## Production Deployment

### Kubernetes Deployment

See [k8s/README.md](k8s/README.md) for detailed Kubernetes deployment instructions.

Quick deployment:
```bash
# Create secrets
kubectl create secret generic postgres-credentials \
  --from-literal=username=postgres \
  --from-literal=password=your-password

# Apply manifests
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/pvc.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

# Verify
kubectl get pods -l app=queue-service
kubectl logs -l app=queue-service --tail=100
```

## Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `DB_HOST` | PostgreSQL host | localhost | Yes |
| `DB_PORT` | PostgreSQL port | 5432 | Yes |
| `DB_NAME` | Database name | sif_broker | Yes |
| `DB_USERNAME` | Database username | postgres | Yes |
| `DB_PASSWORD` | Database password | postgres | Yes |
| `REDIS_HOST` | Redis host | localhost | Yes |
| `REDIS_PORT` | Redis port | 6379 | Yes |
| `REDIS_PASSWORD` | Redis password | (empty) | No |
| `STORAGE_TYPE` | Storage backend | filesystem | Yes |
| `S3_BUCKET` | S3 bucket name | - | If S3 |
| `AWS_REGION` | AWS region | us-east-1 | If S3 |
| `STORAGE_PATH` | Filesystem path | ./data/messages | If filesystem |
| `SERVER_PORT` | HTTP port | 8082 | No |

### Storage Configuration

**S3 Storage (Production):**
```yaml
STORAGE_TYPE=s3
S3_BUCKET=your-bucket-name
AWS_REGION=us-east-1
```

Requires AWS credentials via:
- Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
- IAM role (recommended for EC2/ECS/EKS)
- AWS credentials file (~/.aws/credentials)

**Filesystem Storage (Development):**
```yaml
STORAGE_TYPE=filesystem
STORAGE_PATH=/app/data/messages
```

Requires persistent volume in Kubernetes or Docker volume mount.

## API Endpoints

### Queue Management

**Create Queue**
```bash
POST /api/queues
Content-Type: application/json

{
  "name": "consumer-responses",
  "ownerId": "uuid",
  "maxRetries": 3,
  "retryDelayMs": 5000
}
```

**Get Queue**
```bash
GET /api/queues/{queueId}
```

**List Queues by Owner**
```bash
GET /api/queues?ownerId={environmentId}
```

**Delete Queue**
```bash
DELETE /api/queues/{queueId}
```

**Get Queue Statistics**
```bash
GET /api/queues/{queueId}/stats
```

### Message Operations

**Enqueue Message**
```bash
POST /api/queues/{queueId}/messages
Content-Type: application/json

{
  "environment": "prod-consumer-1",
  "zone": "default",
  "context": "district-a",
  "service": "StudentPersonal",
  "payload": { ... }
}
```

**Deliver Messages (Poll)**
```bash
POST /api/queues/{queueId}/deliver
Content-Type: application/json

{
  "maxMessages": 10,
  "visibilityTimeoutSeconds": 30
}
```

**Acknowledge Message**
```bash
POST /api/messages/{messageId}/acknowledge
```

**Get Dead-Letter Messages**
```bash
GET /api/queues/{queueId}/dead-letter
```

**Reprocess Dead-Letter Message**
```bash
POST /api/messages/{messageId}/reprocess
```

## Monitoring

### Health Checks

- **Liveness**: `GET /actuator/health/liveness`
- **Readiness**: `GET /actuator/health/readiness`
- **Full Health**: `GET /actuator/health`

### Metrics

- **Prometheus**: `GET /actuator/prometheus`
- **Metrics**: `GET /actuator/metrics`
- **Info**: `GET /actuator/info`

### Logging

Logs are output to stdout in JSON format (production) or console format (development).

Log levels can be configured via environment variables:
```yaml
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_ORG_SIFUNITY_BROKER_QUEUESERVICE=DEBUG
```

## Database Migrations

Flyway migrations run automatically on startup. The service will:
1. Check for pending migrations
2. Apply migrations in order
3. Baseline existing databases if needed

Migration files are in `src/main/resources/db/migration/`.

To disable migrations (not recommended):
```yaml
spring.flyway.enabled=false
```

## Troubleshooting

### Service won't start

**Check database connection:**
```bash
# Test PostgreSQL connection
psql -h $DB_HOST -p $DB_PORT -U $DB_USERNAME -d $DB_NAME

# Check logs
docker logs queue-service
kubectl logs -l app=queue-service
```

**Check Redis connection:**
```bash
# Test Redis connection
redis-cli -h $REDIS_HOST -p $REDIS_PORT ping

# Should return: PONG
```

### Messages not being delivered

**Check queue statistics:**
```bash
curl http://localhost:8082/api/queues/{queueId}/stats
```

**Check Redis data:**
```bash
redis-cli -h $REDIS_HOST -p $REDIS_PORT
> KEYS queue:*
> HGETALL message:{messageId}
```

**Check storage:**
```bash
# For filesystem storage
ls -la /app/data/messages

# For S3 storage
aws s3 ls s3://$S3_BUCKET/
```

### High memory usage

Adjust JVM settings:
```bash
JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"
java $JAVA_OPTS -jar app.jar
```

In Kubernetes, adjust resource limits in deployment.yaml.

### Slow performance

**Check connection pools:**
- Database pool size: `spring.datasource.hikari.maximum-pool-size`
- Redis pool size: `spring.data.redis.lettuce.pool.max-active`

**Check indexes:**
```sql
-- Verify indexes exist
SELECT * FROM pg_indexes WHERE tablename = 'queues';
```

**Monitor metrics:**
```bash
curl http://localhost:8082/actuator/metrics/jvm.memory.used
curl http://localhost:8082/actuator/metrics/http.server.requests
```

## Scaling

### Horizontal Scaling

The Queue Service is stateless and can be scaled horizontally:

```bash
# Kubernetes
kubectl scale deployment queue-service --replicas=5

# Docker Compose
docker-compose up --scale queue-service=3
```

### Vertical Scaling

Adjust resource limits based on workload:

```yaml
resources:
  requests:
    memory: "1Gi"
    cpu: "1000m"
  limits:
    memory: "2Gi"
    cpu: "2000m"
```

### Auto-scaling

Use Horizontal Pod Autoscaler in Kubernetes:

```bash
kubectl autoscale deployment queue-service \
  --cpu-percent=70 \
  --min=3 \
  --max=10
```

## Security

### Network Security

- Service runs on port 8082 (configurable)
- Use ClusterIP service in Kubernetes (internal only)
- Expose via Gateway service for external access

### Database Security

- Use strong passwords
- Store credentials in Kubernetes secrets
- Enable SSL/TLS for database connections
- Restrict database access to service IPs

### Storage Security

- Use IAM roles for S3 access (no hardcoded credentials)
- Enable S3 bucket encryption
- Use VPC endpoints for S3 access
- Restrict filesystem permissions (0600)

### Application Security

- Service runs as non-root user in Docker
- No sensitive data in logs
- Input validation on all endpoints
- Rate limiting via Gateway

## Backup and Recovery

### Database Backup

```bash
# Backup PostgreSQL
pg_dump -h $DB_HOST -U $DB_USERNAME $DB_NAME > backup.sql

# Restore
psql -h $DB_HOST -U $DB_USERNAME $DB_NAME < backup.sql
```

### Redis Backup

Redis data is ephemeral (message metadata). No backup needed as:
- Messages are stored in durable storage (S3/filesystem)
- Queue metadata is in PostgreSQL
- Redis can be rebuilt from PostgreSQL + storage

### Storage Backup

**S3:**
- Enable S3 versioning
- Use S3 lifecycle policies
- Cross-region replication

**Filesystem:**
- Backup PVC volumes
- Use volume snapshots

## Performance Tuning

### Database Optimization

```sql
-- Analyze query performance
EXPLAIN ANALYZE SELECT * FROM queues WHERE owner_id = 'uuid';

-- Update statistics
ANALYZE queues;

-- Vacuum
VACUUM ANALYZE queues;
```

### Redis Optimization

```yaml
# Increase max memory
redis.maxmemory: 2gb
redis.maxmemory-policy: allkeys-lru

# Enable persistence (optional)
redis.save: "900 1 300 10"
```

### Application Optimization

```yaml
# Connection pools
spring.datasource.hikari.maximum-pool-size: 20
spring.data.redis.lettuce.pool.max-active: 16

# JPA optimization
spring.jpa.properties.hibernate.jdbc.batch_size: 20
spring.jpa.properties.hibernate.order_inserts: true
```

## Support

For issues or questions:
1. Check logs: `kubectl logs -l app=queue-service`
2. Check health: `curl http://localhost:8082/actuator/health`
3. Review metrics: `curl http://localhost:8082/actuator/metrics`
4. Consult documentation: README.md, IMPLEMENTATION-SUMMARY.md
