# Docker Deployment Guide - Subscription Service

This guide provides instructions for building and deploying the Subscription Service using Docker.

## Prerequisites

- Docker 20.10 or later
- Docker Compose 2.0 or later
- At least 2GB of available RAM
- Ports 8083, 5433, 6380, and 8082 available

## Quick Start

### 1. Build the Docker Image

From the `subscription-service` directory:

```bash
docker build -t subscription-service:latest .
```

This creates a multi-stage Docker image:
- **Stage 1**: Builds the application using Maven and Amazon Corretto 17
- **Stage 2**: Creates a minimal runtime image with only the JAR file

### 2. Start All Services

```bash
docker-compose up -d
```

This starts:
- **PostgreSQL** (port 5433): Database for subscription metadata
- **Redis** (port 6380): Cache for subscription queries
- **Mock Queue Service** (port 8082): Simulated Queue Service for testing
- **Subscription Service** (port 8083): The main application

### 3. Verify Services are Running

Check service health:

```bash
docker-compose ps
```

All services should show "healthy" status.

Check subscription service logs:

```bash
docker-compose logs -f subscription-service
```

### 4. Test the API

#### Health Check

```bash
curl http://localhost:8083/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

#### Create a Subscription

```bash
curl -X POST http://localhost:8083/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "queueId": "123e4567-e89b-12d3-a456-426614174000",
    "serviceName": "StudentPersonal",
    "zoneId": "district-123",
    "contextId": "school-456",
    "type": "EVENT"
  }'
```

Expected response (201 Created):
```json
{
  "id": "generated-uuid",
  "queueId": "123e4567-e89b-12d3-a456-426614174000",
  "serviceName": "StudentPersonal",
  "zoneId": "district-123",
  "contextId": "school-456",
  "type": "EVENT",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

#### Query EVENT Subscriptions

```bash
curl "http://localhost:8083/subscriptions/events?serviceName=StudentPersonal&zoneId=district-123&contextId=school-456"
```

#### Get Subscription by ID

```bash
curl http://localhost:8083/subscriptions/{subscription-id}
```

#### Delete a Subscription

```bash
curl -X DELETE http://localhost:8083/subscriptions/{subscription-id}
```

### 5. Stop Services

```bash
docker-compose down
```

To also remove volumes (database data):

```bash
docker-compose down -v
```

## Docker Configuration Details

### Dockerfile

The Dockerfile uses a multi-stage build approach:

**Build Stage**:
- Base image: `maven:3.9-amazoncorretto-17`
- Downloads dependencies offline for faster builds
- Compiles the application with `mvn clean package -DskipTests`

**Runtime Stage**:
- Base image: `amazoncorretto:17-alpine` (minimal size)
- Runs as non-root user `spring:spring` for security
- Exposes port 8083
- Includes health check using wget
- Health check runs every 30s, starts after 40s

### docker-compose.yml

The compose file defines four services:

#### PostgreSQL
- Image: `postgres:15-alpine`
- Port: 5433 (mapped to avoid conflicts)
- Database: `sif_broker`
- Credentials: postgres/postgres
- Health check: `pg_isready`

#### Redis
- Image: `redis:7-alpine`
- Port: 6380 (mapped to avoid conflicts)
- Health check: `redis-cli ping`

#### Mock Queue Service
- Image: `mockserver/mockserver:latest`
- Port: 8082 (internal 1080)
- Simulates Queue Service for testing
- Responds to HEAD requests on `/api/v1/queues/*`

#### Subscription Service
- Built from local Dockerfile
- Port: 8083
- Environment variables:
  - Database connection to PostgreSQL
  - Redis connection
  - Queue Service URL pointing to mock server
- Depends on all other services being healthy
- Health check: `/actuator/health` endpoint

## Environment Variables

The following environment variables can be configured:

### Database
- `SPRING_DATASOURCE_URL`: JDBC URL (default: `jdbc:postgresql://postgres:5432/sif_broker`)
- `SPRING_DATASOURCE_USERNAME`: Database user (default: `postgres`)
- `SPRING_DATASOURCE_PASSWORD`: Database password (default: `postgres`)

### Redis
- `SPRING_REDIS_HOST`: Redis hostname (default: `redis`)
- `SPRING_REDIS_PORT`: Redis port (default: `6379`)

### Queue Service
- `QUEUE_SERVICE_URL`: Queue Service base URL (default: `http://mock-queue-service:1080`)

### Server
- `SERVER_PORT`: Application port (default: `8083`)

## Production Deployment

For production deployment:

1. **Replace Mock Queue Service**: Point `QUEUE_SERVICE_URL` to the actual Queue Service
2. **Use Secrets**: Store database credentials in Docker secrets or environment-specific secret management
3. **Configure Resources**: Set memory and CPU limits in docker-compose.yml
4. **Enable TLS**: Configure HTTPS for the service
5. **Use External Database**: Point to a managed PostgreSQL instance
6. **Use External Redis**: Point to a managed Redis instance
7. **Configure Logging**: Set up centralized logging (e.g., ELK stack)
8. **Set Up Monitoring**: Configure Prometheus metrics scraping

Example production environment variables:

```yaml
environment:
  SPRING_DATASOURCE_URL: jdbc:postgresql://prod-db.example.com:5432/sif_broker
  SPRING_DATASOURCE_USERNAME: ${DB_USER}
  SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
  SPRING_REDIS_HOST: prod-redis.example.com
  SPRING_REDIS_PORT: 6379
  QUEUE_SERVICE_URL: http://queue-service:8082
  SPRING_PROFILES_ACTIVE: production
```

## Troubleshooting

### Service Won't Start

Check logs:
```bash
docker-compose logs subscription-service
```

Common issues:
- Database not ready: Wait for PostgreSQL health check to pass
- Port conflicts: Ensure ports 8083, 5433, 6380, 8082 are available
- Build failures: Check Maven dependencies and network connectivity

### Database Connection Issues

Verify PostgreSQL is running:
```bash
docker-compose ps postgres
```

Test database connection:
```bash
docker-compose exec postgres psql -U postgres -d sif_broker -c "SELECT 1;"
```

### Redis Connection Issues

Verify Redis is running:
```bash
docker-compose ps redis
```

Test Redis connection:
```bash
docker-compose exec redis redis-cli ping
```

### Health Check Failures

Check health endpoint directly:
```bash
curl http://localhost:8083/actuator/health
```

Check readiness:
```bash
curl http://localhost:8083/actuator/health/readiness
```

Check liveness:
```bash
curl http://localhost:8083/actuator/health/liveness
```

## Performance Tuning

### JVM Options

Add JVM options to the Dockerfile ENTRYPOINT:

```dockerfile
ENTRYPOINT ["java", \
  "-Xms512m", \
  "-Xmx1g", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-jar", "app.jar"]
```

### Database Connection Pool

Configure HikariCP in application.yml:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

### Redis Cache TTL

Adjust cache TTL based on your needs:

```yaml
spring:
  cache:
    redis:
      time-to-live: 300000  # 5 minutes
```

## Security Considerations

1. **Non-root User**: The container runs as user `spring:spring`
2. **Minimal Base Image**: Uses Alpine Linux for smaller attack surface
3. **No Secrets in Image**: All credentials passed via environment variables
4. **Health Checks**: Ensures service is responding before accepting traffic
5. **Network Isolation**: Services communicate on internal Docker network

## Next Steps

- Review [README.md](README.md) for API documentation
- See [Kubernetes deployment guide](k8s/README.md) for production deployment
- Configure monitoring and alerting
- Set up automated backups for PostgreSQL
- Implement log aggregation
